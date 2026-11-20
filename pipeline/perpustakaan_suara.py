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
sys.path.insert(0, str(Path(__file__).resolve().parent))  # pelafalan.py
LIB = ROOT / "video" / "public" / "adri" / "suara"
KATALOG = LIB / "katalog.json"
TTS = ROOT / "video" / "scripts" / "tts_batch.py"
PY = os.environ.get("PIPELINE_PYTHON", "/root/voxcpm-venv/bin/python" if Path("/root/voxcpm-venv/bin/python").exists() else sys.executable)


def kid(suara, teks):
    """id rekaman = dari teks yang DIBACA (setelah kamus pelafalan): kamus berubah -> kalimat itu dibuat ulang."""
    from pelafalan import lafal
    return hashlib.sha1(f"{suara}|{lafal(teks)}".encode()).hexdigest()[:12]


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


# Fungsi kalimat dalam alur cerita (berbeda dari nada/emosi). Urutan = prioritas; kalimat bisa punya 1-2 fungsi.
FUNGSI = [
    ("pengenalan akun", r"^di adriy project\b"),
    ("cta", r"\bfollow\b|sampai (jumpa|ketemu)|terima kasih sudah (menonton|ikut)"),
    ("ajakan", r"komentar|\bbagikan\b|\bsimpan\b|\btag\b|\bshare\b|\blike\b|kirim video|tonton sekali lagi"),
    ("jeda dramatis", r"^(sebentar|tunggu( sebentar| dulu)?|titik|hening|eits, tunggu dulu)\.?$|\.\.\.$|^dan (saat itulah|tiba-tiba)|semuanya menjadi sunyi|tidak ada suara"),
    ("pertanyaan retoris", r"(, kan|, ya|, bukan|, sih)\?$|^bukankah|^(benar|seru|lucu|aneh|simpel|mudah|keren banget|luar biasa|nggak nyangka|masuk akal), (kan|ya)\?|^siapa (yang )?(setuju|sangka)|^masa, sih\?|sampai di sini masih masuk akal"),
    ("memberikan analogi", r"\bbayangkan\b|\bseperti\b|\bmirip\b|analogi|\bibarat|\bmisalnya\b|\bcontoh(nya)?\b|kira-kira seperti"),
    ("membantah asumsi", r"\bpadahal\b|\bmitos\b|\bkeliru\b|salah (kira|paham|memahami)|tidak sepenuhnya|faktanya justru|sebaliknya|kita (selama ini )?mengira|anggapan|bukan (opini|kebetulan|sekadar|karena|berarti|soal)|sains berkata lain|jangan (langsung )?percaya|korelasi|belum tentu|terbantahkan|luruskan"),
    ("mengungkap kejutan", r"\bternyata\b|siapa sangka|mengejutkan|\bkaget|nggak nyangka|di luar (dugaan|nalar)|tidak (ada yang )?(me)?nyangka|lebih gila|\bwah\b|\bwow\b|astaga|ya ampun|kejutan|mustahil|serius\?|sumpah\?|beneran\?|yang benar\?"),
    ("menjelaskan sebab-akibat", r"\bkarena\b|akibat(nya)?|sebab(nya)?|semakin .* semakin|menyebabkan|dipengaruhi|\bfaktor\b|\balasan\b|memicu|efek berantai|reaksi|sehingga|berdampak|dampak"),
    ("menghubungkan dua topik", r"hubungan|berhubungan|terhubung|berkaitan|\bkembali ke\b|dibandingkan|bandingkan|sisi lain|selain itu|bagian berikutnya|poin berikutnya|\blanjut\b|membawa kita|dari sisi|pertanyaan berikutnya|bagaimana dengan sebaliknya"),
    ("menyampaikan kesimpulan", r"^jadi\b|kesimpulan|singkatnya|\bintinya\b|pada akhirnya|^itulah|dengan kata lain|sekarang kamu (sudah )?tahu|kalau dirangkum|penjelasan singkatnya|^dan itulah|begitulah"),
    ("mengawali cerita", r"bermula|berawal|dimulai|^suatu|^dulu\b|pada awalnya|kita mulai|^semuanya dimulai|hari itu|sejak (saat|hari) itu|^ada (satu|sesuatu)|^hampir setiap hari|^rasanya baru kemarin"),
    ("memperkenalkan fakta", r"penelitian|\bdata\b|ilmuwan|para ahli|\bmenurut\b|\bistilah\b|disebut|dikenal|\bangka|persen|\bfakta\b|ditemukan|\bproses|\btahap\b|terjadi (karena|pada)|bahasa (latin|yunani)"),
    ("memancing rasa penasaran", r"\?$|^pernah|penasaran|rahasia|jarang (dibahas|disadari|diketahui|orang tahu)|belum (pernah )?(benar-benar )?dibicarakan|tersembunyi|misteri|janggal|ada yang aneh|tunggu dulu"),
    ("mengubah suasana", r"\bsantai\b|\btenang\b|ngopi|plot twist|ngaku|\bhumor|nggak usah|tarik napas|bercanda|drama\b|lumayan|asyik|nggak sendirian|bukan (ujian|kutukan)"),
]
NADA_FUNGSI = {"humor": "mengubah suasana", "santai": "mengubah suasana", "lucu": "mengubah suasana", "hangat": "mengubah suasana",
               "hening": "jeda dramatis", "pelan": "jeda dramatis", "dramatis": "jeda dramatis", "menegangkan": "memancing rasa penasaran",
               "reflektif": "menyampaikan kesimpulan", "emosional": "menyampaikan kesimpulan", "motivasi": "menyampaikan kesimpulan",
               "inspiratif": "menyampaikan kesimpulan", "penutup": "menyampaikan kesimpulan", "informatif": "memperkenalkan fakta",
               "tegas": "membantah asumsi", "serius": "memperkenalkan fakta", "spontan": "mengungkap kejutan", "terkejut": "mengungkap kejutan",
               "kagum": "mengungkap kejutan", "penasaran": "memancing rasa penasaran", "bingung": "memancing rasa penasaran"}


def fungsi_kalimat(teks, nada=""):
    """1-2 fungsi kalimat dalam alur cerita (lihat FUNGSI). Tanpa pola cocok: dari nada, lalu 'memperkenalkan fakta'."""
    import re
    t = teks.strip().lower()
    hasil = []
    for nama, pola in FUNGSI:
        if re.search(pola, t) and nama not in hasil:
            hasil.append(nama)
            if nama in ("pengenalan akun", "cta", "ajakan"):  # fungsi wajib berdiri sendiri
                return hasil
        if len(hasil) == 2:
            break
    if not hasil:
        n = re.sub(r"^nada\s+", "", (nada or "").lower()).split()[0] if nada else ""
        hasil = [NADA_FUNGSI.get(n, "memperkenalkan fakta")]
    return hasil


def tandai_semua():
    """Isi ulang kolom 'fungsi' semua kalimat di katalog."""
    k = _baca()
    for e in k["kalimat"]:
        e["fungsi"] = fungsi_kalimat(e["teks"], e.get("catatan", ""))
    _tulis(k)
    return k


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
        from pelafalan import lafal
        k["kalimat"].append({"id": i, "suara": suara, "teks": teks, "dibaca": lafal(teks), "fungsi": fungsi_kalimat(teks, catatan), "file": f"suara/{suara}/{i}.wav", "durasi": _durasi(dst),
                             "kategori": kategori, "catatan": catatan, "sumber": sumber, "dibuat": time.strftime("%Y-%m-%d")})
        _tulis(k)
    return True


def buat(kalimat, suara_list, kategori="", catatan=""):
    """Buat suara untuk kalimat x suara yang belum ada di perpustakaan, lalu simpan."""
    from pelafalan import lafal
    todo = [{"id": kid(s, t), "voice": s, "text": lafal(t), "asli": t} for t in kalimat for s in suara_list if not (LIB / s / f"{kid(s, t)}.wav").exists()]
    if todo:
        tmp = LIB / ".buat"
        tmp.mkdir(parents=True, exist_ok=True)
        job = tmp / "job.json"
        job.write_text(json.dumps(todo, ensure_ascii=False))
        t0 = time.time()
        subprocess.run([PY, str(TTS), str(job), str(tmp)], check=True)
        for j in todo:
            simpan(j["voice"], j["asli"], tmp / f"{j['id']}.wav", kategori, catatan, "perpustakaan_suara.py")
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
    b.add_argument("--fungsi", help="mis. 'membantah asumsi'")
    sub.add_parser("tandai", help="isi ulang kolom fungsi semua kalimat")
    sub.add_parser("ringkas", help="jumlah kalimat per fungsi")
    args = ap.parse_args()
    if args.cmd == "tambah":
        buat(args.kalimat, args.suara, args.kategori, args.catatan)
    elif args.cmd == "tandai":
        print(len(tandai_semua()["kalimat"]), "kalimat ditandai")
    elif args.cmd == "ringkas":
        from collections import Counter
        c = Counter(f for e in _baca()["kalimat"] if e["suara"] == "adriy" for f in e.get("fungsi", []))
        for nama, _ in FUNGSI:
            print(f"{c.get(nama, 0):4d}  {nama}")
    else:
        for e in _baca()["kalimat"]:
            if (not args.suara or e["suara"] == args.suara) and (not args.fungsi or args.fungsi in e.get("fungsi", [])):
                print(f"{e['suara']:7s} {e.get('durasi') or 0:5.1f}s {', '.join(e.get('fungsi', [])):40s} {e['teks'][:70]}")


if __name__ == "__main__":
    main()
