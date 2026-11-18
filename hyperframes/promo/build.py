"""Susun index.html video promo NotesByAdriy (versi HyperFrames dari komposisi Remotion "PromoSuara").

  python3 build.py

Durasi tiap scene mengikuti panjang voiceover di assets/vo/, dan setiap sound effect ditempatkan
tepat saat elemen di layar "mendarat" (90% geraknya), dihitung dari easing GSAP yang sama dengan
animasinya. Volume sound effect memakai ukuran dari ../../video/src/promo/sfx-meta.json
(dibuat oleh video/scripts/sfx_analyze.py), dengan aturan yang sama seperti komponen Sfx di Remotion.
"""
import json
import math
import subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
META = json.loads((HERE / "../../video/src/promo/sfx-meta.json").read_text())

FPS = 30
TRANSITION = 0.5  # detik, sama dengan 15 frame di versi Remotion
LEAD, HOOK_LEAD, TAIL, OUTRO_TAIL = 18 / FPS, 30 / FPS, 14 / FPS, 2.0
VO_START = 0.6  # voiceover mulai 0,6 detik setelah scene mulai
TARGET_DB = {"accent": -24, "underVoice": -32}
PEAK_LIMIT_DB = {"accent": -12, "underVoice": -26}

# Kunci di sfx-meta.json -> file di assets/
SFX_FILES = {
    "sfx/kertas-remas.wav": "assets/sfx/kertas-remas.wav",
    "mixkit/sfx/transisi-sweep-kecil.mp3": "assets/mixkit/transisi-sweep-kecil.mp3",
    "mixkit/sfx/pop-kering-notifikasi.mp3": "assets/mixkit/pop-kering-notifikasi.mp3",
    "mixkit/sfx/ketik-keyboard-laptop.mp3": "assets/mixkit/ketik-keyboard-laptop.mp3",
    "mixkit/sfx/jepret-kamera.mp3": "assets/mixkit/jepret-kamera.mp3",
    "mixkit/sfx/ai-magic-sparkle-whoosh.mp3": "assets/mixkit/ai-magic-sparkle-whoosh.mp3",
    "mixkit/sfx/sukses-konfirmasi.mp3": "assets/mixkit/sukses-konfirmasi.mp3",
    "mixkit/sfx/sukses-jawaban-benar.mp3": "assets/mixkit/sukses-jawaban-benar.mp3",
}

ICONS = {
    "feather": "M20.2,3.8C16.1,2.6 10.4,4.2 7.3,8.6C5.4,11.3 4.8,14.4 5.2,17.3L3.3,19.3C2.9,19.7 2.9,20.3 3.3,20.7C3.7,21.1 4.3,21.1 4.7,20.7L6.7,18.7C9.6,19.1 12.7,18.5 15.4,16.6C19.8,13.5 21.4,7.9 20.2,3.8ZM14.3,15C12.3,16.4 9.9,17 7.7,16.8L12.7,11.7L11.3,10.3L6.9,14.8C6.9,12.9 7.4,11.1 8.6,9.4C11,6 14.9,4.7 18.3,5.2C18.8,8.7 17.6,12.7 14.3,15Z",
    "note": "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z",
    "scan": "M3 9V5c0-1.1.9-2 2-2h4v2H5v4H3zm2 6H3v4c0 1.1.9 2 2 2h4v-2H5v-4zm14 4h-4v2h4c1.1 0 2-.9 2-2v-4h-2v4zm0-16h-4v2h4v4h2V5c0-1.1-.9-2-2-2zM7 11h10v2H7z",
    "ai": "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
    "sync": "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM10 17l-3.5-3.5 1.41-1.41L10 14.17 15.18 9l1.41 1.41L10 17z",
}

FEATURES = [
    ("catatan", "FITUR 01", "Catatan pintar", "Markdown, checklist, dan gambar di antara teks.", "note", "#2563EB", "#E6EFFE"),
    ("scan", "FITUR 02", "Pindai teks", "Foto dokumen, teksnya langsung jadi catatan (OCR).", "scan", "#16A35A", "#E3F7EC"),
    ("ai", "FITUR 03", "Asisten AI", "Rapikan format dan tanya apa saja dengan API key-mu sendiri.", "ai", "#7C4DFF", "#F0E9FE"),
    ("sinkron", "FITUR 04", "Sinkron antar HP", "Catatan, ide, journal, dan berkas dicadangkan dengan aman.", "sync", "#F07A16", "#FFEEDF"),
]
# Warna teks label per fitur kalau aksennya terlalu pucat untuk teks di latar terang (kontras < 3:1).
STEP_TEXT = {"sinkron": "#C2620E"}


# Easing GSAP yang dipakai di animasi, untuk menghitung kapan elemen mendarat.
def back_out(s):
    return lambda t: 1 + (s + 1) * (t - 1) ** 3 + s * (t - 1) ** 2


def power4_out(t):
    return 1 - (1 - t) ** 4


def land(start, dur, ease):
    """Detik saat animasi mencapai 90% geraknya."""
    for i in range(1, 1001):
        t = i / 1000
        if ease(t) >= 0.9:
            return start + t * dur
    return start + dur


def audio_duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return float(out)


sfx_tags = []


def sfx(label, key, at, role, align="onset", max_len=None, trim_start=0.0, track=2):
    """Sound effect di detik `at` (waktu film). align="peak": bagian terkeras jatuh tepat di `at`."""
    m = META[key]
    vol = min(1, 10 ** ((TARGET_DB[role] - m["loudness"]) / 20), 10 ** ((PEAK_LIMIT_DB[role] - m["peakDb"]) / 20))
    start = at - (m["peak"] if align == "peak" else 0)
    media_start = m["onset"] + trim_start
    if start < 0:
        media_start -= start
        start = 0
    # Sisa bunyi setelah titik potong, supaya slot tidak lebih panjang dari bunyinya.
    remaining = m["onset"] + m["duration"] - media_start
    length = min(max_len or remaining, remaining)
    sfx_tags.append(
        f'<audio id="sfx-{len(sfx_tags) + 1}" class="clip" src="{SFX_FILES[key]}" data-start="{start:.3f}" '
        f'data-duration="{length:.3f}" data-media-start="{media_start:.3f}" data-volume="{vol:.3f}" '
        f'data-fade-out="0.15" data-track-index="{track}"></audio><!-- {label} -->'
    )


# ---------- durasi scene ----------
vo = [audio_duration(p) for p in sorted((HERE / "assets/vo").glob("*.wav"))]
assert len(vo) == 7, "butuh 7 voiceover di assets/vo/"
durs = []
for i, s in enumerate(vo):
    lead = HOOK_LEAD if i == 0 else LEAD
    tail = OUTRO_TAIL if i == 6 else TAIL
    durs.append(lead + math.ceil(s * FPS) / FPS + tail)
starts = []
t = 0.0
for i, d in enumerate(durs):
    starts.append(t)
    t += d - TRANSITION
total = starts[-1] + durs[-1]
names = ["hook", "intro"] + [f[0] for f in FEATURES] + ["outro"]
transitions = [None, "fade", "slide-up", "slide-left", "slide-left", "slide-left", "fade"]

# ---------- animasi (GSAP, waktu mutlak) ----------
js = []


def at(scene, offset):
    return round(starts[names.index(scene)] + offset, 3)


def tw(sel, frm, to, t0, dur, ease):
    js.append(f'tl.fromTo("{sel}", {json.dumps(frm)}, {{...{json.dumps(to)}, duration: {dur}, ease: "{ease}"}}, {t0});')


for i, n in enumerate(names):
    tr = transitions[i]
    if tr == "fade":
        tw(f"#{n}", {"opacity": 0}, {"opacity": 1}, at(n, 0), TRANSITION, "none")
    elif tr == "slide-up":
        tw(f"#{n}", {"y": 1920}, {"y": 0}, at(n, 0), TRANSITION, "power3.out")
        js.append(f'tl.to("#{names[i - 1]}", {{y: -1920, duration: {TRANSITION}, ease: "power3.out"}}, {at(n, 0)});')
    elif tr == "slide-left":
        tw(f"#{n}", {"x": 1080}, {"x": 0}, at(n, 0), TRANSITION, "power3.out")
        # Scene yang keluar memakai to(), bukan fromTo(): fromTo kedua pada elemen yang sama akan
        # menetapkan nilai awalnya saat timeline dibuat dan merusak posisi masuk scene itu.
        js.append(f'tl.to("#{names[i - 1]}", {{x: -1080, duration: {TRANSITION}, ease: "power3.out"}}, {at(n, 0)});')

# Hook: tiga label melayang masuk, lalu judul
for j, (cid, dx, dy, r0, r1) in enumerate([("ide", -260, -120, -12, -4), ("journal", 240, -160, 10, 5), ("berkas", 0, 220, -6, 2)]):
    t0 = at("hook", 0.15 * j)
    tw(f"#chip-{cid}", {"x": dx, "y": dy, "rotation": r0, "opacity": 0}, {"x": 0, "y": 0, "rotation": r1, "opacity": 1}, t0, 0.7, "back.out(1.4)")
tw("#hook-title", {"opacity": 0, "y": 50}, {"opacity": 1, "y": 0}, at("hook", 0.6), 0.5, "power4.out")
tw("#hook-sub", {"opacity": 0}, {"opacity": 1}, at("hook", 1.0), 0.5, "none")
# Intro: logo memantul, nama, tagline
tw("#intro-logo", {"scale": 0, "rotation": -25}, {"scale": 1, "rotation": 0}, at("intro", 0), 0.8, "back.out(2)")
tw("#intro-name", {"opacity": 0, "y": 60}, {"opacity": 1, "y": 0}, at("intro", 0.5), 0.6, "power4.out")
tw("#intro-tag", {"opacity": 0, "y": 40}, {"opacity": 1, "y": 0}, at("intro", 0.9), 0.6, "power4.out")
# Fitur
for fid, *_ in FEATURES:
    tw(f"#{fid}-step", {"opacity": 0}, {"opacity": 1}, at(fid, 0), 0.4, "none")
    tw(f"#{fid}-icon", {"scale": 0}, {"scale": 1}, at(fid, 0), 0.6, "back.out(2)")
    tw(f"#{fid}-title", {"opacity": 0, "y": 50}, {"opacity": 1, "y": 0}, at(fid, 0.25), 0.5, "power4.out")
    tw(f"#{fid}-desc", {"opacity": 0, "y": 40}, {"opacity": 1, "y": 0}, at(fid, 0.55), 0.5, "power4.out")
    tw(f"#{fid}-card", {"opacity": 0, "y": 120}, {"opacity": 1, "y": 0}, at(fid, 0.8), 0.5, "power4.out")
    for k, (w, d) in enumerate([(100, 1.1), (88, 1.25), (64, 1.4)]):
        tw(f"#{fid}-line{k}", {"width": "0%"}, {"width": f"{w}%"}, at(fid, d), 0.6, "power4.out")
# Outro
tw("#outro-logo", {"scale": 0.6, "opacity": 0}, {"scale": 1, "opacity": 1}, at("outro", 0), 0.6, "back.out(1.6)")
tw("#outro-slogan", {"opacity": 0, "y": 50}, {"opacity": 1, "y": 0}, at("outro", 0.4), 0.5, "power4.out")
tw("#outro-cta", {"scale": 0}, {"scale": 1}, at("outro", 1.0), 0.5, "back.out(3)")
js.append(f'tl.to("#outro-cta", {{scale: 1.06, duration: 0.5, ease: "sine.inOut", yoyo: true, repeat: 1}}, {at("outro", 2.5)});')
tw("#outro-note", {"opacity": 0}, {"opacity": 1}, at("outro", 1.4), 0.5, "none")

# ---------- audio ----------
vo_tags = []
for i, n in enumerate(names):
    off = HOOK_LEAD if i == 0 else VO_START
    vo_tags.append(
        f'<audio id="vo-{n}" class="clip" src="assets/vo/{sorted((HERE / "assets/vo").glob("*.wav"))[i].name}" '
        f'data-start="{at(n, off):.3f}" data-duration="{vo[i]:.3f}" data-volume="1" data-track-index="1"></audio>'
    )

sfx("Kertas diremas, puncak = label terakhir mendarat", "sfx/kertas-remas.wav", land(at("hook", 0.3), 0.7, back_out(1.4)), "accent", "peak")
sfx("Pop logo", "mixkit/sfx/pop-kering-notifikasi.mp3", land(at("intro", 0), 0.8, back_out(2)), "accent", "peak")
for fid, *_ in FEATURES:
    sfx(f"Sweep transisi ke {fid}", "mixkit/sfx/transisi-sweep-kecil.mp3", at(fid, TRANSITION / 2), "accent", "peak", track=3)
sfx("Ketik saat baris kartu catatan muncul", "mixkit/sfx/ketik-keyboard-laptop.mp3", at("catatan", 1.1), "underVoice", max_len=0.9, trim_start=2)
sfx("Jepret saat ikon scan mendarat", "mixkit/sfx/jepret-kamera.mp3", land(at("scan", 0), 0.6, back_out(2)), "accent", "peak")
sfx("Sparkle saat ikon AI mendarat", "mixkit/sfx/ai-magic-sparkle-whoosh.mp3", land(at("ai", 0), 0.6, back_out(2)), "accent", "peak", max_len=0.7)
sfx("Konfirmasi saat kartu sinkron muncul", "mixkit/sfx/sukses-konfirmasi.mp3", land(at("sinkron", 0.8), 0.5, power4_out), "underVoice", "peak")
sfx("Sukses saat tombol Unduh muncul", "mixkit/sfx/sukses-jawaban-benar.mp3", land(at("outro", 1.0), 0.5, back_out(3)), "underVoice", "peak")


# ---------- HTML ----------
def scene(n, inner, bg):
    i = names.index(n)
    return (f'<section id="{n}" class="clip scene" data-start="{starts[i]:.3f}" data-duration="{durs[i]:.3f}" '
            f'data-track-index="0" style="background:{bg}; z-index:{i + 1}">\n{inner}\n</section>')


def icon(name, size, color):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24"><path d="{ICONS[name]}" fill="{color}"/></svg>'


GRADIENT = "radial-gradient(circle at 50% 35%, #3B82F6 0%, #2563EB 45%, #0B2A6B 100%)"
parts = [
    scene("hook", """  <div class="stack">
    <div class="chips">
      <div id="chip-ide" class="chip" style="color:#FFD25E;border-color:#FFD25E">💡 Ide</div>
      <div id="chip-journal" class="chip" style="color:#FF8A8E;border-color:#FF8A8E">📓 Journal</div>
      <div id="chip-berkas" class="chip" style="color:#7CA7FF;border-color:#7CA7FF">📎 Berkas</div>
    </div>
    <h1 id="hook-title" class="headline" style="color:#E8ECF2;text-align:center">Catatan di mana‑mana?</h1>
    <p id="hook-sub" class="sub" style="color:#9AA4B2;text-align:center">Ide hilang, journal berserakan, berkas tercecer.</p>
  </div>""", "#0E1218"),
    scene("intro", f"""  <div class="stack" style="align-items:center;gap:56px">
    <div id="intro-logo" class="logo" style="width:300px;height:300px;border-radius:84px">{icon("feather", 200, "#2563EB")}</div>
    <h1 id="intro-name" style="color:#fff;font-size:120px;font-weight:800;letter-spacing:-3px">NotesByAdriy</h1>
    <p id="intro-tag" style="color:#DCE8FD;font-size:60px;font-weight:500">Second Brain AI</p>
  </div>""", GRADIENT),
]
for fid, step, title, desc, ic, accent, soft in FEATURES:
    parts.append(scene(fid, f"""  <div class="stack feature">
    <div id="{fid}-step" class="step" style="color:{STEP_TEXT.get(fid, accent)}">{step}</div>
    <div id="{fid}-icon" class="icon-tile" style="background:{soft}">{icon(ic, 140, accent)}</div>
    <h1 id="{fid}-title" class="headline" style="color:#0F141B">{title}</h1>
    <p id="{fid}-desc" class="sub" style="color:#5B6472">{desc}</p>
    <div id="{fid}-card" class="card">
      <div class="bar" style="background:{accent}"></div>
      <div id="{fid}-line0" class="line"></div>
      <div id="{fid}-line1" class="line"></div>
      <div id="{fid}-line2" class="line"></div>
    </div>
  </div>""", "#F6F8FB"))
parts.append(scene("outro", f"""  <div class="stack" style="align-items:center;gap:60px">
    <div id="outro-logo" style="display:flex;align-items:center;gap:28px">
      <div class="logo" style="width:150px;height:150px;border-radius:44px">{icon("feather", 100, "#2563EB")}</div>
      <div style="color:#fff;font-size:84px;font-weight:800;letter-spacing:-2px">NotesByAdriy</div>
    </div>
    <h1 id="outro-slogan" style="color:#fff;font-size:80px;font-weight:800;line-height:1.1;text-align:center;letter-spacing:-2px">Your Knowledge.<br>One Intelligent Space.</h1>
    <div id="outro-cta" class="cta">Unduh Gratis</div>
    <p id="outro-note" style="color:#DCE8FD;font-size:46px;font-weight:500;text-align:center">Offline-first · Data tetap di HP kamu</p>
  </div>""", "radial-gradient(circle at 50% 40%, #3B82F6 0%, #2563EB 50%, #0B2A6B 100%)"))

html = f"""<!doctype html>
<!-- Dibuat oleh build.py. Jangan edit langsung: ubah build.py lalu jalankan ulang. -->
<html lang="id" data-resolution="portrait">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1080, height=1920" />
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.15.0/dist/gsap.min.js"></script>
    <style>
      @font-face {{
        font-family: "Plus Jakarta Sans";
        src: url("assets/fonts/PlusJakartaSans-latin.woff2") format("woff2");
        font-weight: 200 800;
      }}
      * {{ margin: 0; padding: 0; box-sizing: border-box; }}
      html, body {{ width: 1080px; height: 1920px; overflow: hidden; background: #0E1218; }}
      #root {{ position: relative; width: 1080px; height: 1920px; overflow: hidden; font-family: "Plus Jakarta Sans", sans-serif; }}
      .scene {{ position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; }}
      .stack {{ width: 100%; padding: 0 90px; display: flex; flex-direction: column; align-items: center; gap: 64px; }}
      .stack.feature {{ align-items: stretch; gap: 44px; }}
      .chips {{ display: flex; flex-wrap: wrap; justify-content: center; gap: 28px; height: 260px; align-content: center; }}
      .chip {{ padding: 24px 44px; border-radius: 999px; background: #1C222C; border: 3px solid; font-size: 48px; font-weight: 700; }}
      .headline {{ font-size: 104px; font-weight: 800; line-height: 1.08; letter-spacing: -2px; }}
      .sub {{ font-size: 54px; font-weight: 500; line-height: 1.35; }}
      .logo {{ background: #fff; display: flex; align-items: center; justify-content: center; box-shadow: 0 40px 80px rgba(11,42,107,.35); }}
      .step {{ font-size: 44px; font-weight: 800; letter-spacing: 6px; }}
      .icon-tile {{ width: 240px; height: 240px; border-radius: 64px; display: flex; align-items: center; justify-content: center; }}
      .card {{ margin-top: 40px; padding: 56px; border-radius: 56px; background: #fff; box-shadow: 0 30px 70px rgba(15,20,27,.10); display: flex; flex-direction: column; gap: 30px; }}
      .card .bar {{ height: 36px; width: 55%; border-radius: 18px; }}
      .card .line {{ height: 26px; width: 0; border-radius: 13px; background: #E6EAF1; }}
      .cta {{ margin-top: 30px; padding: 40px 80px; border-radius: 999px; background: #fff; color: #2563EB; font-size: 56px; font-weight: 800; box-shadow: 0 30px 60px rgba(11,42,107,.35); }}
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="{total:.3f}" data-width="1080" data-height="1920">
{chr(10).join(parts)}
{chr(10).join(vo_tags)}
{chr(10).join(sfx_tags)}
    </div>
    <script>
      const tl = gsap.timeline({{ paused: true }});
      {chr(10) + "      ".join(js)}
      window.__timelines = window.__timelines || {{}};
      window.__timelines["main"] = tl;
      tl.seek(0);
    </script>
  </body>
</html>
"""
(HERE / "index.html").write_text(html, encoding="utf-8")
print(f"index.html: {total:.2f} detik, {len(names)} scene, {len(vo_tags)} voiceover, {len(sfx_tags)} sound effect")
for n, s, d in zip(names, starts, durs):
    print(f"  {n:8s} mulai {s:6.2f}  durasi {d:5.2f}")
