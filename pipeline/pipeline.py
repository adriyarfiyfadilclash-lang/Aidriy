"""Pipeline video: yt-dlp/VoxCPM -> ffmpeg -> Whisper -> subtitle -> sound effect -> Remotion + HyperFrames -> ffmpeg.

Sumber bisa berupa URL (yt-dlp), file video/audio, atau naskah .json (suara dibuat VoxCPM):
  {"title": "...", "lines": [{"voice": "adriy", "text": "...", "sfx": "public/sfx/kertas-remas.wav", "visual": "a.png"}, ...]}
  "sfx" boleh berupa path (relatif ke video/) atau {"file": ..., "at": "start"|"end", "role": "accent"|"underVoice"}.
  "visual" (opsional): gambar atau klip yang tampil selama kalimat itu (relatif ke file naskah). Kalimat tanpa
  "visual" melanjutkan gambar sebelumnya. Untuk sumber biasa, pakai --visuals FOLDER (dibagi rata).
  Gambar dirangkai Open Montage jadi video latar (crossfade, zoom pelan), lalu Remotion dan HyperFrames
  menambahkan subtitle dan audio di atasnya.
  "style" (opsional): nama style di pipeline/styles/ (wayang, koran, bersih, ...). Tanpa "style" atau --style,
  style dipilih otomatis dari kata kunci topik (judul + isi). "sfx" boleh merujuk ke style: "style:lineStart".

Contoh:
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --title "Kaca Mobil Berkabut"
  python3 pipeline/pipeline.py "https://www.youtube.com/watch?v=..." --name klip
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --from pages     # ulang mulai langkah pages
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --only remotion  # satu langkah saja
  python3 pipeline/pipeline.py naskah.json --name iklan --until sfx            # buat suara dulu, belum render
  python3 pipeline/pipeline.py naskah.json --name iklan --renderer both        # Remotion + HyperFrames
  python3 pipeline/pipeline.py naskah.json --name sejarah --style wayang       # pilih style
  python3 pipeline/pipeline.py --list-styles                                   # daftar style
  python3 pipeline/pipeline.py --preview-styles                                # gambar pratinjau style (bukan video)

Semua hasil disimpan di media/<name>/ (tidak di-commit):
  source.*          file asli (dari yt-dlp, salinan file, atau gabungan voiceover dari naskah)
  vo/, lines.json   (naskah) suara per kalimat dan waktu mulai/selesai tiap kalimat
  voice.wav         suara 48 kHz;  audio-16k.wav  16 kHz mono untuk Whisper
  audio.wav         suara + sound effect, dipakai kedua renderer;  sfx-cues.json  letak dan volume tiap bunyi
  montage.json, video.mp4   (kalau ada visual) rangkaian gambar dari Open Montage, dipakai sebagai latar
  meta.json         durasi, judul, ada/tidaknya video
  transcript.json   kata per kata, format Caption Remotion (text, startMs, endMs, ...). Untuk naskah,
                    waktu dari Whisper tapi ejaan dari naskah (transcript-whisper.json = hasil asli Whisper)
  pages.json        kata dikelompokkan jadi halaman subtitle; dipakai Remotion DAN HyperFrames
  remotion.mp4, hyperframes.mp4   hasil render (bawaan hanya Remotion; pilih dengan --renderer)
  final-remotion.mp4, final-hyperframes.mp4   volume disamakan ke -14 LUFS (standar TikTok/Reels/YouTube)
  thumbnail.jpg

Setiap langkah dilewati kalau hasilnya sudah ada dan lebih baru dari masukannya.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REMOTION = ROOT / "video"
HYPERFRAMES = ROOT / "hyperframes"
# Python yang punya transformers + torch untuk Whisper (di container ini: venv VoxCPM).
WHISPER_PY = os.environ.get("PIPELINE_PYTHON", "/root/voxcpm-venv/bin/python" if Path("/root/voxcpm-venv/bin/python").exists() else sys.executable)
WHISPER_MODEL = os.environ.get("WHISPER_MODEL", "openai/whisper-large-v3-turbo")

STEPS = ["fetch", "prepare", "transcribe", "pages", "scenes", "sfx", "montage", "remotion", "hyperframes", "finish"]
MONTAGE_TRANSITION = 0.4  # detik crossfade antar gambar
VOICE_LEAD, LINE_GAP = 0.6, 0.5  # detik: hening sebelum kalimat pertama, dan di antara kalimat
SR = 48000
STYLES = ROOT / "pipeline" / "styles"


def load_styles():
    return {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in sorted(STYLES.glob("*.json"))}


def choose_style(ctx):
    """Style dari --style, lalu "style" di naskah, lalu otomatis dari kata kunci topik."""
    styles = load_styles()
    d = ctx["dir"]
    want = ctx.get("style") or "auto"
    if want == "auto" and (d / "script.json").exists():
        want = json.loads((d / "script.json").read_text(encoding="utf-8")).get("style", "auto")
    if want != "auto":
        if want not in styles:
            raise SystemExit(f"Style '{want}' tidak ada. Pilihan: {', '.join(styles)}")
        return styles[want], "dipilih"
    text = ctx["title"].lower()
    if (d / "transcript.json").exists():
        text += " " + "".join(w["text"] for w in json.loads((d / "transcript.json").read_text())).lower()
    # Kata kunci dicocokkan sebagai kata utuh: "ai" tidak boleh cocok dengan "pakai" atau "sampai".
    def hits(k):
        return [m.start() for m in re.finditer(rf"(?<!\w){re.escape(k)}(?!\w)", text)]
    found = {sid: [h for k in st.get("useFor", []) for h in hits(k)] for sid, st in styles.items()}
    scores = {sid: len(v) for sid, v in found.items()}
    # posisi kata kunci pertama yang cocok: kalau nilai seri, topik yang disebut lebih dulu yang menang
    first = {sid: min(v, default=10**9) for sid, v in found.items()}
    best = min(scores, key=lambda sid: (-scores[sid], first[sid]))
    if scores[best] == 0:
        return styles.get("bersih", next(iter(styles.values()))), "otomatis (tidak ada kata kunci cocok)"
    ties = [sid for sid in scores if scores[sid] == scores[best] and sid != best]
    note = f"; seri dengan {', '.join(ties)}, dipilih yang disebut lebih dulu. Pakai --style untuk memastikan" if ties else ""
    return styles[best], f"otomatis ({scores[best]} kata kunci cocok{note})"


def resolve_sfx_ref(value, style):
    """"style:lineStart" -> path sound effect dari style yang dipakai."""
    if isinstance(value, str) and value.startswith("style:"):
        key = value.split(":", 1)[1]
        path = style.get("sfx", {}).get(key)
        if not path or key == "note":
            raise SystemExit(f"Style '{style['id']}' tidak punya sound effect '{key}'")
        return "public/" + path
    return value


def log(step, msg):
    print(f"[{step}] {msg}", flush=True)


def run(cmd, cwd=None, **kw):
    r = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True, **kw)
    if r.returncode != 0:
        sys.stderr.write(r.stdout[-3000:] + r.stderr[-3000:])
        raise SystemExit(f"Gagal: {' '.join(map(str, cmd))[:200]}")
    return r.stdout


def fresh(out, *inputs):
    """True kalau `out` ada dan lebih baru dari semua masukan."""
    out = Path(out)
    if not out.exists():
        return False
    return all(out.stat().st_mtime >= Path(i).stat().st_mtime for i in inputs if Path(i).exists())


def probe(path):
    info = json.loads(run(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type,width,height",
                           "-of", "json", str(path)]))
    video = [s for s in info.get("streams", []) if s.get("codec_type") == "video" and s.get("width")]
    return float(info["format"]["duration"]), bool(video)


# ---------------------------------------------------------------- langkah
def is_script(path):
    return str(path).lower().endswith(".json") and Path(path).exists()


def decode(path):
    import numpy as np
    raw = run_bytes(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"])
    return np.frombuffer(raw, dtype=np.float32)


def encode(x, path):
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-", str(path)],
                   input=x.astype("float32").tobytes(), check=True)


def run_bytes(cmd):
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode != 0:
        raise SystemExit(f"Gagal: {' '.join(map(str, cmd))[:200]}\n{r.stderr.decode()[-1500:]}")
    return r.stdout


def step_tts(ctx):
    """Naskah -> suara per kalimat (VoxCPM) -> satu file source.wav dengan jeda, plus lines.json."""
    import hashlib
    import numpy as np
    d = ctx["dir"]
    script = json.loads(Path(ctx["input"]).read_text(encoding="utf-8"))
    shutil.copy2(ctx["input"], d / "script.json")
    if script.get("title") and ctx["title"] == ctx["name"]:
        ctx["title"] = script["title"]
    vo_dir = d / "vo"
    vo_dir.mkdir(exist_ok=True)
    lines = script["lines"]
    for ln in lines:  # nama file dari isi kalimat: kalimat yang tidak berubah tidak dibuat ulang
        ln["id"] = hashlib.sha1(f'{ln["voice"]}|{ln["text"]}'.encode()).hexdigest()[:12]
    todo = [{"id": ln["id"], "voice": ln["voice"], "text": ln["text"]} for ln in lines if not (vo_dir / f'{ln["id"]}.wav').exists()]
    if todo:
        log("fetch", f"VoxCPM membuat {len(todo)} dari {len(lines)} kalimat...")
        job = d / ".tts.json"
        job.write_text(json.dumps(todo, ensure_ascii=False))
        t = time.time()
        run([WHISPER_PY, str(REMOTION / "scripts/tts_batch.py"), str(job), str(vo_dir)], env=dict(os.environ, HF_HUB_OFFLINE=os.environ.get("HF_HUB_OFFLINE", "0")))
        job.unlink()
        log("fetch", f"selesai dalam {time.time() - t:.0f} dtk")
    else:
        log("fetch", "semua kalimat sudah ada suaranya")
    parts, t, out_lines = [np.zeros(int(VOICE_LEAD * SR), np.float32)], VOICE_LEAD, []
    for k, ln in enumerate(lines):
        x = decode(vo_dir / f'{ln["id"]}.wav')
        out_lines.append({"index": k, "voice": ln["voice"], "text": ln["text"], "start": round(t, 3),
                          "end": round(t + len(x) / SR, 3), "sfx": ln.get("sfx")})
        parts += [x, np.zeros(int(LINE_GAP * SR), np.float32)]
        t += len(x) / SR + LINE_GAP
    src = d / "source.wav"
    encode(np.concatenate(parts), src)
    (d / "lines.json").write_text(json.dumps(out_lines, ensure_ascii=False, indent=1))
    ctx["source"] = src
    log("fetch", f"-> source.wav ({t:.1f} dtk, {len(lines)} kalimat)")


def step_fetch(ctx):
    src = ctx["input"]
    if is_script(src):
        if not ctx["force"] and fresh(ctx["dir"] / "source.wav", src) and (ctx["dir"] / "lines.json").exists():
            ctx["source"] = ctx["dir"] / "source.wav"
            return log("fetch", "sudah ada: source.wav (dari naskah)")
        return step_tts(ctx)
    existing = sorted(ctx["dir"].glob("source.*"))
    if existing and not ctx["force"]:
        ctx["source"] = existing[0]
        return log("fetch", f"sudah ada: {existing[0].name}")
    if re.match(r"https?://", src):
        if not shutil.which("yt-dlp"):
            raise SystemExit("yt-dlp belum terpasang: pip install -U yt-dlp")
        log("fetch", "mengunduh dengan yt-dlp (hanya untuk video milik sendiri atau yang boleh dipakai)")
        r = subprocess.run(["yt-dlp", "-f", "mp4/bestvideo+bestaudio/best", "--merge-output-format", "mp4",
                            "-o", str(ctx["dir"] / "source.%(ext)s"), src], text=True, capture_output=True)
        if r.returncode != 0:
            tail = r.stderr.strip().splitlines()[-1:] or ["(tanpa pesan)"]
            raise SystemExit(f"yt-dlp gagal: {tail[0]}\nUnduh di komputer sendiri, lalu jalankan pipeline dengan file-nya.")
        ctx["source"] = sorted(ctx["dir"].glob("source.*"))[0]
    else:
        p = Path(src).expanduser().resolve()
        if not p.exists():
            raise SystemExit(f"File tidak ditemukan: {p}")
        ctx["source"] = ctx["dir"] / f"source{p.suffix.lower()}"
        shutil.copy2(p, ctx["source"])
    log("fetch", f"-> {ctx['source'].name}")


def step_prepare(ctx):
    d, src = ctx["dir"], ctx["source"]
    a48, a16, meta = d / "voice.wav", d / "audio-16k.wav", d / "meta.json"
    if not ctx["force"] and fresh(a48, src) and fresh(a16, src) and fresh(meta, src):
        return log("prepare", "sudah ada")
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-vn", "-ac", "2", "-ar", "48000", str(a48)])
    run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-vn", "-ac", "1", "-ar", "16000", str(a16)])
    duration, has_video = probe(src)
    if has_video:  # video sumber dipakai sebagai latar: ubah ke H.264 1080x1920 tanpa suara
        run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-an", "-vf",
             "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920", "-c:v", "libx264", "-crf", "20",
             "-pix_fmt", "yuv420p", str(d / "video.mp4")])
    meta.write_text(json.dumps({"duration": round(duration + 0.5, 3), "title": ctx["title"], "hasVideo": has_video,
                                "source": "video.mp4" if has_video else None}, ensure_ascii=False, indent=1))
    log("prepare", f"durasi {duration:.1f} dtk, video: {'ya' if has_video else 'tidak (audio saja)'}")


WHISPER_SCRIPT = r'''
import json, sys
from transformers import pipeline
asr = pipeline("automatic-speech-recognition", model=sys.argv[3], device="cpu")
kw = {"return_timestamps": "word"}
if sys.argv[4]:
    kw["generate_kwargs"] = {"language": sys.argv[4], "task": "transcribe"}
out = asr(sys.argv[1], **kw)
caps = []
for c in out["chunks"]:
    s, e = c["timestamp"]
    if s is None:
        continue
    e = e if e is not None else s + 0.3
    text = c["text"]
    # Whisper memecah kata seperti "tiba-tiba" jadi "tiba" + "-tiba" tanpa spasi: gabungkan lagi.
    if caps and not text.startswith(" ") and not text[:1].isalnum():
        caps[-1]["text"] += text
        caps[-1]["endMs"] = round(e * 1000)
        continue
    if caps and not text.startswith(" "):
        text = " " + text
    caps.append({"text": text, "startMs": round(s * 1000), "endMs": round(e * 1000),
                 "timestampMs": round((s + e) * 500), "confidence": None})
json.dump(caps, open(sys.argv[2], "w"), ensure_ascii=False, indent=1)
print(len(caps))
'''


def step_transcribe(ctx):
    d = ctx["dir"]
    out = d / "transcript.json"
    if not ctx["force"] and fresh(out, d / "audio-16k.wav"):
        return log("transcribe", "sudah ada")
    script = d / ".whisper.py"
    script.write_text(WHISPER_SCRIPT)
    log("transcribe", f"Whisper ({WHISPER_MODEL}) di CPU, bisa beberapa menit...")
    t = time.time()
    env = dict(os.environ, HF_HUB_OFFLINE=os.environ.get("HF_HUB_OFFLINE", "0"))
    n = run([WHISPER_PY, "-I", str(script), str(d / "audio-16k.wav"), str(out), WHISPER_MODEL, ctx["language"]], env=env).strip().splitlines()[-1]
    script.unlink()
    log("transcribe", f"{n} kata dalam {time.time() - t:.0f} dtk")
    if (d / "lines.json").exists():
        shutil.copy2(out, d / "transcript-whisper.json")
        words, fixed = align_to_script(json.loads(out.read_text()), json.loads((d / "lines.json").read_text()))
        out.write_text(json.dumps(words, ensure_ascii=False, indent=1))
        log("transcribe", f"ejaan disamakan dengan naskah ({fixed} bagian dibetulkan)")


def norm(w):
    return re.sub(r"[^0-9a-z]", "", w.lower())


def align_to_script(whisper, lines):
    """Ambil waktu dari Whisper, tapi teks dari naskah. Bagian yang berbeda (misalnya "Notes by Adri"
    lawan "NotesByAdriy") dipetakan per blok, dan waktunya dibagi rata ke kata-kata naskah."""
    import difflib
    script = []  # (kata, indeks kalimat)
    for ln in lines:
        script += [(w, ln["index"]) for w in ln["text"].split()]
    a, b = [norm(w["text"]) for w in whisper], [norm(w) for w, _ in script]
    out, fixed = [None] * len(script), 0
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
        if tag == "equal":
            for k in range(j2 - j1):
                out[j1 + k] = (whisper[i1 + k]["startMs"], whisper[i1 + k]["endMs"])
        elif j2 > j1:
            fixed += 1
            if i2 > i1:
                s0, e0 = whisper[i1]["startMs"], whisper[i2 - 1]["endMs"]
            else:  # kata naskah tidak terdengar oleh Whisper: sisipkan di antara tetangganya
                s0 = whisper[i1 - 1]["endMs"] if i1 else 0
                e0 = whisper[i1]["startMs"] if i1 < len(whisper) else s0 + 300 * (j2 - j1)
            step = max(e0 - s0, 1) / (j2 - j1)
            for k in range(j2 - j1):
                out[j1 + k] = (round(s0 + k * step), round(s0 + (k + 1) * step))
    # Kata tetap di dalam kalimatnya: kalau jatuh di luar waktu kalimat, jepit ke batasnya.
    bounds = {ln["index"]: (ln["start"] * 1000, ln["end"] * 1000) for ln in lines}
    words = []
    for k, ((w, li), (s0, e0)) in enumerate(zip(script, out)):
        lo, hi = bounds[li]
        s0, e0 = min(max(s0, lo), hi), min(max(e0, lo), hi)
        words.append({"text": (" " if k else "") + w, "startMs": round(s0), "endMs": round(max(e0, s0 + 1)),
                      "timestampMs": round((s0 + e0) / 2), "confidence": None})
    return words, fixed


def step_pages(ctx):
    """Kelompokkan kata jadi halaman subtitle pendek yang enak dibaca di layar HP."""
    d = ctx["dir"]
    out = d / "pages.json"
    if not ctx["force"] and fresh(out, d / "transcript.json"):
        return log("pages", "sudah ada")
    words = json.loads((d / "transcript.json").read_text())
    MAX_WORDS, MAX_CHARS, MAX_MS, GAP_MS = 4, 24, 1800, 400

    groups, cur = [], []
    for i, w in enumerate(words):
        prev = words[i - 1] if i else None
        too_long = cur and len(("".join(x["text"] for x in cur) + w["text"]).strip()) > MAX_CHARS
        new_page = cur and (
            too_long
            or len(cur) >= MAX_WORDS
            or w["startMs"] - prev["endMs"] > GAP_MS                  # jeda bicara
            or re.search(r"[.,?!;:]$", prev["text"].strip())          # akhir kalimat atau koma
            or w["endMs"] - cur[0]["startMs"] > MAX_MS
        )
        if new_page:
            groups.append(cur)
            cur = []
        cur.append(w)
    if cur:
        groups.append(cur)

    # Halaman satu kata yang pendek ("ya?") digabung ke halaman sebelumnya kalau masih muat,
    # supaya subtitle tidak berganti terlalu cepat.
    merged = []
    for g in groups:
        if merged and len(g) == 1 and g[0]["endMs"] - g[0]["startMs"] < 700:
            prev = merged[-1]
            joined = ("".join(x["text"] for x in prev) + g[0]["text"]).strip()
            if len(joined) <= MAX_CHARS and len(prev) < MAX_WORDS + 1 and g[0]["startMs"] - prev[-1]["endMs"] <= GAP_MS:
                prev.append(g[0])
                continue
        merged.append(g)
    groups = merged

    pages = []
    for k, g in enumerate(groups):
        start = g[0]["startMs"]
        end = max(g[-1]["endMs"], start + 300)
        if k + 1 < len(groups):  # tetap tampil sampai halaman berikutnya, kecuali ada jeda panjang
            nxt = groups[k + 1][0]["startMs"]
            end = nxt if nxt - g[-1]["endMs"] <= 600 else min(end + 200, nxt)
        tokens = [{"text": x["text"].lstrip() if j == 0 else x["text"], "fromMs": x["startMs"], "toMs": x["endMs"]} for j, x in enumerate(g)]
        pages.append({"startMs": start, "durationMs": end - start, "tokens": tokens})
    out.write_text(json.dumps(pages, ensure_ascii=False, indent=1))
    log("pages", f"{len(words)} kata -> {len(pages)} halaman subtitle")


def openmontage_bin():
    return os.environ.get("OPENMONTAGE_BIN") or shutil.which("open-montage") or "/root/openmontage-venv/bin/open-montage"


VISUAL_EXT = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif", ".tif", ".mp4", ".mov", ".webm", ".mkv", ".m4v"}


def visual_plan(ctx, duration):
    """Daftar (file, mulai, selesai) untuk latar. Dari "visual" per kalimat di naskah, atau --visuals FOLDER."""
    d = ctx["dir"]
    plan = []
    if (d / "lines.json").exists() and (d / "script.json").exists():
        script = json.loads((d / "script.json").read_text(encoding="utf-8"))
        base = Path(ctx["input"]).resolve().parent if is_script(ctx["input"]) else d
        lines = json.loads((d / "lines.json").read_text())
        for ln, src in zip(lines, script["lines"]):
            if src.get("visual"):
                p = Path(src["visual"])
                p = p if p.is_absolute() else base / p
                if not p.exists():
                    raise SystemExit(f"Visual tidak ditemukan: {p}")
                plan.append([p, ln["start"] if plan else 0.0])
    elif ctx.get("visuals"):
        files = sorted(f for f in Path(ctx["visuals"]).iterdir() if f.suffix.lower() in VISUAL_EXT)
        if not files:
            raise SystemExit(f"Tidak ada gambar/klip di {ctx['visuals']}")
        plan = [[f, i * duration / len(files)] for i, f in enumerate(files)]
    return [(p, s, plan[i + 1][1] if i + 1 < len(plan) else duration) for i, (p, s) in enumerate(plan)]


def step_montage(ctx):
    """Open Montage merangkai gambar/klip jadi video latar 1080x1920 yang pas dengan waktu tiap kalimat."""
    d = ctx["dir"]
    meta_f = d / "meta.json"
    meta = json.loads(meta_f.read_text())
    plan = visual_plan(ctx, meta["duration"])
    out = d / "video.mp4"
    if not plan:
        return log("montage", "tidak ada visual: dilewati (latar bawaan dipakai)")
    mont = choose_style(ctx)[0].get("montage", {})
    transition = mont.get("transition", MONTAGE_TRANSITION)
    # Crossfade memakan waktu dari klip berikutnya, jadi tiap klip (kecuali terakhir) dipanjangkan
    # sebesar durasi crossfade: gambar baru mulai muncul tepat saat kalimatnya dimulai.
    clips = []
    for i, (p, s0, e0) in enumerate(plan):
        dur = (e0 - s0) + (transition if i + 1 < len(plan) else 0)
        clips.append({"source": str(p), "duration": round(max(dur, 0.1), 3), "label": p.stem})
    plan_json = json.dumps({"title": ctx["title"], "clips": clips, "transition": transition,
                            "kenBurns": mont.get("kenBurns", True)}, ensure_ascii=False, indent=1)
    old = (d / "montage.json").read_text() if (d / "montage.json").exists() else None
    # Render ulang hanya kalau rangkaiannya berubah atau salah satu gambar diganti
    # (meta.json tidak ikut dicek, karena langkah ini sendiri yang menulisnya).
    if ctx["force"] or old != plan_json or not fresh(out, *[p for p, _, _ in plan]):
        (d / "montage.json").write_text(plan_json)
        log("montage", f"Open Montage merangkai {len(clips)} visual...")
        t = time.time()
        run([openmontage_bin(), "render", str(d / "montage.json"), "-o", str(d / "montage-raw.mp4"), "--fps", "30",
             "--size", "1080x1920", "--fit", "cover", "--transition", str(transition)] + (["--ken-burns"] if mont.get("kenBurns", True) else []))
        # Samakan format dengan latar video biasa: H.264, tanpa suara, durasi pas dengan audio.
        run(["ffmpeg", "-y", "-v", "error", "-i", str(d / "montage-raw.mp4"), "-an", "-t", str(meta["duration"]),
             "-vf", "tpad=stop_mode=clone:stop_duration=1", "-c:v", "libx264", "-crf", "20", "-pix_fmt", "yuv420p", str(out)])
        (d / "montage-raw.mp4").unlink()
        log("montage", f"-> video.mp4 ({time.time() - t:.0f} dtk)")
    else:
        log("montage", "sudah ada")
    if not (meta.get("hasVideo") and meta.get("source") == "video.mp4"):  # tulis hanya kalau berubah,
        meta.update({"hasVideo": True, "source": "video.mp4"})          # supaya render tidak diulang
        meta_f.write_text(json.dumps(meta, ensure_ascii=False, indent=1))


TARGET_DB = {"accent": -24, "underVoice": -32}       # sama dengan video/src/promo/Sfx.tsx
PEAK_LIMIT_DB = {"accent": -12, "underVoice": -26}


def step_sfx(ctx):
    """Campur sound effect ke suara: puncak bunyi tepat di awal/akhir kalimat, volume menurut peran."""
    import numpy as np
    sys.path.insert(0, str(REMOTION / "scripts"))
    from sfx_analyze import analyze
    d = ctx["dir"]
    out, lines_f = d / "audio.wav", d / "lines.json"
    cue_files = []
    lines = json.loads(lines_f.read_text()) if lines_f.exists() else []
    style = choose_style(ctx)[0] if any(ln.get("sfx") for ln in lines) else None
    for ln in lines:
        if ln.get("sfx"):
            c = dict(ln["sfx"]) if isinstance(ln["sfx"], dict) else {"file": ln["sfx"]}
            c["file"] = resolve_sfx_ref(c["file"], style)
            p = Path(c["file"])
            c["path"] = p if p.is_absolute() else REMOTION / p
            if not c["path"].exists():
                raise SystemExit(f"Sound effect tidak ditemukan: {c['path']}")
            cue_files.append((ln, c))
    if not ctx["force"] and fresh(out, d / "voice.wav", lines_f, *[c["path"] for _, c in cue_files]):
        return log("sfx", "sudah ada")
    if not cue_files:
        shutil.copy2(d / "voice.wav", out)
        return log("sfx", "tidak ada sound effect di naskah: audio.wav = suara saja")
    voice = decode(d / "voice.wav")
    mix = np.zeros(len(voice) + 3 * SR, np.float32)
    mix[: len(voice)] += voice
    sfx_only = np.zeros_like(mix)  # untuk diperiksa dengan video/scripts/sfx_check.py
    cues = []
    for ln, c in cue_files:
        m = analyze(str(c["path"]))
        role = c.get("role", "accent")
        gain = min(1, 10 ** ((TARGET_DB[role] - m["loudness"]) / 20), 10 ** ((PEAK_LIMIT_DB[role] - m["peakDb"]) / 20))
        x = decode(c["path"])[int(m["onset"] * SR):][: int(min(m["duration"], c.get("max", 99)) * SR)]
        fade = min(len(x), int(0.15 * SR))
        x = x * gain
        x[-fade:] *= np.linspace(1, 0, fade)
        if c.get("at", "start") == "start":  # di jeda sebelum kalimat: puncak 0,1 detik sebelum orang mulai bicara
            t = ln["start"] - 0.1 - m["peak"]
        else:                                 # setelah kalimat selesai
            t = ln["end"] + 0.05
        i = int(t * SR)
        if i < 0:  # puncak terlalu jauh di dalam file untuk muat sebelum kalimat: potong bagian awalnya
            x, i = x[-i:], 0
        mix[i:i + len(x)] += x[: len(mix) - i]
        sfx_only[i:i + len(x)] += x[: len(mix) - i]
        cues.append({"line": ln["index"], "file": str(c["file"]), "role": role, "start": round(i / SR, 3),
                     "peak": round(t + m["peak"], 3), "gainDb": round(20 * np.log10(gain), 1)})
    peak = float(np.abs(mix).max())
    if peak > 0.98:
        mix *= 0.98 / peak
    end = max(len(voice), max(int((cu["start"] + 3) * SR) for cu in cues))
    encode(mix[:end], out)
    encode(sfx_only[:end] * (0.98 / peak if peak > 0.98 else 1), d / "sfx-only.wav")
    (d / "sfx-cues.json").write_text(json.dumps(cues, ensure_ascii=False, indent=1))
    log("sfx", f"{len(cues)} sound effect dicampur -> audio.wav (lihat sfx-cues.json)")


# ---------------------------------------------------------------- perencana adegan (style dengan "engine": "adriy")
SCENE_KICKER = {"karakter": "Adri.project", "objek": "Lihat lebih dekat", "infografis": "Poin penting", "diagram": "Langkahnya",
                "perbandingan": "Bandingkan", "tipografi": "", "sinematik": "Ingat", "angka": "Faktanya"}
DATA_TYPES = {"angka", "diagram", "perbandingan", "infografis"}


ICON_WORDS = [("mobil", r"mobil|kendaraan|berkendara|kaca depan"), ("drop", r"air|uap|embun|kabut|lembap|lembab|hujan|basah"),
              ("suhu", r"suhu|dingin|panas|hangat|derajat"), ("angin", r"udara|angin|ac|sirkulasi|napas"),
              ("waktu", r"detik|menit|jam|waktu|cepat|lama"), ("awas", r"bahaya|aman|hati-hati|ingat|jangan|berhenti"),
              ("grafik", r"naik|turun|persen|data|angka|jumlah"), ("ide", r"cara|tips|solusi|efektif|ide|rahasia")]


def guess_icon(text):
    for icon, pat in ICON_WORDS:
        if re.search(rf"\b({pat})\b", text, re.I):
            return icon
    return "bintang"


def _clauses(text):
    parts = [p.strip(" .!?") for p in re.split(r"[,;:]| lalu | kemudian | dan ", text) if p.strip(" .!?")]
    return [p for p in parts if len(p.split()) >= 2][:4]


def _short_title(text, n=7):
    first = re.split(r"[.!?]", text)[0].strip() or text.strip()
    words = first.split()
    t = " ".join(words[:n]) + ("..." if len(words) > n else "")
    return t[:1].upper() + t[1:]


def plan_scenes(lines, duration, title, style):
    """Ubah kalimat-kalimat jadi rencana adegan yang mengikuti standar Adriy.project."""
    rules = style.get("scenes", {}).get("rules", {})
    overlap = style.get("motion", {}).get("overlap", 0.35)
    tones, cams = ["dark", "blue", "light"], ["push", "pan-right", "rise", "pan-left", "pull"]
    plan = []
    for i, ln in enumerate(lines):
        text, given = ln["text"], dict(ln.get("scene") or {})
        words = text.split()
        num = re.search(r"(\d+(?:[.,]\d+)?)\s*(%|persen|derajat|°|kali|detik|menit|jam|tahun|juta|ribu)?", text)
        has_visual = bool(ln.get("visual"))
        outro = style.get("outro", {})
        is_outro = bool(re.search(r"adri\.?\s*project|inilah adri|explore more|know more", text, re.I))
        if is_outro and plan and plan[-1].get("outro"):
            plan[-1]["end"] = round(lines[i + 1]["start"] + overlap if i + 1 < len(lines) else duration, 3)  # gabung penutup
            continue
        if given.get("type"):
            kind = given["type"]
        elif is_outro:
            kind = "karakter"
        elif i == 0:
            kind = "karakter"
        elif num and not re.search(r"\b(langkah|pertama|kedua)\b", text, re.I):
            kind = "angka"
        elif (re.search(r"\b(langkah|pertama|kedua|ketiga)\b", text, re.I) and len(_clauses(text)) >= 2) or (
                re.search(r"\b(lalu|kemudian|setelah itu)\b", text, re.I) and len(_clauses(text)) >= 3):
            kind = "diagram"
        elif has_visual:
            kind = "sinematik" if plan and plan[-1]["type"] == "objek" else "objek"
        elif len(words) <= 7:
            kind = "tipografi"
        elif text.strip().endswith("?"):
            kind = "karakter"
        else:
            kind = None
        # Variasi: jenis yang muncul di 2 adegan terakhir, atau sudah > 30% dari rencana, tidak dipilih lagi.
        recent = [p["type"] for p in plan[-2:]]
        cap = max(2, round(0.3 * len(lines)))
        used = lambda k: sum(1 for p in plan if p["type"] == k)  # noqa: E731
        ok = lambda k: k not in recent and used(k) < cap  # noqa: E731
        fits = {
            "infografis": len(_clauses(text)) >= 2,
            "karakter": True,
            "tipografi": len(words) <= 12,
            "objek": True,
            "sinematik": bool(re.search(r"\b(ingat|penting|aman|jalan|perjalanan|akhirnya|karena)\b", text, re.I)),
        }
        if given.get("type") or is_outro:
            pass
        elif kind is None or not ok(kind):
            pool = ["infografis", "karakter", "objek", "tipografi", "sinematik"]
            pool = pool[i % len(pool):] + pool[:i % len(pool)]  # mulai dari tempat berbeda tiap adegan
            kind = next((k for k in pool if fits[k] and ok(k)), next((k for k in pool if fits[k] and k not in recent[-1:]), "karakter"))
        # aturan: ADRI muncul paling tidak tiap N adegan
        every = rules.get("characterEvery", 3)
        with_adri = {"karakter", "objek", "infografis", "angka", "diagram"}
        if not given.get("type") and len(plan) >= every - 1 and all(p["type"] not in with_adri for p in plan[-(every - 1):]) and kind not in with_adri:
            kind = "karakter"
        sc = {"type": kind, "line": ln["index"], "text": text}
        # isi adegan
        if kind == "angka" and num:
            v = float(num.group(1).replace(",", "."))
            unit = {"persen": "%", "derajat": "°"}.get((num.group(2) or "").lower(), num.group(2) or "")
            sc.update({"value": v, "unit": unit, "max": 100 if unit == "%" else max(v * 1.25, 1), "label": _short_title(text, 9), "pose": "kaget"})
        elif kind == "diagram":
            sc["steps"] = [c[:28] for c in _clauses(text)]
        elif kind == "infografis":
            sc["points"] = [f"{guess_icon(c)}:{c[:40]}" for c in _clauses(text)]
        elif kind in ("objek", "sinematik"):
            if has_visual:
                sc["visual"] = ln["visual"]
            cl = _clauses(text) or [text]
            sc["points"] = [f"{guess_icon(text)}:"] + [f"{guess_icon(c)}:{c[:24]}" for c in cl[:3]] if kind == "objek" else []
            if kind == "objek":
                sc["points"] = sc["points"][1:] or [f"{guess_icon(text)}:"]
                sc["icon"] = guess_icon(text)
        elif kind == "tipografi":
            sc["text"] = text.strip()
            sc.setdefault("highlight", max(words, key=len).strip(".,!?"))
        if kind == "karakter":
            sc["points"] = [f"{guess_icon(text)}:", f"{guess_icon(' '.join(words[len(words)//2:]))}:"]
            sc["pose"] = "sapa" if i == 0 else ("pikir" if text.strip().endswith("?") else ("senang" if text.strip().endswith("!") else "jelaskan"))
            sc["side"] = "right" if sum(1 for p in plan if p["type"] == "karakter") % 2 == 0 else "left"
        sc["title"] = title if i == 0 and kind == "karakter" else _short_title(text)
        sc["kicker"] = SCENE_KICKER[kind] if i else (style.get("outro", {}).get("tagline", "Adri.project").replace("Inilah ", ""))
        if is_outro and not given.get("type"):
            # penutup brand: ADRI senang, tagline + slogan
            sc.update({"outro": True, "pose": "senang", "kicker": outro.get("tagline", "Inilah Adri.project"),
                       "title": outro.get("slogan", "Explore more. Know more."), "highlight": "Know more", "points": ["bintang:", "ide:"]})
        hl = ln.get("highlight") or (num.group(0).strip() if num else None)
        if hl:
            sc["highlight"] = hl
        # transisi menurut konteks
        sc["transition"] = ("slide" if i == 0 or ln.get("topic") else "wipe" if kind in DATA_TYPES else
                            "zoom" if (hl or text.strip().endswith("!")) else "fade")
        sc["tone"] = tones[i % 3]
        sc["camera"] = cams[i % len(cams)]
        sc.update({k: v for k, v in given.items() if k != "type"})
        # waktu: dari awal kalimat (dikurangi tumpang tindih transisi) sampai awal kalimat berikutnya
        start = 0.0 if i == 0 else max(0.0, ln["start"] - overlap)
        end = lines[i + 1]["start"] + overlap if i + 1 < len(lines) else duration
        sc.update({"start": round(start, 3), "end": round(end, 3)})
        plan.append(sc)
    return plan


def check_standard(plan, style):
    """Periksa rencana adegan terhadap aturan standar. Kembalikan daftar pelanggaran."""
    rules = style.get("scenes", {}).get("rules", {})
    issues = []
    for a, b in zip(plan, plan[1:]):
        if rules.get("noRepeatType", True) and a["type"] == b["type"]:
            issues.append(f"adegan {plan.index(b) + 1}: jenis '{b['type']}' sama dengan adegan sebelumnya")
        if a["tone"] == b["tone"] and a["camera"] == b["camera"]:
            issues.append(f"adegan {plan.index(b) + 1}: komposisi (latar + kamera) sama dengan sebelumnya")
    every = rules.get("characterEvery", 3)
    with_adri = {"karakter", "objek", "infografis", "angka", "diagram"}
    run_ = 0
    for i, sc in enumerate(plan):
        run_ = 0 if sc["type"] in with_adri else run_ + 1
        if run_ >= every:
            issues.append(f"adegan {i + 1}: ADRI tidak muncul selama {run_} adegan")
    for i, sc in enumerate(plan):
        d = sc["end"] - sc["start"]
        if d > rules.get("maxScene", 6.5) + 1:
            issues.append(f"adegan {i + 1}: {d:.1f} dtk terlalu lama untuk satu komposisi; pecah kalimatnya atau beri adegan tambahan")
        if d < rules.get("minScene", 1.6):
            issues.append(f"adegan {i + 1}: {d:.1f} dtk terlalu singkat untuk dibaca")
    if len({sc['type'] for sc in plan}) < min(4, len(plan)):
        issues.append(f"variasi kurang: hanya {len({sc['type'] for sc in plan})} jenis adegan dari {len(plan)} adegan")
    return issues


def lines_from_pages(d, duration, max_len=6.0, min_len=1.6):
    """Untuk rekaman tanpa naskah: pecah transkrip jadi kalimat (titik/tanya/seru). Kalimat yang lebih
    panjang dari max_len dipecah di koma yang paling dekat ke tengahnya; potongan yang terlalu pendek
    digabung ke sebelumnya."""
    words = json.loads((d / "transcript.json").read_text())
    sents, cur = [], []
    for w in words:
        cur.append(w)
        if re.search(r"[.!?]$", w["text"].strip()):
            sents.append(cur)
            cur = []
    if cur:
        sents.append(cur)

    def split(ws):
        if (ws[-1]["endMs"] - ws[0]["startMs"]) / 1000 <= max_len or len(ws) < 6:
            return [ws]
        mid = (ws[0]["startMs"] + ws[-1]["endMs"]) / 2
        commas = [i for i, w in enumerate(ws[:-1]) if w["text"].strip().endswith(",") and 2 <= i < len(ws) - 3]
        cut = min(commas, key=lambda i: abs(ws[i]["endMs"] - mid)) + 1 if commas else min(range(3, len(ws) - 2), key=lambda i: abs(ws[i]["startMs"] - mid))
        return split(ws[:cut]) + split(ws[cut:])

    chunks = [c for s_ in sents for c in split(s_)]
    merged = []
    for c in chunks:
        if merged and (c[-1]["endMs"] - c[0]["startMs"]) / 1000 < min_len:
            merged[-1] = merged[-1] + c
        else:
            merged.append(c)
    return [{"index": i, "text": "".join(w["text"] for w in c).strip(), "start": c[0]["startMs"] / 1000, "end": c[-1]["endMs"] / 1000}
            for i, c in enumerate(merged)]


def step_scenes(ctx):
    d = ctx["dir"]
    style, _ = choose_style(ctx)
    if style.get("engine") != "adriy":
        return log("scenes", f"style '{style['id']}' tidak memakai adegan: dilewati")
    out = d / "scenes.json"
    meta = json.loads((d / "meta.json").read_text())
    if (d / "lines.json").exists():
        lines = json.loads((d / "lines.json").read_text())
        script = json.loads((d / "script.json").read_text(encoding="utf-8"))
        for ln, src in zip(lines, script["lines"]):
            ln.update({k: src[k] for k in ("scene", "highlight", "topic", "visual") if k in src})
            if ln.get("visual") and not str(ln["visual"]).startswith("media/"):
                ln.pop("visual")  # gambar naskah dipakai lewat montase; gambar untuk adegan harus di video/public/
    else:
        lines = lines_from_pages(d, meta["duration"])
    plan = plan_scenes(lines, meta["duration"], ctx["title"], style)
    issues = check_standard(plan, style)
    plan_json = json.dumps(plan, ensure_ascii=False, indent=1)
    if not out.exists() or out.read_text(encoding="utf-8") != plan_json:
        out.write_text(plan_json, encoding="utf-8")
    counts = {}
    for sc in plan:
        counts[sc["type"]] = counts.get(sc["type"], 0) + 1
    log("scenes", f"{len(plan)} adegan: " + ", ".join(f"{k} {v}" for k, v in counts.items()))
    for sc in plan:
        log("scenes", f"  {sc['start']:6.2f}-{sc['end']:6.2f}  {sc['type']:12s} {sc['transition']:5s} {sc['tone']:5s} {sc['camera']:9s} {sc.get('title', '')[:44]}")
    if issues:
        for it in issues:
            log("scenes", f"  ! {it}")
    else:
        log("scenes", "  standar Adriy.project terpenuhi")


def step_remotion(ctx):
    d, name = ctx["dir"], ctx["name"]
    out = d / "remotion.mp4"
    inputs = [d / "pages.json", d / "meta.json", d / "audio.wav", d / "video.mp4"]
    style, why = choose_style(ctx)
    style_f = d / "style.json"
    style_json = json.dumps(style, ensure_ascii=False, indent=1)
    if not style_f.exists() or style_f.read_text(encoding="utf-8") != style_json:
        style_f.write_text(style_json, encoding="utf-8")  # ditulis hanya kalau berubah, supaya render tidak diulang
    log("remotion", f"style: {style['id']} ({why})")
    if not ctx["force"] and fresh(out, *inputs, style_f, d / "scenes.json"):
        return log("remotion", "sudah ada")
    pub = REMOTION / "public" / "media" / name
    pub.mkdir(parents=True, exist_ok=True)
    for f in ["meta.json", "pages.json", "audio.wav", "video.mp4", "style.json", "scenes.json"]:
        if (d / f).exists():
            shutil.copy2(d / f, pub / f)
    log("remotion", "render komposisi Subtitle...")
    t = time.time()
    comp = "AdriyVideo" if style.get("engine") == "adriy" else "Subtitle"
    run(["npx", "remotion", "render", comp, str(out), f"--props={json.dumps({'media': name})}"], cwd=REMOTION)
    log("remotion", f"-> {out.name} ({time.time() - t:.0f} dtk)")


HF_STYLE = """
@font-face { font-family: "Plus Jakarta Sans"; src: url("assets/PlusJakartaSans-latin.woff2") format("woff2"); font-weight: 200 800; }
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { width: 1080px; height: 1920px; overflow: hidden; background: #0B2A6B; }
#root { position: relative; width: 1080px; height: 1920px; overflow: hidden; font-family: "Plus Jakarta Sans", sans-serif;
  background: radial-gradient(circle at 50% 30%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%); }
#bg { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
#brand { position: absolute; top: 260px; left: 0; right: 0; display: flex; flex-direction: column; align-items: center; gap: 40px; }
#brand .logo { width: 200px; height: 200px; border-radius: 56px; background: #fff; display: flex; align-items: center; justify-content: center; }
#brand .title { color: #DCE8FD; font-size: 52px; font-weight: 700; text-align: center; padding: 0 90px; }
.page { position: absolute; left: 80px; right: 80px; bottom: 520px; text-align: center; font-size: 84px; font-weight: 800;
  line-height: 1.15; letter-spacing: -1px; color: #fff; text-shadow: 0 6px 24px rgba(0,0,0,.45); }
.w { white-space: pre-wrap; }
"""
FEATHER = "M20.2,3.8C16.1,2.6 10.4,4.2 7.3,8.6C5.4,11.3 4.8,14.4 5.2,17.3L3.3,19.3C2.9,19.7 2.9,20.3 3.3,20.7C3.7,21.1 4.3,21.1 4.7,20.7L6.7,18.7C9.6,19.1 12.7,18.5 15.4,16.6C19.8,13.5 21.4,7.9 20.2,3.8ZM14.3,15C12.3,16.4 9.9,17 7.7,16.8L12.7,11.7L11.3,10.3L6.9,14.8C6.9,12.9 7.4,11.1 8.6,9.4C11,6 14.9,4.7 18.3,5.2C18.8,8.7 17.6,12.7 14.3,15Z"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def step_hyperframes(ctx):
    d = ctx["dir"]
    out = d / "hyperframes.mp4"
    inputs = [d / "pages.json", d / "meta.json", d / "audio.wav", d / "video.mp4"]
    if not ctx["force"] and fresh(out, *inputs):
        return log("hyperframes", "sudah ada")
    st = choose_style(ctx)[0]
    if st["id"] != "bersih":
        log("hyperframes", f"catatan: style '{st['id']}' baru tersedia di Remotion; versi HyperFrames memakai tampilan bersih")
    proj = d / "hyperframes"
    (proj / "assets").mkdir(parents=True, exist_ok=True)
    shutil.copy2(REMOTION / "public/fonts/PlusJakartaSans-latin.woff2", proj / "assets")
    shutil.copy2(d / "audio.wav", proj / "assets/audio.wav")
    meta = json.loads((d / "meta.json").read_text())
    pages = json.loads((d / "pages.json").read_text())
    if meta["hasVideo"]:
        shutil.copy2(d / "video.mp4", proj / "assets/video.mp4")
        bg = f'<video id="bg" class="clip" src="assets/video.mp4" muted data-start="0" data-duration="{meta["duration"]}" data-track-index="0"></video>'
    else:
        bg = (f'<div id="brand" class="clip" data-start="0" data-duration="{meta["duration"]}" data-track-index="0">'
              f'<div class="logo"><svg width="130" height="130" viewBox="0 0 24 24"><path d="{FEATHER}" fill="#2563EB"/></svg></div>'
              f'<div class="title">{esc(meta["title"])}</div></div>')
    html_pages, js = [], []
    for i, p in enumerate(pages):
        s, dur = p["startMs"] / 1000, max(p["durationMs"], 34) / 1000
        spans = "".join(f'<span id="p{i}w{j}" class="w">{esc(t["text"])}</span>' for j, t in enumerate(p["tokens"]))
        html_pages.append(f'<div id="p{i}" class="clip page" data-start="{s:.3f}" data-duration="{dur:.3f}" data-track-index="1">{spans}</div>')
        js.append(f'tl.fromTo("#p{i}", {{scale: 0.85}}, {{scale: 1, duration: 0.2, ease: "back.out(2.5)"}}, {s:.3f});')
        for j, t in enumerate(p["tokens"]):
            js.append(f'tl.set("#p{i}w{j}", {{color: "#FFD25E"}}, {t["fromMs"] / 1000:.3f}); tl.set("#p{i}w{j}", {{color: "#FFFFFF"}}, {t["toMs"] / 1000:.3f});')
    html = f"""<!doctype html>
<!-- Dibuat oleh pipeline/pipeline.py dari pages.json. Jangan edit langsung. -->
<html lang="id" data-resolution="portrait">
<head><meta charset="UTF-8" /><meta name="viewport" content="width=1080, height=1920" />
<script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
<style>{HF_STYLE}</style></head>
<body>
<div id="root" data-composition-id="main" data-start="0" data-duration="{meta['duration']}" data-width="1080" data-height="1920">
{bg}
<audio id="audio" class="clip" src="assets/audio.wav" data-start="0" data-duration="{meta['duration']}" data-volume="1" data-track-index="2"></audio>
{chr(10).join(html_pages)}
</div>
<script>
const tl = gsap.timeline({{ paused: true }});
{chr(10).join(js)}
window.__timelines = window.__timelines || {{}};
window.__timelines["main"] = tl;
tl.seek(0);
</script>
</body></html>
"""
    (proj / "index.html").write_text(html, encoding="utf-8")
    (proj / "hyperframes.json").write_text(json.dumps({"paths": {"assets": "assets"}}, indent=1))
    (proj / "meta.json").write_text(json.dumps({"id": ctx["name"], "name": ctx["title"]}, ensure_ascii=False))
    cli = HYPERFRAMES / "node_modules/.bin/hyperframes"
    log("hyperframes", "render...")
    t = time.time()
    run([str(cli), "render", "-o", str(out), "--quiet"], cwd=proj)
    log("hyperframes", f"-> {out.name} ({time.time() - t:.0f} dtk)")


def step_finish(ctx):
    d = ctx["dir"]
    for kind in ctx["renderers"]:
        src, out = d / f"{kind}.mp4", d / f"final-{kind}.mp4"
        if not src.exists():
            continue
        if not ctx["force"] and fresh(out, src):
            log("finish", f"final-{kind}.mp4 sudah ada")
            continue
        # Dua tahap: ukur dulu, lalu samakan ke -14 LUFS dengan puncak maksimal -1 dBTP.
        m = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(src), "-af", "loudnorm=I=-14:TP=-1:LRA=11:print_format=json",
                            "-f", "null", "-"], capture_output=True, text=True).stderr
        j = json.loads(m[m.rindex("{"):m.rindex("}") + 1])
        af = (f"loudnorm=I=-14:TP=-1:LRA=11:measured_I={j['input_i']}:measured_TP={j['input_tp']}:"
              f"measured_LRA={j['input_lra']}:measured_thresh={j['input_thresh']}:offset={j['target_offset']}:linear=true")
        run(["ffmpeg", "-y", "-v", "error", "-i", str(src), "-c:v", "copy", "-af", af, "-ar", "48000", "-c:a", "aac", "-b:a", "192k", str(out)])
        log("finish", f"final-{kind}.mp4 (kekerasan awal {j['input_i']} LUFS -> -14 LUFS)")
    pages = json.loads((d / "pages.json").read_text())
    at = (pages[0]["startMs"] + 300) / 1000 if pages else 1
    src = next((d / f"final-{k}.mp4" for k in ctx["renderers"] if (d / f"final-{k}.mp4").exists()), d / "final-remotion.mp4")
    if src.exists():
        run(["ffmpeg", "-y", "-v", "error", "-ss", f"{at:.2f}", "-i", str(src), "-frames:v", "1", "-q:v", "3", str(d / "thumbnail.jpg")])
        log("finish", "thumbnail.jpg")


def list_styles():
    for sid, st in load_styles().items():
        print(f"{sid:10s} {st['label']}")
        print(f"           {st['description']}")
        print(f"           kata kunci: {', '.join(st.get('useFor', [])[:10])}{' ...' if len(st.get('useFor', [])) > 10 else ''}")


def preview_styles(only=None):
    """Gambar diam (PNG) tiap style dari komposisi StylePreview. Tidak membuat video."""
    pub = REMOTION / "public" / "styles"
    pub.mkdir(parents=True, exist_ok=True)
    out_dir = ROOT / "media" / "style-preview"
    out_dir.mkdir(parents=True, exist_ok=True)
    for sid, st in load_styles().items():
        if only and sid != only:
            continue
        (pub / f"{sid}.json").write_text(json.dumps(st, ensure_ascii=False), encoding="utf-8")
        prev = st.get("preview", {})
        props = {"styleId": sid, "style": None, "title": prev.get("title", st["label"]), "caption": prev.get("caption", "Contoh subtitle"),
                 "active": prev.get("active", 0), "visual": "style-preview/tokoh.png"}
        out = out_dir / f"{sid}.png"
        run(["npx", "remotion", "still", "StylePreview", str(out), f"--props={json.dumps(props, ensure_ascii=False)}"], cwd=REMOTION)
        print(f"{sid:10s} -> {out.relative_to(ROOT)}")


def preview_scenes(name, only=None):
    """Gambar diam dari tengah tiap adegan di media/<name>/scenes.json (bukan video)."""
    d = ROOT / "media" / name
    if not (d / "scenes.json").exists():
        raise SystemExit("Belum ada scenes.json: jalankan dulu dengan --style adriy --until scenes")
    pub = REMOTION / "public" / "media" / name
    pub.mkdir(parents=True, exist_ok=True)
    style = load_styles()["adriy"]
    (d / "style.json").write_text(json.dumps(style, ensure_ascii=False, indent=1), encoding="utf-8")
    for f in ["meta.json", "pages.json", "audio.wav", "style.json", "scenes.json"]:
        if (d / f).exists():
            shutil.copy2(d / f, pub / f)
    scenes = json.loads((d / "scenes.json").read_text())
    out_dir = d / "scene-preview"
    out_dir.mkdir(exist_ok=True)
    for i, sc in enumerate(scenes):
        if only and (i + 1) not in only:
            continue
        frame = int(((sc["start"] + sc["end"]) / 2) * 30)
        out = out_dir / f"{i + 1:02d}-{sc['type']}.png"
        run(["npx", "remotion", "still", "AdriyVideo", str(out), f"--frame={frame}", f"--props={json.dumps({'media': name})}"], cwd=REMOTION)
        print(f"{i + 1:2d}. {sc['type']:12s} detik {frame / 30:6.2f} -> {out.relative_to(ROOT)}")


def check():
    """Periksa semua alat dan sambungan antar-alat tanpa membuat video atau suara."""
    results = []

    def item(group, name, ok, detail, fix=""):
        results.append((group, name, ok, detail, fix))

    def cmd(c, cwd=None, timeout=120, env=None, stdout_only=False):
        try:
            r = subprocess.run(c, cwd=cwd, capture_output=True, text=True, timeout=timeout, env=env)
            return r.returncode, (r.stdout if stdout_only else r.stdout + r.stderr).strip()
        except (FileNotFoundError, subprocess.TimeoutExpired) as e:
            return 1, str(e)

    # 1. Alat
    code, out = cmd(["yt-dlp", "--version"])
    item("Alat", "yt-dlp", code == 0, out.splitlines()[-1] if code == 0 else "tidak ada", "pip install -U yt-dlp")
    code, out = cmd(["ffmpeg", "-version"])
    item("Alat", "ffmpeg", code == 0, out.split("\n")[0][:40] if code == 0 else "tidak ada", "pasang ffmpeg")
    code, out = cmd(["ffprobe", "-version"])
    item("Alat", "ffprobe", code == 0, out.split("\n")[0][:40] if code == 0 else "tidak ada", "pasang ffmpeg")
    code, out = cmd([WHISPER_PY, "-c", "import transformers, torch; print(transformers.__version__, torch.__version__)"])
    item("Alat", "Whisper (transformers + torch)", code == 0, f"{WHISPER_PY}: {out.splitlines()[-1]}" if code == 0 else out[-120:],
         "pip install transformers torch, lalu set PIPELINE_PYTHON")
    code, out = cmd([WHISPER_PY, "-W", "ignore", "-c", "import importlib.metadata as m; print(m.version('voxcpm'))"], stdout_only=True)
    item("Alat", "VoxCPM", code == 0, out.splitlines()[-1] if code == 0 else "tidak ada", "pip install -r video/requirements-tts.txt")
    code, out = cmd(["node", "-p", "require('remotion/package.json').version + ' (cli ' + require('@remotion/cli/package.json').version + ')'"],
                    cwd=REMOTION, stdout_only=True)
    item("Alat", "Remotion", code == 0, out if code == 0 else "tidak ada", "cd video && npm install")
    code, out = cmd([openmontage_bin(), "--version"], stdout_only=True)
    item("Alat", "OpenMontage (montase gambar/klip)", code == 0, out if code == 0 else "tidak ada",
         'pip install "open-montage[render]==0.4.0" (lalu set OPENMONTAGE_BIN kalau tidak di PATH)')
    hf = HYPERFRAMES / "node_modules/.bin/hyperframes"
    code, out = cmd([str(hf), "--version"])
    item("Alat", "HyperFrames", code == 0, out.splitlines()[-1] if code == 0 else "tidak ada", "cd hyperframes && npm install")

    # 2. Data dan model yang dibutuhkan
    hub = Path(os.environ.get("HF_HOME", Path.home() / ".cache/huggingface")) / "hub"
    for model in [WHISPER_MODEL, "openbmb/VoxCPM2"]:
        d = hub / ("models--" + model.replace("/", "--"))
        item("Model", model, d.exists(), "sudah diunduh" if d.exists() else "belum diunduh (otomatis saat pertama dipakai)", "")
    voices = sorted(p.name for p in (REMOTION / "voices").glob("*") if (p / "prompt.wav").exists()) if (REMOTION / "voices").exists() else []
    item("Model", "Profil suara", bool(voices), ", ".join(voices) or "tidak ada", "buat profil di video/voices/<nama>/ (lihat scripts/tts.py)")
    mk = len(list((REMOTION / "public/mixkit").rglob("*.mp3"))) if (REMOTION / "public/mixkit").exists() else 0
    item("Model", "Sound effect Mixkit", mk > 0, f"{mk} file", "python3 video/scripts/mixkit_sfx_download.py")

    # Style: font dan sound effect yang dirujuk harus ada
    for sid, st in load_styles().items():
        missing = [st["font"]["file"]] if not (REMOTION / "public" / st["font"]["file"]).exists() else []
        missing += [v for k, v in st.get("sfx", {}).items() if k != "note" and not (REMOTION / "public" / v).exists()]
        item("Style", f"{sid}: {st['label']}", not missing, "font dan sound effect lengkap" if not missing else "tidak ada: " + ", ".join(missing),
             "unduh sound effect Mixkit (video/MIXKIT.md) atau perbaiki path di pipeline/styles/")

    # 3. Sambungan antar-alat
    code, out = cmd([sys.executable, "-c", f"import sys; sys.path.insert(0, {str(REMOTION / 'scripts')!r}); from sfx_analyze import analyze; print('ok')"])
    item("Sambungan", "pipeline -> sfx_analyze.py (ukur sound effect)", code == 0, "bisa diimpor" if code == 0 else out[-120:], "pip install numpy")
    item("Sambungan", "pipeline -> tts_batch.py (VoxCPM)", (REMOTION / "scripts/tts_batch.py").exists(), "video/scripts/tts_batch.py", "")
    code, out = cmd(["npx", "remotion", "compositions"], cwd=REMOTION, timeout=300)
    comps = [c for c in ["Subtitle", "StylePreview", "PromoSuara", "Promo"] if re.search(rf"^{c}\s", out, re.M)]
    item("Sambungan", "pipeline -> Remotion (komposisi Subtitle)", "Subtitle" in comps, "komposisi terdaftar: " + ", ".join(comps) if comps else out[-150:], "periksa video/src/Root.tsx")
    font = REMOTION / "public/fonts/PlusJakartaSans-latin.woff2"
    item("Sambungan", "Remotion -> HyperFrames (font bersama)", font.exists(), str(font.relative_to(ROOT)), "")
    meta = REMOTION / "src/promo/sfx-meta.json"
    item("Sambungan", "Remotion -> HyperFrames (ukuran sound effect bersama)", meta.exists(), str(meta.relative_to(ROOT)), "python3 video/scripts/sfx_analyze.py ...")
    sample = next((d for d in sorted((ROOT / "media").glob("*")) if (d / "pages.json").exists()), None) if (ROOT / "media").exists() else None
    if sample:
        pages = json.loads((sample / "pages.json").read_text())
        ok_shape = all({"startMs", "durationMs", "tokens"} <= p.keys() for p in pages)
        item("Sambungan", "Whisper -> pages.json -> Remotion + HyperFrames", ok_shape, f"media/{sample.name}: {len(pages)} halaman, format cocok", "")
        proj = sample / "hyperframes"
        if (proj / "index.html").exists():
            code, out = cmd([str(hf), "lint"], cwd=proj, timeout=300)
            errs = re.search(r"(\d+) error", out)
            item("Sambungan", "pages.json -> HyperFrames (HTML yang dibuat pipeline)", code == 0 and (not errs or errs.group(1) == "0"),
                 f"lint: {errs.group(0) if errs else 'lolos'}", "")
        om_dir = next((d for d in sorted((ROOT / "media").glob("*")) if (d / "montage.json").exists()), None)
        if om_dir:
            m = json.loads((om_dir / "meta.json").read_text())
            ok = (om_dir / "video.mp4").exists() and m.get("hasVideo") and m.get("source") == "video.mp4"
            item("Sambungan", "Open Montage -> video latar -> Remotion + HyperFrames", ok,
                 f"media/{om_dir.name}: {len(json.loads((om_dir / 'montage.json').read_text())['clips'])} visual jadi video.mp4", "")
        else:
            item("Sambungan", "Open Montage -> video latar -> Remotion + HyperFrames", None, "belum ada naskah dengan visual di media/", "")
    else:
        item("Sambungan", "Whisper -> pages.json -> Remotion + HyperFrames", None, "belum ada hasil di media/ untuk dicek", "jalankan pipeline sekali")
    code, out = cmd([str(hf), "telemetry", "status"], stdout_only=True)
    line = next((l.strip() for l in out.splitlines() if re.search(r"(?i)status|enabled|disabled", l) and "env var" not in l.lower()), out[:80])
    item("Privasi", "Telemetri HyperFrames", bool(re.search(r"(?i)disabled", line)), line[:80], "npx hyperframes telemetry disable")

    # 4. Skill untuk Claude (membantu saat menulis kode, bukan dipanggil oleh pipeline)
    sk = ROOT / ".claude/skills"
    rem = sorted(p.name for p in sk.glob("remotion-*")) if sk.exists() else []
    item("Skill", "Skill Remotion", len(rem) >= 13, f"{len(rem)} skill di .claude/skills/", "")
    hfs = [p for p in [Path.home() / ".claude/skills/hyperframes", sk / "hyperframes"] if p.exists()]
    item("Skill", "Skill HyperFrames", bool(hfs), "terpasang" if hfs else "belum (dilewati saat init)", "npx hyperframes skills update")

    print()
    group = None
    for g, n, ok, det, fix in results:
        if g != group:
            print(f"\n{g}")
            group = g
        mark = "✓" if ok else ("·" if ok is None else "✗")
        print(f"  {mark} {n:52s} {det}" + (f"\n      perbaikan: {fix}" if ok is False and fix else ""))
    bad = sum(1 for r in results if r[2] is False)
    print(f"\n{len(results)} pemeriksaan, {bad} perlu perhatian")
    return bad


FUNCS = dict(zip(STEPS, [step_fetch, step_prepare, step_transcribe, step_pages, step_scenes, step_sfx, step_montage, step_remotion, step_hyperframes, step_finish]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", nargs="?", help="URL video (yt-dlp), file video/audio, atau naskah .json")
    ap.add_argument("--name", help="nama folder di media/, misalnya kaca-mobil")
    ap.add_argument("--check", action="store_true", help="periksa semua alat dan sambungan, tanpa membuat apa pun")
    ap.add_argument("--style", default="auto", help="style visual: auto (bawaan, dari kata kunci topik) atau nama di pipeline/styles/")
    ap.add_argument("--list-styles", action="store_true", help="tampilkan daftar style")
    ap.add_argument("--preview-styles", nargs="?", const="", metavar="STYLE", help="buat gambar pratinjau style (semua, atau satu)")
    ap.add_argument("--preview-scenes", action="store_true", help="gambar diam dari tengah tiap adegan (style adriy), bukan video")
    ap.add_argument("--title", default="", help="judul di layar kalau sumbernya audio saja")
    ap.add_argument("--renderer", choices=["remotion", "hyperframes", "both"], default="remotion",
                    help="mesin render (bawaan: remotion; both = dua versi)")
    ap.add_argument("--visuals", help="folder gambar/klip untuk latar (Open Montage), dibagi rata sepanjang audio")
    ap.add_argument("--language", default="indonesian", help='bahasa untuk Whisper ("" = deteksi otomatis)')
    ap.add_argument("--from", dest="start", choices=STEPS, help="ulang mulai langkah ini (langkah setelahnya ikut diulang)")
    ap.add_argument("--only", choices=STEPS, help="jalankan satu langkah saja")
    ap.add_argument("--skip", action="append", default=[], choices=STEPS, help="lewati langkah ini (bisa berulang)")
    ap.add_argument("--until", choices=STEPS, help="berhenti setelah langkah ini (misalnya --until sfx untuk dengar suara dulu)")
    args = ap.parse_args()
    if args.check:
        raise SystemExit(1 if check() else 0)
    if args.list_styles:
        return list_styles()
    if args.preview_styles is not None:
        return preview_styles(args.preview_styles or None)
    if args.preview_scenes:
        if not args.name:
            ap.error("--preview-scenes butuh --name")
        return preview_scenes(args.name)
    if not args.input or not args.name:
        ap.error("butuh input dan --name (atau pakai --check)")

    ctx = {"input": args.input, "name": args.name, "title": args.title or args.name, "language": args.language, "force": False,
           "visuals": args.visuals, "style": args.style,
           "renderers": ["remotion", "hyperframes"] if args.renderer == "both" else [args.renderer]}
    ctx["dir"] = ROOT / "media" / args.name
    ctx["dir"].mkdir(parents=True, exist_ok=True)
    steps = [args.only] if args.only else STEPS[: STEPS.index(args.until) + 1] if args.until else STEPS
    t0 = time.time()
    for s in steps:
        if s in args.skip:
            log(s, "dilewati")
            continue
        if s in ("remotion", "hyperframes") and s not in ctx["renderers"]:
            continue  # tidak dipilih lewat --renderer
        ctx["force"] = bool(args.only) or (args.start is not None and STEPS.index(s) >= STEPS.index(args.start))
        if s != "fetch" and "source" not in ctx:
            found = sorted(ctx["dir"].glob("source.*"))
            if not found:
                raise SystemExit("Belum ada source.*: jalankan langkah fetch dulu")
            ctx["source"] = found[0]
        FUNCS[s](ctx)
    print(f"Selesai dalam {time.time() - t0:.0f} dtk. Hasil: {ctx['dir'].relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
