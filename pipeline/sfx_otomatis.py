"""Sound effect otomatis untuk naskah Adriy.project.

Memilih paling banyak satu sound effect per kalimat dari pustaka yang ada di video/public:
  mixkit/sfx (pilihan), mixkit/sfx-kategori/<kategori>, adri/sfx (OpenGameArt, SoundBible), sfx (proyek)
Urutan prioritas per kalimat: kata di kalimat (sfx-aturan.json "kata") > jenis adegan ("adegan") > babak
("babak": pembuka, kesimpulan) > pergantian adegan ("transisi"). Jarak antar sound effect minimal "jarak_min"
detik dan jumlahnya kira-kira "kerapatan" per detik video, supaya tidak ramai. File yang sama tidak dipakai dua
kali dalam satu video; pilihan deterministik (judul yang sama = pilihan yang sama).
"""
import hashlib
import json
import os
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PUBLIC = ROOT / "video" / "public"
ATURAN = ROOT / "pipeline" / "sfx-aturan.json"
INDEKS = PUBLIC / ".sfx-indeks.json"  # dibuat ulang bila pustaka berubah; tidak masuk git
SUMBER = [("mixkit/sfx", "pilihan"), ("mixkit/sfx-kategori", None), ("adri/sfx/opengameart", "oga"), ("adri/sfx/soundbible", "soundbible"), ("sfx", "proyek")]
AUDIO = re.compile(r"\.(mp3|wav|ogg|flac|m4a)$", re.I)


def _durasi(p):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)], capture_output=True, text=True)
    try:
        return round(float(r.stdout.strip()), 2)
    except ValueError:
        return None


def indeks():
    """Daftar sound effect: file (relatif ke video/), kategori, teks untuk dicocokkan, durasi. Disimpan di INDEKS."""
    files = []
    for rel, cat in SUMBER:
        base = PUBLIC / rel
        if not base.exists():
            continue
        for r, _, fs in os.walk(base):
            for f in fs:
                if AUDIO.search(f):
                    p = Path(r) / f
                    k = cat or Path(r).name
                    if cat == "oga":
                        k = "oga " + Path(r).relative_to(base).parts[0]
                    files.append((p, k))
    tanda = f"{len(files)}:{sum(int(p.stat().st_mtime) for p, _ in files)}"
    try:
        lama = json.loads(INDEKS.read_text())
        if lama.get("tanda") == tanda:
            return lama["sfx"]
    except (OSError, ValueError):
        lama = {"sfx": []}
    dur_lama = {e["file"]: e["dur"] for e in lama.get("sfx", [])}
    rels = [str(p.relative_to(ROOT / "video")) for p, _ in files]
    todo = [p for p, r in zip(files, rels) if r not in dur_lama]
    with ThreadPoolExecutor(8) as ex:
        baru = dict(zip([str(p.relative_to(ROOT / "video")) for p, _ in todo], ex.map(_durasi, [p for p, _ in todo])))
    out = []
    for (p, k), r in zip(files, rels):
        d = dur_lama.get(r, baru.get(r))
        if not d:
            continue
        nama = re.sub(r"^mixkit-", "", p.stem.lower())
        teks = f"{k} {re.sub(r'[-_]+', ' ', nama)}"
        out.append({"file": r, "kategori": k, "teks": teks, "dur": d})
    INDEKS.write_text(json.dumps({"tanda": tanda, "sfx": out}, ensure_ascii=False))
    return out


def _pilih(pustaka, cari, maks, kunci, dipakai, terlarang, boleh_terlarang=False, utamakan=None):
    pat = re.compile(rf"(?<![a-z])(?:{cari})", re.I)  # di awal kata: "cat" tidak cocok dengan "notification"
    kand = [e for e in pustaka if pat.search(e["teks"]) and e["file"] not in dipakai and 0.15 <= e["dur"] <= max(6, maks * 4)
            and (boleh_terlarang or not terlarang.search(e["teks"]))]
    if not kand:
        return None
    # utamakan yang pendek (mendekati maks) dan pustaka pilihan; lalu pilih deterministik dari 8 teratas
    utama = re.compile(utamakan, re.I) if utamakan else None
    if utama:  # kategori yang diutamakan habis: lebih baik tanpa bunyi daripada bunyi yang salah
        kand = [e for e in kand if utama.search(e["teks"])]
        if not kand:
            return None
    kand.sort(key=lambda e: (bool(utama) and not utama.search(e["teks"]), e["kategori"] != "pilihan", not pat.search(e["kategori"]), abs(e["dur"] - maks) if e["dur"] > maks else 0, e["file"]))
    top = kand[:8]
    h = int(hashlib.sha1(kunci.encode()).hexdigest(), 16)
    return top[h % len(top)]


def rencana(lines, scenes, title, durasi=None):
    """Kembalikan {index kalimat: {"file", "role", "at", "max", "alasan"}} untuk kalimat yang belum punya sfx."""
    aturan = json.loads(ATURAN.read_text(encoding="utf-8"))
    pustaka = indeks()
    terlarang = re.compile(aturan["terlarang"], re.I)
    durasi = durasi or (lines[-1]["end"] if lines else 0)
    batas = max(2, round(durasi * aturan["kerapatan"]))
    # kategori sound effect tema konten (pipeline/tema-konten.json, kolom "sfx") untuk kalimat pembuka
    tema_sfx = None
    try:
        for t in json.loads((ROOT / "pipeline" / "tema-konten.json").read_text(encoding="utf-8"))["tema"]:
            if title and re.search(t["kata"], title, re.I):
                tema_sfx = (t["nama"], "^(" + "|".join(re.escape(c) for c in t.get("sfx", [])) + ")\\b") if t.get("sfx") else None
                break
    except (OSError, ValueError, KeyError):
        pass
    adegan_di = {}
    for sc in scenes or []:
        adegan_di.setdefault(sc.get("line"), sc)
    calon = []
    for i, ln in enumerate(lines):
        if ln.get("sfx") is not None:  # diisi naskah (atau false = dimatikan)
            continue
        teks = ln["text"].lower()
        sc = adegan_di.get(ln["index"], {})
        pilihan = []
        for r in aturan["kata"]:
            m = re.search(rf"(?<!\w)(?:{r['kata']})(?!\w)", teks)
            if m:
                pilihan.append((3, r["cari"], r["maks"], f"kata '{m.group(0)}'", r.get("boleh_terlarang", False), r.get("utamakan")))
                break
        a = aturan["adegan"].get(sc.get("type"))
        if a:
            pilihan.append((2, a["cari"], a["maks"], f"adegan {sc.get('type')}", False, None))
        if i == 0 and tema_sfx:
            pilihan.append((2, tema_sfx[1], 2, f"tema {tema_sfx[0]}", False, None))
        b = aturan["babak"].get(sc.get("phase"))
        if b and (sc.get("phase") != "pembuka" or i == 0):
            pilihan.append((2, b["cari"], b["maks"], f"babak {sc.get('phase')}", False, None))
        if i > 0 and sc:
            t = aturan["transisi"]
            pilihan.append((1, t["cari"], t["maks"], "pergantian adegan", False, None))
        if pilihan:
            calon.append((ln, sorted(pilihan, key=lambda x: -x[0])))
    # pilih yang terpenting dulu, jaga jarak antar bunyi dan jumlah total
    calon.sort(key=lambda c: (-c[1][0][0], c[0]["start"]))
    hasil, waktu, dipakai, per_aturan = {}, [], set(), {}
    for ln, pilihan in calon:
        if len(hasil) >= batas:
            break
        if any(abs(ln["start"] - t) < aturan["jarak_min"] for t in waktu):
            continue
        for skor, cari, maks, alasan, boleh, utamakan in pilihan:
            if per_aturan.get(cari, 0) >= aturan.get("maks_per_aturan", 2):
                continue
            e = _pilih(pustaka, cari, maks, f"{title}|{ln['index']}|{cari}", dipakai, terlarang, boleh, utamakan)
            if e:
                hasil[ln["index"]] = {"file": e["file"], "role": "accent", "at": "start", "max": maks, "alasan": alasan}
                dipakai.add(e["file"])
                per_aturan[cari] = per_aturan.get(cari, 0) + 1
                waktu.append(ln["start"])
                break
    return hasil
