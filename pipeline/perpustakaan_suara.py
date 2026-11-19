"""Perpustakaan suara: kalimat yang sudah pernah dibuat VoxCPM, disimpan per model suara, dipakai ulang.

Lokasi: video/public/adri/suara/<suara>/<id>.wav + katalog.json (repo aset PRIVAT adriy-karakter-aset,
folder suara/; tidak masuk repo Aidriy yang publik). id = 12 huruf sha1("<suara>|<teks>"), sama dengan nama file di
media/<video>/vo/, jadi kalimat yang persis sama (huruf, tanda baca) langsung dipakai tanpa dibuat ulang.

Pakai dari baris perintah:
  python3 pipeline/perpustakaan_suara.py tambah "Kalimat..." --suara adriy ongky [--kategori hook] [--catatan "nada penasaran"]
  python3 pipeline/perpustakaan_suara.py daftar [--suara adriy]
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIB = ROOT / "video" / "public" / "adri" / "suara"
KATALOG = LIB / "katalog.json"
TTS = ROOT / "video" / "scripts" / "tts_batch.py"
PY = os.environ.get("PIPELINE_PYTHON", "/root/voxcpm-venv/bin/python" if Path("/root/voxcpm-venv/bin/python").exists() else sys.executable)


def kid(suara, teks):
    return hashlib.sha1(f"{suara}|{teks}".encode()).hexdigest()[:12]


def _baca():
    try:
        return json.loads(KATALOG.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {"note": "Perpustakaan suara Adriy.Project: kalimat siap pakai per model suara (lihat pipeline/perpustakaan_suara.py).", "kalimat": []}


def _tulis(k):
    LIB.mkdir(parents=True, exist_ok=True)
    KATALOG.write_text(json.dumps(k, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


def _durasi(p):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)], capture_output=True, text=True)
    try:
        return round(float(r.stdout.strip()), 2)
    except ValueError:
        return None


def ambil(suara, teks, tujuan):
    """Salin kalimat dari perpustakaan ke `tujuan` (file .wav). True bila ada."""
    src = LIB / suara / f"{kid(suara, teks)}.wav"
    if src.exists():
        Path(tujuan).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, tujuan)
        return True
    return False


def simpan(suara, teks, wav, kategori="", catatan="", sumber=""):
    """Masukkan file suara yang sudah jadi ke perpustakaan (bila belum ada)."""
    i = kid(suara, teks)
    dst = LIB / suara / f"{i}.wav"
    if not LIB.parent.exists():  # repo aset tidak ada di mesin ini: lewati diam-diam
        return False
    dst.parent.mkdir(parents=True, exist_ok=True)
    if not dst.exists():
        shutil.copy2(wav, dst)
    k = _baca()
    if not any(e["id"] == i and e["suara"] == suara for e in k["kalimat"]):
        k["kalimat"].append({"id": i, "suara": suara, "teks": teks, "file": f"suara/{suara}/{i}.wav", "durasi": _durasi(dst),
                             "kategori": kategori, "catatan": catatan, "sumber": sumber, "dibuat": time.strftime("%Y-%m-%d")})
        _tulis(k)
    return True


def buat(kalimat, suara_list, kategori="", catatan=""):
    """Buat suara untuk kalimat x suara yang belum ada di perpustakaan, lalu simpan."""
    todo = [{"id": kid(s, t), "voice": s, "text": t} for t in kalimat for s in suara_list if not (LIB / s / f"{kid(s, t)}.wav").exists()]
    if todo:
        tmp = LIB / ".buat"
        tmp.mkdir(parents=True, exist_ok=True)
        job = tmp / "job.json"
        job.write_text(json.dumps(todo, ensure_ascii=False))
        t0 = time.time()
        subprocess.run([PY, str(TTS), str(job), str(tmp)], check=True)
        for j in todo:
            simpan(j["voice"], j["text"], tmp / f"{j['id']}.wav", kategori, catatan, "perpustakaan_suara.py")
        shutil.rmtree(tmp)
        print(f"{len(todo)} suara dibuat dalam {time.time() - t0:.0f} dtk")
    for t in kalimat:
        for s in suara_list:
            print(f"  [{s}] {LIB / s / (kid(s, t) + '.wav')}  {t[:60]}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    a = sub.add_parser("tambah")
    a.add_argument("kalimat", nargs="+")
    a.add_argument("--suara", nargs="+", default=["adriy"])
    a.add_argument("--kategori", default="")
    a.add_argument("--catatan", default="")
    b = sub.add_parser("daftar")
    b.add_argument("--suara")
    args = ap.parse_args()
    if args.cmd == "tambah":
        buat(args.kalimat, args.suara, args.kategori, args.catatan)
    else:
        for e in _baca()["kalimat"]:
            if not args.suara or e["suara"] == args.suara:
                print(f"{e['suara']:7s} {e.get('durasi') or 0:5.1f}s {e.get('kategori', ''):10s} {e['teks'][:70]}")


if __name__ == "__main__":
    main()
