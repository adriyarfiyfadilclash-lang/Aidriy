"""Pipeline video: yt-dlp/VoxCPM -> ffmpeg -> Whisper -> subtitle -> sound effect -> Remotion + HyperFrames -> ffmpeg.

Sumber bisa berupa URL (yt-dlp), file video/audio, atau naskah .json (suara dibuat VoxCPM):
  {"title": "...", "lines": [{"voice": "adriy", "text": "...", "sfx": "public/sfx/kertas-remas.wav"}, ...]}
  "sfx" boleh berupa path (relatif ke video/) atau {"file": ..., "at": "start"|"end", "role": "accent"|"underVoice"}.

Contoh:
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --title "Kaca Mobil Berkabut"
  python3 pipeline/pipeline.py "https://www.youtube.com/watch?v=..." --name klip
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --from pages     # ulang mulai langkah pages
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --only remotion  # satu langkah saja
  python3 pipeline/pipeline.py naskah.json --name iklan --until sfx            # buat suara dulu, belum render

Semua hasil disimpan di media/<name>/ (tidak di-commit):
  source.*          file asli (dari yt-dlp, salinan file, atau gabungan voiceover dari naskah)
  vo/, lines.json   (naskah) suara per kalimat dan waktu mulai/selesai tiap kalimat
  voice.wav         suara 48 kHz;  audio-16k.wav  16 kHz mono untuk Whisper
  audio.wav         suara + sound effect, dipakai kedua renderer;  sfx-cues.json  letak dan volume tiap bunyi
  meta.json         durasi, judul, ada/tidaknya video
  transcript.json   kata per kata, format Caption Remotion (text, startMs, endMs, ...). Untuk naskah,
                    waktu dari Whisper tapi ejaan dari naskah (transcript-whisper.json = hasil asli Whisper)
  pages.json        kata dikelompokkan jadi halaman subtitle; dipakai Remotion DAN HyperFrames
  remotion.mp4, hyperframes.mp4   hasil render
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

STEPS = ["fetch", "prepare", "transcribe", "pages", "sfx", "remotion", "hyperframes", "finish"]
VOICE_LEAD, LINE_GAP = 0.6, 0.5  # detik: hening sebelum kalimat pertama, dan di antara kalimat
SR = 48000


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
    for ln in lines:
        if ln.get("sfx"):
            c = ln["sfx"] if isinstance(ln["sfx"], dict) else {"file": ln["sfx"]}
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


def step_remotion(ctx):
    d, name = ctx["dir"], ctx["name"]
    out = d / "remotion.mp4"
    inputs = [d / "pages.json", d / "meta.json", d / "audio.wav"]
    if not ctx["force"] and fresh(out, *inputs):
        return log("remotion", "sudah ada")
    pub = REMOTION / "public" / "media" / name
    pub.mkdir(parents=True, exist_ok=True)
    for f in ["meta.json", "pages.json", "audio.wav", "video.mp4"]:
        if (d / f).exists():
            shutil.copy2(d / f, pub / f)
    log("remotion", "render komposisi Subtitle...")
    t = time.time()
    run(["npx", "remotion", "render", "Subtitle", str(out), f"--props={json.dumps({'media': name})}"], cwd=REMOTION)
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
.page { position: absolute; left: 80px; right: 80px; bottom: 520px; text-align: center; font-size: 92px; font-weight: 800;
  line-height: 1.15; letter-spacing: -1px; color: #fff; text-shadow: 0 6px 24px rgba(0,0,0,.45); }
.w { white-space: pre; }
"""
FEATHER = "M20.2,3.8C16.1,2.6 10.4,4.2 7.3,8.6C5.4,11.3 4.8,14.4 5.2,17.3L3.3,19.3C2.9,19.7 2.9,20.3 3.3,20.7C3.7,21.1 4.3,21.1 4.7,20.7L6.7,18.7C9.6,19.1 12.7,18.5 15.4,16.6C19.8,13.5 21.4,7.9 20.2,3.8ZM14.3,15C12.3,16.4 9.9,17 7.7,16.8L12.7,11.7L11.3,10.3L6.9,14.8C6.9,12.9 7.4,11.1 8.6,9.4C11,6 14.9,4.7 18.3,5.2C18.8,8.7 17.6,12.7 14.3,15Z"


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def step_hyperframes(ctx):
    d = ctx["dir"]
    out = d / "hyperframes.mp4"
    inputs = [d / "pages.json", d / "meta.json", d / "audio.wav"]
    if not ctx["force"] and fresh(out, *inputs):
        return log("hyperframes", "sudah ada")
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
    for kind in ["remotion", "hyperframes"]:
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
    src = d / "final-remotion.mp4" if (d / "final-remotion.mp4").exists() else d / "final-hyperframes.mp4"
    if src.exists():
        run(["ffmpeg", "-y", "-v", "error", "-ss", f"{at:.2f}", "-i", str(src), "-frames:v", "1", "-q:v", "3", str(d / "thumbnail.jpg")])
        log("finish", "thumbnail.jpg")


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

    # 3. Sambungan antar-alat
    code, out = cmd([sys.executable, "-c", f"import sys; sys.path.insert(0, {str(REMOTION / 'scripts')!r}); from sfx_analyze import analyze; print('ok')"])
    item("Sambungan", "pipeline -> sfx_analyze.py (ukur sound effect)", code == 0, "bisa diimpor" if code == 0 else out[-120:], "pip install numpy")
    item("Sambungan", "pipeline -> tts_batch.py (VoxCPM)", (REMOTION / "scripts/tts_batch.py").exists(), "video/scripts/tts_batch.py", "")
    code, out = cmd(["npx", "remotion", "compositions"], cwd=REMOTION, timeout=300)
    comps = [c for c in ["Subtitle", "PromoSuara", "Promo"] if re.search(rf"^{c}\s", out, re.M)]
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


FUNCS = dict(zip(STEPS, [step_fetch, step_prepare, step_transcribe, step_pages, step_sfx, step_remotion, step_hyperframes, step_finish]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", nargs="?", help="URL video (yt-dlp), file video/audio, atau naskah .json")
    ap.add_argument("--name", help="nama folder di media/, misalnya kaca-mobil")
    ap.add_argument("--check", action="store_true", help="periksa semua alat dan sambungan, tanpa membuat apa pun")
    ap.add_argument("--title", default="", help="judul di layar kalau sumbernya audio saja")
    ap.add_argument("--language", default="indonesian", help='bahasa untuk Whisper ("" = deteksi otomatis)')
    ap.add_argument("--from", dest="start", choices=STEPS, help="ulang mulai langkah ini (langkah setelahnya ikut diulang)")
    ap.add_argument("--only", choices=STEPS, help="jalankan satu langkah saja")
    ap.add_argument("--skip", action="append", default=[], choices=STEPS, help="lewati langkah ini (bisa berulang)")
    ap.add_argument("--until", choices=STEPS, help="berhenti setelah langkah ini (misalnya --until sfx untuk dengar suara dulu)")
    args = ap.parse_args()
    if args.check:
        raise SystemExit(1 if check() else 0)
    if not args.input or not args.name:
        ap.error("butuh input dan --name (atau pakai --check)")

    ctx = {"input": args.input, "name": args.name, "title": args.title or args.name, "language": args.language, "force": False}
    ctx["dir"] = ROOT / "media" / args.name
    ctx["dir"].mkdir(parents=True, exist_ok=True)
    steps = [args.only] if args.only else STEPS[: STEPS.index(args.until) + 1] if args.until else STEPS
    t0 = time.time()
    for s in steps:
        if s in args.skip:
            log(s, "dilewati")
            continue
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
