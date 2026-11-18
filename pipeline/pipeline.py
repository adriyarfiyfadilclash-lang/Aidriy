"""Pipeline video: yt-dlp -> ffmpeg -> Whisper -> subtitle -> Remotion + HyperFrames -> ffmpeg.

Contoh:
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --title "Kaca Mobil Berkabut"
  python3 pipeline/pipeline.py "https://www.youtube.com/watch?v=..." --name klip
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --from pages     # ulang mulai langkah pages
  python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --only remotion  # satu langkah saja

Semua hasil disimpan di media/<name>/ (tidak di-commit):
  source.*          file asli (dari yt-dlp, atau salinan file yang diberikan)
  audio.wav         audio 48 kHz untuk video;  audio-16k.wav  audio 16 kHz mono untuk Whisper
  meta.json         durasi, judul, ada/tidaknya video
  transcript.json   kata per kata dari Whisper, format Caption Remotion (text, startMs, endMs, ...)
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

STEPS = ["fetch", "prepare", "transcribe", "pages", "remotion", "hyperframes", "finish"]


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
def step_fetch(ctx):
    src = ctx["input"]
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
    a48, a16, meta = d / "audio.wav", d / "audio-16k.wav", d / "meta.json"
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


FUNCS = dict(zip(STEPS, [step_fetch, step_prepare, step_transcribe, step_pages, step_remotion, step_hyperframes, step_finish]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input", help="URL video (yt-dlp) atau file video/audio")
    ap.add_argument("--name", required=True, help="nama folder di media/, misalnya kaca-mobil")
    ap.add_argument("--title", default="", help="judul di layar kalau sumbernya audio saja")
    ap.add_argument("--language", default="indonesian", help='bahasa untuk Whisper ("" = deteksi otomatis)')
    ap.add_argument("--from", dest="start", choices=STEPS, help="ulang mulai langkah ini (langkah setelahnya ikut diulang)")
    ap.add_argument("--only", choices=STEPS, help="jalankan satu langkah saja")
    ap.add_argument("--skip", action="append", default=[], choices=STEPS, help="lewati langkah ini (bisa berulang)")
    args = ap.parse_args()

    ctx = {"input": args.input, "name": args.name, "title": args.title or args.name, "language": args.language, "force": False}
    ctx["dir"] = ROOT / "media" / args.name
    ctx["dir"].mkdir(parents=True, exist_ok=True)
    steps = [args.only] if args.only else STEPS
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
