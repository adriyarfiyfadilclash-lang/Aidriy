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


_TEMA = None

# ---------------------------------------------------------------- judul yang sudah pernah dibuat
JUDUL_TERPAKAI = ROOT / "pipeline" / "judul-terpakai.json"
_STOP = set("""kenapa mengapa bagaimana apa apakah benarkah dan yang di ke dari itu ini bisa saat padahal kita tapi tetapi lebih
dengan setelah karena hampir selalu sering terasa versi baru ada adalah sebenarnya tidak pernah masih seluruh vs versus
punya jadi atau untuk pada para sih kok ternyata begitu seenak cara""".split())


_SINONIM = {"sehari": "hari", "harian": "hari", "perhari": "hari", "bilang": "ucap", "mengucap": "ucap", "mengucapkan": "ucap",
            "berwarna": "warna", "melayang": "layang", "mengapung": "apung", "hp": "ponsel", "handphone": "ponsel", "smartphone": "ponsel",
            "uang": "uang", "duit": "uang", "nabung": "menabung", "tabung": "menabung"}


def _dasar(w):
    """Pemotong imbuhan sederhana: membunyikan -> bunyi, berbentuk -> bentuk, melayang -> layang."""
    if len(w) <= 5 or w.isdigit():
        return w
    w2 = re.sub(r"(kan|an|i)$", "", w) if len(w) > 7 else w
    for pre, ganti in (("meng", ""), ("meny", "s"), ("mem", ""), ("men", ""), ("me", ""), ("ber", ""), ("ter", ""), ("di", ""), ("pe", "")):
        if w2.startswith(pre) and len(w2) - len(pre) >= 4:
            return ganti + w2[len(pre):]
    return w2


def _kata_judul(t):
    t = re.sub(r"\(.*?\)", " ", t.lower())
    # angka disamakan: "Rp10.000", "10 ribu", "10rb" -> 10000; "seratus ribu" -> 100000
    t = re.sub(r"rp\s*", "", t).replace("seratus ribu", "100000").replace("sepuluh ribu", "10000")
    t = re.sub(r"(\d+)[.,]000\b", r"\g<1>000", t)
    t = re.sub(r"(\d+)\s*(ribu|rb|k)\b", lambda m: str(int(m.group(1)) * 1000), t)
    t = re.sub(r"(\d+)\s*(juta|jt)\b", lambda m: str(int(m.group(1)) * 1000000), t)
    ws = re.findall(r"[a-z0-9]+", t.replace("-", ""))
    out = set()
    for w in ws:
        w = re.sub(r"(nya|lah|kah)$", "", w) if len(w) > 5 else w
        w = _SINONIM.get(w, w)
        w = _dasar(w)
        if w not in _STOP and w != "per" and len(w) > 1:
            out.add(w)
    return out


def judul_mirip(title):
    """Judul terpakai yang sama atau mirip dengan title (kata inti hampir sama), atau None."""
    try:
        daftar = json.loads(JUDUL_TERPAKAI.read_text(encoding="utf-8"))["judul"]
    except (OSError, ValueError, KeyError):
        return None
    import difflib
    a = _kata_judul(title)
    low = title.lower()
    for e in daftar:
        if e.get("inti") and all(re.search(pat, low) for pat in e["inti"]):
            return e
        b = _kata_judul(e["judul"])
        if not a or not b:
            continue
        overlap = len(a & b) / min(len(a), len(b))
        rasio = difflib.SequenceMatcher(None, " ".join(sorted(a)), " ".join(sorted(b))).ratio()
        sama = len(a & b)
        # sengaja ketat: lebih baik sesekali salah menolak (ada --judul-boleh-sama) daripada membuat ulang topik
        if (overlap >= 0.66 and sama >= 2) or rasio >= 0.85 or a == b:
            return e
    return None


def catat_judul(title, name):
    """Tambahkan judul video yang selesai ke daftar judul terpakai (kecuali video uji/contoh)."""
    if not title or name.startswith(("_", "contoh")) or title == name:
        return
    try:
        d = json.loads(JUDUL_TERPAKAI.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        d = {"judul": []}
    if any(e["judul"].strip().lower() == title.strip().lower() for e in d["judul"]):
        return
    d["judul"].append({"judul": title, "kategori": (tema_konten(title) or {}).get("nama", ""), "sumber": f"media/{name}"})
    JUDUL_TERPAKAI.write_text(json.dumps(d, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    log("finish", f"judul dicatat di {JUDUL_TERPAKAI.relative_to(ROOT)}")


def tema_konten(title):
    """Tema konten (pipeline/tema-konten.json) dari judul: tema pertama yang polanya cocok, atau None."""
    global _TEMA
    if _TEMA is None:
        f = ROOT / "pipeline" / "tema-konten.json"
        _TEMA = json.loads(f.read_text(encoding="utf-8"))["tema"] if f.exists() else []
    return next((t for t in _TEMA if title and re.search(t["kata"], title, re.I)), None)


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
        tk = tema_konten(ctx["title"])
        if tk and tk.get("style") in styles:
            return styles[tk["style"]], f"otomatis dari tema konten '{tk['nama']}'"
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
                "perbandingan": "Bandingkan", "tipografi": "", "sinematik": "Ingat", "angka": "Faktanya",
                "grafik": "Datanya", "lingkaran": "Porsinya", "timeline": "Lini masa", "peta": "Di mana?", "alur": "Alurnya",
                "potongan": "Di dalamnya", "persentase": "Faktanya"}
DATA_TYPES = {"angka", "diagram", "perbandingan", "infografis", "grafik", "lingkaran", "persentase", "alur"}
SCENE_KICKER["minimalis"] = "Jadi intinya"
SCENE_KICKER["lingkungan"] = ""

# ---------------------------------------------------------------- art direction: babak cerita, suasana, latar
# Babak: pembuka (memancing rasa penasaran) -> eksplorasi (membawa masuk ke topik) -> penjelasan ->
# bagian menarik (kejutan, angka, perbandingan) -> kesimpulan -> penutup brand. Suasana tiap babak memilih latar.
HERO_ENVS = {"langit", "alam", "kota", "dapur", "sains", "arsip", "pastel", "malam", "teknologi", "bawahlaut", "antariksa", "sabana", "jurnal", "lab"}
DARK_ENVS = {"malam", "teknologi", "brand", "antariksa", "bawahlaut"}
BAHAYA = r"\b(bahaya|berbahaya|awas|hati-hati|jangan|risiko|peringatan|beracun|kecelakaan)\b"
PENEMUAN = r"\b(ditemukan|menemukan|penemuan|berhasil|akhirnya|lahirlah|diciptakan)\b"


_KATALOG = None


def find_object(text):
    """Gambar objek (hewan, ...) dari katalog aset ADRIY yang disebut di kalimat. Frasa terpanjang menang;
    gambar realistis didahulukan dari versi kartun. None bila tidak ada katalog atau tidak ada yang cocok."""
    global _KATALOG
    if _KATALOG is None:
        f = REMOTION / "public" / "adri" / "objek" / "katalog.json"
        _KATALOG = json.loads(f.read_text())["objek"] if f.exists() else []
    best = None
    for it in _KATALOG:
        for kata in it.get("kata", []):
            m = re.search(rf"\b{re.escape(kata)}\b", text, re.I)
            if m:
                # yang paling dulu disebut (biasanya subjek) menang, lalu frasa terpanjang, lalu versi realistis
                score = (-m.start(), len(kata.split()), "kartun" not in it["nama"], not re.search(r"_[bc]$", it["nama"]))
                if best is None or score > best[0]:
                    best = (score, it)
    return best[1] if best else None


def art_direct(plan, title, style=None):
    """Atur babak cerita, suasana, latar, pose, kehadiran ADRI, dan transisi berdasarkan alur cerita,
    bukan rotasi acak. Dipanggil di akhir plan_scenes."""
    if not plan:
        return plan
    text_all = title + " " + " ".join(p["text"] for p in plan)
    # judul paling menentukan tema video; isi kalimat hanya cadangan
    # tema konten (mitos vs fakta, rekor dunia, ...) memberi latar cadangan bila judul tidak menyebut topik latar
    tk = tema_konten(title)
    theme = topic_env(title) or (tk or {}).get("env") or topic_env(text_all) or "cerah"
    # style tematik (bawah laut, antariksa, ...): satu lingkungan dan satu outfit untuk seluruh video
    tema = (style or {}).get("tema")
    if tema:
        theme = tema["env"]
    theme = {"sejarah": "arsip", "psikologi": "pastel"}.get(theme, theme)
    variant = sum(map(ord, title)) % 2  # dua gaya pembuka berbeda antarvideo
    n = len(plan)
    body = [i for i, p in enumerate(plan) if not p.get("outro")]
    for i, p in enumerate(plan):
        t = p["text"]
        if i == 0:
            phase = "pembuka"
        elif p.get("outro"):
            phase = "penutup"
        elif p["type"] == "minimalis":
            phase = "kesimpulan"
        elif p["type"] in ("angka", "persentase", "perbandingan") or (p["type"] == "objek" and p.get("layout") == "full") or re.search(FAKTA + "|" + PENEMUAN, t, re.I):
            phase = "menarik"
        elif i <= max(1, round(len(body) * 0.3)):
            phase = "eksplorasi"
        else:
            phase = "penjelasan"
        p["phase"] = phase
        # komposisi pembuka: close-up ADRI bertanya, atau wide shot lingkungan (berganti antarvideo)
        if i == 0 and not p.get("_given_type"):
            if variant == 0 or theme not in HERO_ENVS:
                p.update({"layout": "closeup", "pose": "tanya" if t.strip().endswith("?") else "jelaskan"})
            else:
                p.update({"type": "lingkungan", "pose": "tunjukkan"})
        # eksplorasi: wide shot lingkungan sekali (bila pembuka belum memakainya), objek dibuat close-up
        if phase == "eksplorasi" and not p.get("_given_type"):
            if p["type"] in ("karakter", "infografis", "tipografi") and theme in HERO_ENVS and not any(q["type"] == "lingkungan" for q in plan[:i]) and plan[i - 1]["type"] != "lingkungan":
                p["type"] = "lingkungan"
            elif p["type"] == "objek" and not p.get("layout"):
                p["layout"] = "closeup"
                p["title"] = _short_title(p["text"], 5).rstrip(".,")  # close-up: judul pendek di samping objek
        # kesimpulan berbeda antarvideo: adegan minimalis, atau close-up ADRI yang menyimpulkan di gelembung bicara
        if phase == "kesimpulan" and variant == 1 and not p.get("_given_type"):
            p.update({"type": "karakter", "layout": "closeup", "pose": "simpulkan"})
        # suasana -> latar
        own = topic_env(t)
        own = {"sejarah": "arsip", "psikologi": "pastel"}.get(own, own)
        if re.search(BAHAYA, t, re.I):
            env, mood = "bahaya", "peringatan"
        elif phase == "pembuka":
            env, mood = (theme if theme != "cerah" else own or theme), "penasaran"  # pembuka menetapkan tema video
        elif phase == "eksplorasi":
            env, mood = (own or theme), "eksploratif"
        elif phase == "menarik":
            env = {"perbandingan": "banding", "angka": "data", "persentase": "data"}.get(p["type"], "hangat")
            mood = "energik"
        elif phase == "kesimpulan":
            env, mood = "minimal", "elegan"
        elif phase == "penutup":
            env, mood = "brand", "cerah"
        else:
            env, mood = (own or (theme if i % 2 else "cerah")), "tenang"
        if p["type"] == "timeline":
            env = "arsip"
        if p["type"] in ("grafik", "lingkaran") and not own:
            env = "data"
        if "env" in p.get("_given", {}):
            env = p["_given"]["env"]
        if tema and "env" not in p.get("_given", {}):
            env = "brand" if phase == "penutup" else tema["env"]
        p["env"], p["mood"] = env, mood
        p["tone"] = "dark" if env in DARK_ENVS else "light"
        # pose sesuai narasi
        if "pose" not in p.get("_given", {}):
            if p.get("outro"):
                p["pose"] = "rayakan"
            elif phase == "kesimpulan":
                p["pose"] = "simpulkan"
            elif re.search(PENEMUAN, t, re.I):
                p["pose"] = "rayakan"
            elif t.strip().endswith("?") and p["type"] == "karakter":
                p["pose"] = "tanya"
            elif re.search(BAHAYA, t, re.I):
                p["pose"] = "angkat"  # peringatan: tangan terangkat
            elif re.search(FAKTA, t, re.I) and p["type"] in ("objek", "angka"):
                p["pose"] = "kaget"
        # ADRI tidak dipaksakan: grafik/peta/lingkungan bergantian tanpa ADRI supaya grafiknya jadi fokus
        if "adri" not in p.get("_given", {}):
            if p["type"] == "objek" and p.get("layout") == "closeup":
                p["adri"] = False
            elif p["type"] in ("grafik", "lingkaran", "peta", "timeline", "potongan") or (p["type"] == "lingkungan" and i > 0):
                visual_no = sum(1 for q in plan[:i] if q["type"] in ("grafik", "lingkaran", "peta", "timeline", "potongan", "lingkungan"))
                if visual_no % 2 == 0:  # visual pertama, ketiga, ...: grafik/lingkungan jadi fokus tanpa ADRI
                    p["adri"] = False
        # lebih sedikit teks bila visual sudah menjelaskan
        if p["type"] in ("grafik", "lingkaran", "peta", "timeline") and not p.get("_given", {}).get("title"):
            p["title"] = _short_title(p["text"], 5).rstrip(".")
        if p["type"] == "lingkungan" and not p.get("_given", {}).get("title"):
            # wide shot: satu kalimat utuh (sampai koma pertama), bukan potongan di tengah
            first = re.split(r"[,;]", p["text"])[0].strip()
            p["title"] = first if len(first.split()) <= 9 else _short_title(first, 8)
        if p["type"] == "lingkungan" or (p["type"] == "objek" and p.get("layout") in ("closeup", "full")):
            if not p.get("_given", {}).get("kicker"):
                p["kicker"] = ""
        p["family"] = family(p["type"], p.get("layout"))
    # ADRI paling tidak tiap 3 adegan
    run_ = 0
    for p in plan:
        has = p["type"] != "tipografi" and p.get("adri", True) is not False
        run_ = 0 if has else run_ + 1
        if run_ >= 3 and p.get("adri") is False and "adri" not in p.get("_given", {}):
            p["adri"] = True
            run_ = 0
    # adegan gelap dibatasi: paling banyak 35% (kecuali naskah memintanya)
    dark = [] if tema else [p for p in plan if p["env"] in DARK_ENVS and not p.get("outro")]
    for p in dark[max(1, round(0.35 * n)):]:
        if "env" not in p.get("_given", {}):
            p["env"], p["tone"] = "cerah", "light"
    # transisi berdasarkan cerita: pergantian babak = warna/lingkungan/match cut; di dalam babak = potongan
    # langsung atau gerak ringan; data = sapuan/bentuk; objek = iris/match. Tidak berulang, tiap jenis <= 25%.
    used = {}
    for i, p in enumerate(plan):
        if i == 0 or "transition" in p.get("_given", {}):
            used[p["transition"]] = used.get(p["transition"], 0) + (i > 0)
            continue
        prev = plan[i - 1]
        if p.get("outro"):
            wants = ["color", "shape", "match"]
        elif prev["phase"] != p["phase"]:
            wants = ["color", "envshift", "match", "cover"] if p["phase"] in ("menarik", "kesimpulan") else ["envshift", "match", "color", "whip"]
        elif p["type"] in DATA_TYPES:
            wants = ["wipe", "cut", "shape", "push"]
        elif p["type"] in ("objek", "potongan", "peta"):
            wants = ["iris", "match", "cut"]
        elif p.get("highlight") and p["text"].strip().endswith("!"):
            wants = ["zoom", "cut"]
        else:
            wants = ["cut", "smooth", "morph", "push"]
        cap = max(2, round(0.25 * (n - 1)))
        pick = next((t for t in wants if t != prev["transition"] and used.get(t, 0) < cap),
                    next((t for t in ["cut", "smooth", "push", "morph", "wipe", "iris"] if t != prev["transition"] and used.get(t, 0) < cap), "cut"))
        p["transition"] = pick
        used[pick] = used.get(pick, 0) + 1
    # outfit ADRIY satu untuk seluruh video (tidak berganti kostum di tengah cerita), dari topik
    # urutan = prioritas; judul diperiksa dulu, baru seluruh naskah
    OUTFITS = [
        ("angkasa", r"\b(luar angkasa|antariksa|astronot|planet|galaksi|roket|satelit|tata surya|mars|bulan|asteroid|orbit|nasa)\b"),
        ("penyelam", r"\b(laut|lautan|samudra|terumbu|karang|menyelam|penyelam|ikan|hiu|paus|gurita|ubur-ubur|bawah air)\b"),
        ("arkeolog", r"\b(arkeolog\w*|fosil|situs|candi|prasasti|artefak|purbakala|ekspedisi|sejarah|kerajaan|kuno|zaman)\b"),
        ("lab", r"\b(sains|ilmuwan|kimia|biologi|fisika|sel|virus|bakteri|dna|vaksin|obat|kedokteran|eksperimen|laboratorium|molekul|atom|penemuan)\b"),
        ("formal", r"\b(investasi|saham|ekonomi|bisnis|keuangan|uang|inflasi|bank|pajak|berita|politik|perusahaan|startup|karier)\b"),
        ("travel", r"\b(travel|wisata|liburan|jalan-jalan|pendakian|backpacker|destinasi|perjalanan ke)\b"),
    ]
    outfit = (tema or {}).get("outfit") or next((o for o, pat in OUTFITS if re.search(pat, title, re.I)), None) or next((o for o, pat in OUTFITS if re.search(pat, text_all, re.I)), None)
    # objek dari katalog (hewan yang disebut di kalimat): adegan ditampilkan sebagai objek dengan gambarnya.
    # Subjek video (hewan di judul) mengisi adegan objek pertama yang tidak menyebut hewan apa pun,
    # supaya tidak tampil ikon umum padahal topiknya hewan (judul hanya muncul di pembuka).
    subject = find_object(title)
    for i, p in enumerate(plan):
        g = p.get("_given", {})
        if p.get("visual") or "visual" in g or p.get("outro") or p["type"] in ("grafik", "lingkaran", "timeline", "peta", "perbandingan", "diagram", "alur", "angka", "persentase"):
            continue
        obj = find_object(p["text"])
        if not obj and subject and p["type"] == "objek" and p.get("phase") not in ("pembuka", "kesimpulan", "penutup") \
                and not any(q.get("visual") == subject["file"] for q in plan):
            obj = subject
        if not obj:
            continue
        # tidak monoton: bukan di pembuka/kesimpulan, gambar yang sama tidak berurutan, maks. 2 kali per video
        used_n = sum(1 for q in plan[:i] if q.get("visual") == obj["file"])
        if "type" not in g and (p.get("phase") in ("pembuka", "kesimpulan", "penutup") or used_n >= 2 or (i > 0 and plan[i - 1].get("visual") == obj["file"])):
            continue
        p.update({"visual": obj["file"], "cutout": obj["transparan"]})
        if not p.get("_given_type") and p["type"] not in ("objek", "potongan"):
            prev_full = i > 0 and plan[i - 1]["type"] == "objek"
            p["type"] = "objek"
            p["layout"] = "presenter" if prev_full else ("full" if p.get("phase") == "menarik" or i == 0 else "presenter")
            p["family"] = family("objek", p["layout"])
        if obj is subject or p.get("layout") == "closeup" or not any(":" in x and x.split(":", 1)[1].strip() for x in p.get("points", [])):
            p["points"] = [f"bintang:{' '.join(obj['base'].split('_')[:2]).capitalize()}"]
    # objek tanpa gambar tepat setelah adegan objek: jadi wide shot lingkungan (tanpa ADRIY) supaya tidak berulang
    for i, p in enumerate(plan[1:], 1):
        if p["type"] == "objek" and not p.get("visual") and plan[i - 1]["type"] == "objek" and not p.get("_given_type") and p["env"] in HERO_ENVS:
            p.update({"type": "lingkungan", "layout": None, "adri": False, "kicker": ""})
            p["family"] = family("lingkungan")
            first = re.split(r"[,;]", p["text"])[0].strip()
            p["title"] = first if len(first.split()) <= 9 else _short_title(first, 8)
    # kicker pembuka menyebut tema konten ("Mitos atau fakta?", "Rekor dunia", ...) bila adegannya punya kicker
    if tk and plan[0].get("kicker") and not plan[0].get("_given", {}).get("kicker") and not plan[0].get("outro"):
        plan[0]["kicker"] = tk["kicker"]
    for p in plan:
        if outfit and "outfit" not in p.get("_given", {}):
            p["outfit"] = outfit
        p.pop("_given", None)
        p.pop("_given_type", None)
    return plan
# ADRI tampil di semua jenis adegan kecuali tipografi (sinematik: ADRI kecil berjalan melintas)
WITH_ADRI = set(SCENE_KICKER) - {"tipografi"}

# Keluarga komposisi: dua adegan berurutan sebaiknya tidak sekeluarga (kecuali data yang bersambung).
def family(kind, layout=None):
    if kind == "objek" and layout == "closeup":
        return "objek-dekat"
    if kind == "karakter" and layout == "closeup":
        return "karakter-dekat"
    return {"objek": "objek-penuh" if layout == "full" else "presenter-objek", "lingkungan": "lingkungan", "potongan": "presenter-objek", "karakter": "presenter",
            "tipografi": "tipografi", "angka": "tipografi", "infografis": "infografis", "grafik": "infografis", "lingkaran": "infografis",
            "persentase": "infografis", "timeline": "linimasa", "peta": "linimasa", "diagram": "proses", "alur": "proses",
            "perbandingan": "perbandingan", "sinematik": "sinematik", "minimalis": "minimalis"}.get(kind, kind)

# Jenis kamera bawaan tiap adegan (cermin dari scenes.tsx). Bila sama dengan adegan sebelumnya, adegan
# memakai kamera penggantinya (altShot) supaya gerak kamera tidak berulang.
DEFAULT_SHOT = {"karakter": "emphasis", "objek": "focus", "objek-full": "reveal", "objek-closeup": "focus", "karakter-closeup": "emphasis",
                "lingkungan": "establish", "infografis": "track", "diagram": "path",
                "perbandingan": "perspective", "tipografi": "drift", "sinematik": "track", "angka": "focus", "grafik": "focus",
                "lingkaran": "establish", "timeline": "path", "peta": "path", "alur": "track", "potongan": "focus",
                "persentase": "focus", "minimalis": "drift"}
ALT_SHOT = {"karakter": "reveal", "objek": "reveal", "objek-full": "focus", "objek-closeup": "rack", "karakter-closeup": "reveal",
            "lingkungan": "pan", "infografis": "panV", "perbandingan": "rack",
            "tipografi": "panV", "angka": "reveal", "grafik": "establish", "lingkaran": "focus", "alur": "panV",
            "potongan": "reveal", "persentase": "emphasis", "minimalis": "establish"}

# Gaya subtitle bawaan (cermin dari SUBTITLE_DEFAULT di scenes.tsx)
SUBTITLE = {"lingkungan": "standar", "karakter": "kinetik", "sinematik": "tanpa", "angka": "kata", "persentase": "kata", "diagram": "minimalis",
            "grafik": "minimalis", "lingkaran": "minimalis", "timeline": "minimalis", "peta": "minimalis", "alur": "minimalis",
            "minimalis": "minimalis", "tipografi": "minimalis"}

KESIMPULAN = r"^\s*(jadi|intinya|kesimpulannya|singkatnya|pada akhirnya|ringkasnya)\b"
FAKTA = r"\b(tahukah|ternyata|faktanya|uniknya|fakta unik|mengejutkan|percaya atau tidak)\b"
CARA = r"\b(cara|tips|bagaimana caranya)\b"
PROSES = r"\b(proses|terjadi ketika|berubah menjadi|mengalir|tahap)\b"

def find_comparison(text):
    """'A butuh 10 detik, sedangkan B 60 detik' -> (kiri, kanan). None kalau bukan perbandingan."""
    m = re.split(r"\s*(?:,\s*)?\b(?:sedangkan|dibanding(?:kan)?|daripada|vs\.?|versus|berbanding)\b\s*", text, maxsplit=1, flags=re.I)
    if len(m) != 2:
        return None
    sides = []
    for part in m:
        n = re.search(r"(±\s*)?(\d+(?:[.,]\d+)?)\s*(%|persen|detik|dtk|menit|jam|kali|kg|km|ribu|juta)?", part)
        label = re.sub(r"\b(butuh|perlu|hanya|sekitar|selama|cuma|dengan|adalah)\b.*$", "", part.split(",")[0], flags=re.I).strip(" .,")
        label = _short_title(label, 3) if label else "?"
        if n:
            unit = {"persen": "%", "detik": "dtk"}.get((n.group(3) or "").lower(), n.group(3) or "")
            sides.append({"label": label, "value": f"{n.group(1) or ''}{n.group(2)} {unit}".strip(), "num": float(n.group(2).replace(",", "."))})
        else:
            sides.append({"label": label, "value": label, "num": None})
    if sum(1 for x in sides if x["num"] is not None) < 1:
        return None
    better = re.search(r"\blebih (cepat|baik|hemat|aman|murah|kuat|efektif|bersih|jernih)\b", text, re.I)
    left, right = sides
    if better and left["num"] is not None and right["num"] is not None:
        good_low = better.group(1).lower() in ("cepat", "hemat", "murah")
        win_right = (right["num"] < left["num"]) if good_low else (right["num"] > left["num"])
        (right if win_right else left)["win"] = True
    for x in sides:
        x.pop("num")
    return left, right

# ---------------------------------------------------------------- topik -> latar (env)
# Latar tetap berbasis navy brand, tapi aksennya mengikuti topik: tidak selalu biru yang sama.
TOPIC_ENV = [
    ("sejarah", r"\b(sejarah|abad|kerajaan|raja|perang|zaman|kuno|kolonial|merdeka|kemerdekaan|candi|prasasti|tahun 1\d{3})\b"),
    ("teknologi", r"\b(teknologi|ai|aplikasi|internet|komputer|chip|robot|digital|software|data center|ponsel|hp|kode)\b"),
    ("sains", r"\b(sel|atom|molekul|suhu|uap|reaksi|energi|kimia|fisika|biologi|ilmuwan|embun|tekanan|oksigen|virus|bakteri)\b"),
    ("alam", r"\b(hutan|laut|gunung|hewan|cuaca|sungai|pohon|pantai|iklim|bumi|tanaman)\b"),
    ("kota", r"\b(kota|jalan|mobil|gedung|macet|transportasi|kendaraan|motor|kereta)\b"),
    ("psikologi", r"\b(psikologi|otak|emosi|perasaan|stres|cemas|kebiasaan|motivasi|pikiran|mental|bahagia|takut)\b"),
    ("langit", r"\b(langit|awan|matahari|atmosfer|cuaca|pelangi|senja|petir|angkasa|bintang|bulan)\b"),
    ("dapur", r"\b(makanan|masak|memasak|dapur|bumbu|resep|minuman|nasi|kopi|teh|rasa|gizi)\b"),
]

def topic_env(text):
    """Pilih latar dari kata kunci; None kalau tidak ada yang cocok."""
    best = None
    for env, pat in TOPIC_ENV:
        n = len(re.findall(pat, text, re.I))
        if n and (best is None or n > best[1]):
            best = (env, n)
    return best[0] if best else None

# Koordinat kasar kota/pulau Indonesia di peta Adriy (u: barat-timur 0..1, v: utara-selatan 0..1)
PLACES = {"aceh": (0.07, 0.22), "medan": (0.16, 0.36), "padang": (0.2, 0.55), "palembang": (0.3, 0.66), "lampung": (0.32, 0.72),
          "jakarta": (0.36, 0.75), "bandung": (0.39, 0.78), "semarang": (0.45, 0.76), "yogyakarta": (0.45, 0.8), "jogja": (0.45, 0.8),
          "surabaya": (0.52, 0.77), "bali": (0.6, 0.81), "lombok": (0.64, 0.81), "pontianak": (0.42, 0.45), "banjarmasin": (0.5, 0.6),
          "balikpapan": (0.55, 0.5), "samarinda": (0.56, 0.45), "makassar": (0.66, 0.67), "palu": (0.66, 0.45), "manado": (0.72, 0.3),
          "ambon": (0.78, 0.55), "surakarta": (0.47, 0.79), "solo": (0.47, 0.79), "malang": (0.53, 0.8), "bogor": (0.37, 0.77),
          "cirebon": (0.41, 0.76), "bukittinggi": (0.19, 0.52), "pekanbaru": (0.23, 0.45), "banda aceh": (0.05, 0.2), "ternate": (0.77, 0.35), "jayapura": (0.97, 0.52), "sumatra": (0.2, 0.5), "jawa": (0.45, 0.76),
          "kalimantan": (0.48, 0.4), "sulawesi": (0.68, 0.45), "papua": (0.9, 0.55), "kupang": (0.72, 0.86), "nusa tenggara": (0.68, 0.82)}

def find_places(text):
    found = []
    for name, (u, v) in PLACES.items():
        m = re.search(rf"\b{name}\b", text, re.I)
        if m and not any(f[0] == (u, v) for f in found):
            found.append(((u, v), m.start(), name))
    found.sort(key=lambda f: f[1])
    return [{"u": uv[0], "v": uv[1], "label": n.title()} for uv, _, n in found][:5]

def find_years(text):
    """Pasangan (tahun, keterangan singkat) dari kalimat seperti 'Tahun 1920 ..., 1939 ...'."""
    out = []
    parts = re.split(r"(?=\b(?:1[5-9]\d{2}|20\d{2})\b)", text)
    for part in parts:
        m = re.match(r"(1[5-9]\d{2}|20\d{2})\b[\s,:-]*(.*)", part.strip())
        if m:
            label = re.sub(r"^(tahun|pada|di)\s+", "", m.group(2).strip(" ,.;"), flags=re.I)
            out.append({"year": m.group(1), "label": _short_title(label, 5) if label else ""})
    return out[:5]

def find_numbers(text):
    """Daftar angka berlabel ('napas 55 persen, baju basah 25 persen') untuk grafik/lingkaran."""
    data = []
    for m in re.finditer(r"([A-Za-zÀ-ÿ][\w\s-]{1,24}?)\s+(?:sekitar\s+|kira-kira\s+|±\s*)?(\d+(?:[.,]\d+)?)\s*(%|persen|detik|menit|jam|kali|juta|ribu)?", text):
        label = re.sub(r"^(dan|lalu|serta|sedangkan|sementara|yaitu|adalah|sebesar|hanya|ada|dari)\s+", "", m.group(1).strip(), flags=re.I)
        label = re.sub(r"(\s+(butuh|perlu|selama|sekitar|sebesar|hanya|mencapai|sampai|hingga|cuma|dari))+$", "", label, flags=re.I)
        label = " ".join(label.split()[-2:])
        label = re.sub(r"^(dari|dan|di|ke|yang|oleh|menghasilkan|mengandung|berisi|terdiri)\s+", "", label, flags=re.I)
        label = label[:1].upper() + label[1:]
        data.append({"label": label, "value": float(m.group(2).replace(",", ".")), "unit": m.group(3) or ""})
    return data[:5]


ICON_WORDS = [("matahari", r"matahari|langit|cahaya|sinar|siang|senja"), ("kereta", r"kereta|lokomotif|\brel\b|stasiun"),
              ("makan", r"makanan|makan|masak|dapur|bumbu|resep"), ("mobil", r"mobil|kendaraan|berkendara|kaca depan"), ("drop", r"air|uap|embun|kabut|lembap|lembab|hujan|basah"),
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
    outro_at = next((j for j, l in enumerate(lines) if re.search(r"adri\.?\s*project|inilah adri|explore more|know more", l["text"], re.I)), len(lines))
    style = dict(style, _topic_env=topic_env(title + " " + " ".join(ln["text"] for ln in lines)))
    plan = []
    for i, ln in enumerate(lines):
        text, given = ln["text"], dict(ln.get("scene") or {})
        words = text.split()
        num = re.search(r"(\d+(?:[.,]\d+)?)\s*(%|persen|derajat|°|kilometer per jam|km/jam|kilometer|km|meter|kg|liter|kali|detik|menit|jam|tahun|juta|ribu)?", text)
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
            kind = "karakter"  # pembuka/pertanyaan
        elif re.search(KESIMPULAN, text, re.I) or (i == outro_at - 1 and i >= 3 and not re.search(r"\d", text)):
            kind = "minimalis"  # kesimpulan
        elif find_comparison(text):
            kind = "perbandingan"
        elif len(find_years(text)) >= 2:
            kind = "timeline"  # sejarah: dua tahun atau lebih
        elif len(find_places(text)) >= 2:
            kind = "peta"
        elif len(find_numbers(text)) >= 2:
            nums = find_numbers(text)
            kind = "lingkaran" if all(n["unit"] in ("%", "persen") for n in nums) and 80 <= sum(n["value"] for n in nums) <= 120 else "grafik"
        elif re.search(FAKTA, text, re.I) and not num:
            kind = "objek"  # fakta: objek penuh layar dibuka dengan zoom
            given.setdefault("layout", "full")
        elif re.search(r"\b(lapisan|terdiri dari|bagian dalam|di dalamnya|isi dari)\b", text, re.I) and len(_clauses(text)) >= 2:
            kind = "potongan"
        elif re.search(r"\b(jika|kalau|apabila)\b.*\b(maka|lakukan|ulangi)\b", text, re.I):
            kind = "alur"
        elif num and (num.group(2) or "").lower() in ("%", "persen") and not (plan and plan[-1]["type"] == "persentase"):
            kind = "persentase" if sum(1 for p in plan if p["type"] == "angka") >= sum(1 for p in plan if p["type"] == "persentase") else "angka"
        elif num and not re.search(r"\b(langkah|pertama|kedua)\b", text, re.I):
            kind = "angka"
        elif (re.search(CARA, text, re.I) or re.search(PROSES, text, re.I)) and len(_clauses(text)) >= 2:
            kind = "diagram"  # cara melakukan / proses -> langkah demi langkah
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
        with_adri = WITH_ADRI
        if not given.get("type") and len(plan) >= every - 1 and all(p["type"] not in with_adri for p in plan[-(every - 1):]) and kind not in with_adri:
            kind = "karakter"
        # komposisi: jangan sekeluarga dengan adegan sebelumnya, kecuali isinya memang menuntut (data, proses, ...)
        lay = given.get("layout")
        if not given.get("type") and plan and family(kind, lay) == plan[-1].get("family") and kind in ("objek", "karakter", "infografis", "tipografi", "sinematik"):
            if kind == "objek":
                lay = "presenter" if lay == "full" else "full"
                given["layout"] = lay
            else:
                alt = [k for k in ["karakter", "infografis", "tipografi", "objek", "sinematik"] if family(k) != plan[-1]["family"] and (fits.get(k) and ok(k))]
                kind = alt[0] if alt else kind
        sc = {"type": kind, "line": ln["index"], "text": text, "_given": dict(ln.get("scene") or {}), "_given_type": bool((ln.get("scene") or {}).get("type"))}
        # isi adegan
        if kind == "angka" and num:
            v = float(num.group(1).replace(",", "."))
            unit = {"persen": "%", "derajat": "°", "kilometer per jam": " km/jam", "km/jam": " km/jam", "kilometer": " km", "km": " km"}.get((num.group(2) or "").lower(), num.group(2) or "")
            sc.update({"value": v, "unit": unit, "label": _short_title(text, 9), "pose": "kaget"})
            if unit == "%":
                sc["max"] = 100  # pengukur hanya untuk persen; skala lain tidak punya batas yang bermakna
        elif kind == "perbandingan" and find_comparison(text):
            sc["left"], sc["right"] = find_comparison(text)
            sc["_vs_title"] = f"{sc['left']['label']} vs {sc['right']['label']}"
        elif kind == "minimalis":
            core = re.sub(KESIMPULAN + r"[,\s]*", "", text, flags=re.I).strip()
            sc["title"] = _short_title(core or text, 8)
            sc["highlight"] = max(re.findall(r"[\w-]+", core or text), key=len)
        elif kind == "persentase" and num:
            sc.update({"value": float(num.group(1).replace(",", ".")), "label": _short_title(text, 9), "pose": "angkat"})
        elif kind == "timeline":
            sc["events"] = find_years(text)
        elif kind == "peta":
            sc["pins"] = find_places(text)
        elif kind in ("grafik", "lingkaran"):
            nums = find_numbers(text)
            sc["data"] = [{"label": n["label"], "value": n["value"]} for n in nums]
            u = nums[0]["unit"] if nums else ""
            sc["unit"] = {"persen": "%", "detik": "s"}.get(u, u)
        elif kind == "potongan":
            # "X terdiri dari A, B, dan C" -> lapisan A, B, C
            m = re.search(r"\b(?:terdiri dari|lapisan(?:nya)?|bagian dalam(?:nya)?|di dalamnya ada|isi dari)\b[:\s]*(.*)", text, re.I)
            body = m.group(1) if m else text
            parts = [p.strip(" .") for p in re.split(r",|\bdan\b|\bserta\b", body) if p.strip(" .")]
            sc["layers"] = [_short_title(p, 3) for p in parts][:4]
        elif kind == "alur":
            # "Jika <kondisi>, <aksi> lalu <aksi>" -> langkah aksi + percabangan "<kondisi>?"
            m = re.search(r"\b(?:jika|kalau|apabila)\s+([^,]+),?\s*(.*)", text, re.I)
            cond, rest = (m.group(1), m.group(2)) if m else ("Berhasil", text)
            acts = [a.strip(" .") for a in re.split(r"\blalu\b|\bkemudian\b|\bdan\b|,", rest) if a.strip(" .")]
            acts = [a for a in acts if not re.match(r"(ulangi|maka)$", a, re.I)] or ["Coba lagi"]
            sc["steps"] = [_short_title(a, 3) for a in acts][:2] + ["Selesai"]
            c = _short_title(cond, 3).rstrip(".?")
            sc["decision"] = c[:1].upper() + c[1:] + "?"
        elif kind == "diagram":
            body = text.split(":", 1)[1] if ":" in text[:40] else text  # "Cara mengatasinya: ..." -> langkahnya saja
            sc["steps"] = [c[:1].upper() + c[1:28] for c in _clauses(body) if not re.match(CARA + r"\s*\w*$", c, re.I)]
        elif kind == "infografis":
            sc["points"] = [f"{guess_icon(c)}:{c[:40]}" for c in _clauses(text)]
        elif kind in ("objek", "sinematik"):
            if has_visual:
                sc["visual"] = ln["visual"]
            cl = _clauses(text) or [text]
            # label pendek utuh per kata (bukan dipotong di tengah kata); kata pembuka fakta dibuang
            def lab(c):
                ws = (re.sub(FAKTA + r"[,\s]*", "", c, flags=re.I).strip() or c).split()[:6]
                while len(" ".join(ws)) > 32 and len(ws) > 2:
                    ws = ws[:-1]
                while len(ws) > 2 and ws[-1].lower() in ("yang", "di", "itu", "adalah", "dan", "ke", "dari", "untuk"):
                    ws = ws[:-1]
                t = " ".join(ws).rstrip(".,")
                return t[:1].upper() + t[1:]
            sc["points"] = [f"{guess_icon(text)}:"] + [f"{guess_icon(c)}:{lab(c)}" for c in cl[:3]] if kind == "objek" else []
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
        if sc.pop("_vs_title", None) and not given.get("title"):
            sc["title"] = f"{sc['left']['label']} vs {sc['right']['label']}"  # judul pendek: nilainya sudah tampil besar
            sc["highlight"] = sc["right" if sc["right"].get("win") else "left"]["label"].split()[0]
        if kind == "minimalis":
            core = text
            while re.match(KESIMPULAN, core, re.I):  # "Jadi intinya, ..." -> "..."
                core = re.sub(KESIMPULAN + r"[,:\s]*", "", core, count=1, flags=re.I).strip()
            sc["title"] = _short_title(core or text, 8)
            sc["kicker"] = SCENE_KICKER["minimalis"]
        hl = ln.get("highlight") or (num.group(0).strip() if num else None)
        if hl:
            sc["highlight"] = hl
        is_full = kind == "objek" and given.get("layout") == "full"
        # transisi menurut konteks
        # pindah topik = sapuan kamera, data = sapuan diagonal, penekanan = zoom, objek = iris dari objeknya,
        # lanjutan = bentuk/gumpalan/dorong. Tidak pernah sama dengan transisi sebelumnya.
        prev_tr = plan[-1]["transition"] if plan else None
        wants = (["slide"] if i == 0 else ["whip", "slide"] if ln.get("topic") else
                 ["cover", "iris"] if is_full else ["smooth", "cover"] if kind == "minimalis" else
                 ["iris", "shape"] if kind in ("objek", "potongan", "peta") else
                 ["wipe", "shape", "push"] if kind in DATA_TYPES else
                 ["zoom", "iris"] if (hl or text.strip().endswith("!")) else ["morph", "smooth", "push", "shape", "wipe"])
        rot = wants[i % len(wants):] + wants[:i % len(wants)]
        sc["transition"] = next((t for t in rot if t != prev_tr), "push" if prev_tr != "push" else "morph")
        sc["tone"] = tones[i % 3]
        sc["camera"] = cams[i % len(cams)]
        # latar: topik kalimat, atau topik video; data memakai latar data; diselingi umum/terang supaya bervariasi
        # topik video hanya dipakai berselang; topik kalimat sendiri selalu didahulukan
        own = topic_env(text)
        env = own or (style.get("_topic_env") if i % 2 == 0 else None)
        if kind in ("grafik", "angka", "persentase", "lingkaran") and not own:
            env = "data"
        if kind == "timeline":
            env = "sejarah"
        # latar khusus komposisi: perbandingan terbagi, kesimpulan minimal, fakta kontras
        env = {"perbandingan": "banding", "minimalis": "minimal"}.get(kind, "fakta" if is_full else env)
        last_env = plan[-1].get("env") if plan else None
        if not env or (env == last_env and kind != "timeline"):
            pool = ["umum", "terang", "data"]  # gedung (kota) hanya untuk topik kota
            env = next(e for e in pool[i % 3:] + pool[:i % 3] if e != last_env)
        sc["env"] = env
        sc["tone"] = "light" if env == "terang" else ("blue" if env == "kota" else "dark")
        sc.update({k: v for k, v in given.items() if k != "type"})
        sc["family"] = family(sc["type"], sc.get("layout"))
        sc.setdefault("subtitle", "tanpa" if sc["type"] == "objek" and sc.get("layout") == "full" else SUBTITLE.get(sc["type"], "standar"))
        # kamera: bila jenis kamera bawaannya sama dengan kamera adegan sebelumnya, pakai kamera pengganti
        key = "objek-full" if sc["type"] == "objek" and sc.get("layout") == "full" else sc["type"]
        shot = DEFAULT_SHOT.get(key, "drift")
        if plan and plan[-1].get("_shot") == shot and key in ALT_SHOT and "shot" not in given:
            sc["altShot"] = True
            shot = ALT_SHOT[key]
        sc["_shot"] = shot
        # waktu: dari awal kalimat (dikurangi tumpang tindih transisi) sampai awal kalimat berikutnya
        start = 0.0 if i == 0 else max(0.0, ln["start"] - overlap)
        end = lines[i + 1]["start"] + overlap if i + 1 < len(lines) else duration
        sc.update({"start": round(start, 3), "end": round(end, 3)})
        plan.append(sc)
    # ADRI berjalan keluar di akhir adegan karakter bila adegan berikutnya bukan adegan karakter
    for a, b in zip(plan, plan[1:]):
        if a["type"] == "karakter" and b["type"] != "karakter" and not a.get("outro"):
            a.setdefault("adriExit", "left" if a.get("side", "right") == "right" else "right")
    plan = art_direct(plan, title, style)
    # kamera dihitung ulang setelah art direction (tata letak/jenis bisa berubah)
    prev_shot = None
    for sc in plan:
        sc.pop("_shot", None)
        key = {"full": "objek-full", "closeup": f"{sc['type']}-closeup"}.get(sc.get("layout"), sc["type"]) if sc["type"] in ("objek", "karakter") else sc["type"]
        shot = DEFAULT_SHOT.get(key, "drift")
        sc.pop("altShot", None)
        if prev_shot == shot and key in ALT_SHOT and "shot" not in sc:
            sc["altShot"] = True
            shot = ALT_SHOT[key]
        sc["shotKind"] = prev_shot = shot
        sc["subtitle"] = "tanpa" if sc["type"] == "objek" and sc.get("layout") == "full" else SUBTITLE.get(sc["type"], "standar") if "subtitle" not in sc or sc["subtitle"] in SUBTITLE.values() or sc["subtitle"] == "standar" else sc["subtitle"]
    return plan


def check_standard(plan, style):
    """Periksa rencana adegan terhadap aturan standar. Kembalikan daftar pelanggaran."""
    rules = style.get("scenes", {}).get("rules", {})
    issues = []
    for a, b in zip(plan, plan[1:]):
        if rules.get("noRepeatType", True) and a["type"] == b["type"] and a.get("family") == b.get("family"):
            issues.append(f"adegan {plan.index(b) + 1}: jenis '{b['type']}' sama dengan adegan sebelumnya")
        if a.get("env", a["tone"]) == b.get("env", b["tone"]) and a["type"] == b["type"]:
            issues.append(f"adegan {plan.index(b) + 1}: komposisi (latar + jenis) sama dengan sebelumnya")
        if a.get("family") and a.get("family") == b.get("family") and b["type"] in ("objek", "karakter", "infografis", "tipografi", "sinematik"):
            issues.append(f"adegan {plan.index(b) + 1}: komposisi '{b['family']}' sama dengan sebelumnya")
        if a.get("shotKind") and a.get("shotKind") == b.get("shotKind") and b["type"] not in ("diagram", "timeline", "peta", "sinematik"):
            issues.append(f"adegan {plan.index(b) + 1}: gerak kamera '{b['shotKind']}' sama dengan sebelumnya")
        if a["end"] - b["start"] > 0.9:
            issues.append(f"adegan {plan.index(b) + 1}: transisi {a['end'] - b['start']:.2f} dtk terlalu panjang")
        if a["transition"] == b["transition"]:
            issues.append(f"adegan {plan.index(b) + 1}: transisi '{b['transition']}' sama dengan sebelumnya")
    every = rules.get("characterEvery", 3)
    with_adri = WITH_ADRI
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
    # art direction: latar gelap tidak mendominasi, transisi bervariasi, ADRI tidak dipaksakan di semua adegan
    n = len(plan)
    dark = sum(1 for sc in plan if sc.get("tone") == "dark")
    if n >= 4 and dark / n > 0.4 and not style.get("tema"):  # style tematik gelap (antariksa) memang sengaja
        issues.append(f"latar gelap {dark} dari {n} adegan: terlalu banyak (maks. 40%)")
    trs = [sc["transition"] for sc in plan[1:]]
    for t in set(trs):
        if len(trs) >= 5 and trs.count(t) / len(trs) > 0.34:
            issues.append(f"transisi '{t}' dipakai {trs.count(t)} dari {len(trs)} kali: kurang bervariasi")
    with_a = sum(1 for sc in plan if sc["type"] != "tipografi" and sc.get("adri", True) is not False)
    if n >= 6 and with_a == n:
        issues.append("ADRI muncul di semua adegan: beri ruang untuk objek, lingkungan, atau grafik sebagai fokus")
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
    tk = tema_konten(ctx["title"])
    log("scenes", f"tema konten: {tk['nama']}" if tk else "tema konten: tidak dikenali dari judul (lihat pipeline/tema-konten.json)")
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
    t = time.time()
    comp = "AdriyVideo" if style.get("engine") == "adriy" else "Subtitle"
    log("remotion", f"render komposisi {comp}...")
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
                 "active": prev.get("active", 0), "visual": ""}  # kosong = ADRI dengan penyelesaian khas style
        out = out_dir / f"{sid}.png"
        if st.get("engine") == "adriy":
            # style beradegan: pratinjau adegan pembuka (close-up ADRIY bertanya) di lingkungan dan outfit style itu
            tema = st.get("tema", {})
            extra = {"layout": "closeup", "pose": "tanya", "title": prev.get("title", st["label"]), "kicker": "Adri.project"}
            if tema:
                extra.update({"env": tema["env"], "outfit": tema["outfit"]})
            props = {"type": "karakter", "styleId": sid, "at": 3.0, "caption": prev.get("caption", "Contoh subtitle"), "extra": extra}
            run(["npx", "remotion", "still", "ScenePreview", str(out), f"--props={json.dumps(props, ensure_ascii=False)}"], cwd=REMOTION)
        else:
            run(["npx", "remotion", "still", "StylePreview", str(out), f"--props={json.dumps(props, ensure_ascii=False)}"], cwd=REMOTION)
        print(f"{sid:10s} -> {out.relative_to(ROOT)}")


def pixel_checks(folder, skip=()):
    """Pemeriksaan piksel pada gambar QA: latar yang terputus (mis. lantai habis saat kamera bergeser)
    dan gambar yang terlalu buram di tengah adegan. `skip`: awalan nama gambar adegan yang sengaja memotong
    bingkai (close-up karakter). Mengembalikan daftar (gambar, pesan)."""
    from PIL import Image, ImageFilter, ImageStat
    found = []
    for f in sorted(Path(folder).glob("*.png")):
        if any(f.name.startswith(p) for p in skip):
            continue
        rgb = Image.open(f).convert("RGB")
        im = rgb.convert("L")
        w, h = im.size
        # di bawah pita subtitle latar harus menyambung. Lantai dan langit sama-sama gelap, jadi yang dicari
        # tepi tegak yang kecil tapi tajam (gradasi biasa berubah sangat halus).
        def edges(y, t):
            row = [rgb.getpixel((x, y)) for x in range(w)]
            return {x for x in range(3, w) if sum(abs(a - b) for a, b in zip(row[x], row[x - 3])) > t}
        # tepi harus memanjang tegak (3 baris berjarak 30 px), jadi partikel kecil tidak terhitung
        r1, r2, r3 = edges(h - 120, 9), edges(h - 90, 6), edges(h - 60, 6)
        # 20 px tepi layar diabaikan (vinyet tekstur kertas berubah tajam di tepi)
        hit = sorted(x for x in r1 if 20 < x < w - 20 and any(abs(x - x2) <= 4 for x2 in r2) and any(abs(x - x3) <= 4 for x3 in r3))
        if hit and "transisi" not in f.name:
            found.append((f.name, f"latar terputus di bawah layar (x={hit[0]})"))
        # ketajaman: variasi tepi sangat rendah di tengah adegan = buram
        if "tengah" in f.name:
            edges = ImageStat.Stat(im.crop((0, 140, w, 1440)).filter(ImageFilter.FIND_EDGES)).var[0]
            if edges < 25:
                found.append((f.name, f"gambar tampak buram (variasi tepi {edges:.0f})"))
    return found


def qa_scenes(name=None):
    """Pemeriksaan visual dan teknis (gambar diam, bukan video). Dengan name: video media/<name>.
    Tanpa name: contoh semua jenis adegan."""
    out = ROOT / "media" / (name or "_contoh") / "qa"
    if name:
        d = ROOT / "media" / name
        if not (d / "scenes.json").exists():
            raise SystemExit("Belum ada scenes.json: jalankan dulu dengan --style adriy --until scenes")
        pub = REMOTION / "public" / "media" / name
        pub.mkdir(parents=True, exist_ok=True)
        if not (d / "style.json").exists():
            (d / "style.json").write_text(json.dumps(load_styles()["adriy"], ensure_ascii=False, indent=1), encoding="utf-8")
        for f in ["meta.json", "pages.json", "audio.wav", "style.json", "scenes.json"]:
            if (d / f).exists():
                shutil.copy2(d / f, pub / f)
        cmd = ["node", "scripts/qa.mjs", "--media", name, "--out", str(out)]
    else:
        cmd = ["node", "scripts/qa.mjs", "--samples", "--out", str(out)]
    run(cmd, cwd=REMOTION)
    skip = ()
    if name:
        plan = json.loads((ROOT / "media" / name / "scenes.json").read_text())
        # close-up karakter sengaja memotong bingkai; latar bertekstur kertas (butiran acak) mengecoh deteksi tepi
        skip = tuple(f"{i + 1:02d}-{sc['type']}" for i, sc in enumerate(plan)
                     if (sc["type"] == "karakter" and sc.get("layout") == "closeup") or sc.get("env") in ("arsip", "jurnal", "dapur", "sejarah"))
    px = pixel_checks(out, skip)
    rep = json.loads((out / "laporan.json").read_text())
    rep["issues"] += [{"frame": f, "level": "masalah", "msg": m} for f, m in px]
    (out / "laporan.json").write_text(json.dumps(rep, ensure_ascii=False, indent=1))
    for f, m in px:
        print(f"  [masalah] {f}: {m}")
    bad = [x for x in rep["issues"] if x["level"] != "catatan"]
    print(f"QA selesai: {len(bad)} masalah. Gambar dan laporan: {out.relative_to(ROOT)}")
    return rep


def preview_scenes(name, only=None):
    """Gambar diam dari tengah tiap adegan di media/<name>/scenes.json (bukan video)."""
    d = ROOT / "media" / name
    if not (d / "scenes.json").exists():
        raise SystemExit("Belum ada scenes.json: jalankan dulu dengan --style adriy --until scenes")
    pub = REMOTION / "public" / "media" / name
    pub.mkdir(parents=True, exist_ok=True)
    if not (d / "style.json").exists():  # style yang dipilih saat --until scenes tetap dipakai
        (d / "style.json").write_text(json.dumps(load_styles()["adriy"], ensure_ascii=False, indent=1), encoding="utf-8")
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
    ap.add_argument("--judul-boleh-sama", action="store_true", help="tetap buat walau judulnya mirip judul yang sudah pernah dibuat (pipeline/judul-terpakai.json)")
    ap.add_argument("--qa", action="store_true", help="periksa tabrakan, teks terpotong, subtitle, blur, sisa adegan, latar (gambar diam). Dengan --name: video itu; tanpa: contoh semua adegan")
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
    if args.qa:
        return qa_scenes(args.name)
    if not args.input or not args.name:
        ap.error("butuh input dan --name (atau pakai --check)")

    ctx = {"input": args.input, "name": args.name, "title": args.title or args.name, "language": args.language, "force": False,
           "visuals": args.visuals, "style": args.style,
           "renderers": ["remotion", "hyperframes"] if args.renderer == "both" else [args.renderer]}
    ctx["dir"] = ROOT / "media" / args.name
    ctx["dir"].mkdir(parents=True, exist_ok=True)
    # judul dari naskah/meta yang sudah ada, supaya --from/--only (tanpa langkah fetch) memakai judul yang sama
    if not args.title:
        for f in ("script.json", "meta.json"):
            try:
                ctx["title"] = json.loads((ctx["dir"] / f).read_text(encoding="utf-8"))["title"] or ctx["title"]
                break
            except (OSError, ValueError, KeyError):
                pass
    if ctx["title"] == args.name and str(args.input).endswith(".json") and Path(args.input).exists():
        try:
            ctx["title"] = json.loads(Path(args.input).read_text(encoding="utf-8")).get("title") or ctx["title"]
        except ValueError:
            pass
    # judul yang sudah pernah dibuat tidak dibuat lagi (kecuali video itu sendiri dibuat ulang)
    sama = judul_mirip(ctx["title"]) if ctx["title"] != args.name else None
    if sama and sama.get("sumber") != f"media/{args.name}" and not args.judul_boleh_sama:
        raise SystemExit(f'Judul "{ctx["title"]}" sudah pernah dibuat: "{sama["judul"]}" ({sama.get("sumber", "")}).\n'
                         "Pilih topik lain, atau pakai --judul-boleh-sama bila memang sengaja membuat ulang.")
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
    if "finish" in steps and "finish" not in args.skip:
        catat_judul(ctx["title"], args.name)
    print(f"Selesai dalam {time.time() - t0:.0f} dtk. Hasil: {ctx['dir'].relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
