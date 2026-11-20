"""Kamus pelafalan: ubah tulisan yang susah dibaca model suara (istilah asing, singkatan, angka, satuan) menjadi
ejaan sesuai bunyi. Hanya untuk SUARA: subtitle dan judul tetap memakai tulisan asli dari naskah.

  lafal("Enzim itu ditemukan tahun 2002 oleh NASA, hemat 25%.")
  -> "Enzim itu ditemukan tahun dua ribu dua oleh nasa, hemat dua puluh lima persen."

Urutan: istilah di pipeline/kamus-pelafalan.json (frasa terpanjang dulu) -> uang -> satuan -> persen -> angka.
Coba dari baris perintah: python3 pipeline/pelafalan.py "kalimat ..."
"""
import json
import re
import sys
from pathlib import Path

KAMUS = Path(__file__).resolve().parent / "kamus-pelafalan.json"
_SATU = ["", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "delapan", "sembilan", "sepuluh", "sebelas"]


def terbilang(n: int) -> str:
    """Bilangan bulat -> kata bahasa Indonesia (0 .. 999 triliun)."""
    if n < 0:
        return "minus " + terbilang(-n)
    if n == 0:
        return "nol"
    if n < 12:
        return _SATU[n]
    if n < 20:
        return terbilang(n - 10) + " belas"
    if n < 100:
        return (terbilang(n // 10) + " puluh " + (terbilang(n % 10) if n % 10 else "")).strip()
    if n < 200:
        return ("seratus " + (terbilang(n - 100) if n > 100 else "")).strip()
    if n < 1000:
        return (terbilang(n // 100) + " ratus " + (terbilang(n % 100) if n % 100 else "")).strip()
    if n < 2000:
        return ("seribu " + (terbilang(n - 1000) if n > 1000 else "")).strip()
    for besar, nama in ((10 ** 12, "triliun"), (10 ** 9, "miliar"), (10 ** 6, "juta"), (1000, "ribu")):
        if n >= besar:
            return (terbilang(n // besar) + f" {nama} " + (terbilang(n % besar) if n % besar else "")).strip()
    return str(n)


def _angka(s: str) -> str:
    """'10.000' -> sepuluh ribu; '1,39' -> satu koma tiga sembilan; '2002' -> dua ribu dua."""
    s = s.strip()
    if re.fullmatch(r"\d{1,3}(\.\d{3})+", s):  # pemisah ribuan
        return terbilang(int(s.replace(".", "")))
    if "," in s:
        a, b = s.split(",", 1)
        return terbilang(int(a or 0)) + " koma " + " ".join(terbilang(int(c)) if c != "0" else "nol" for c in b)
    if "." in s:  # desimal gaya Inggris (3.5)
        a, b = s.split(".", 1)
        return terbilang(int(a or 0)) + " koma " + " ".join(terbilang(int(c)) if c != "0" else "nol" for c in b)
    return terbilang(int(s))


_kamus_cache = None


def kamus():
    global _kamus_cache
    if _kamus_cache is None:
        d = json.loads(KAMUS.read_text(encoding="utf-8")) if KAMUS.exists() else {}
        istilah = {k: v for k, v in d.get("istilah", {}).items()}
        satuan = d.get("satuan", {})
        _kamus_cache = (sorted(istilah.items(), key=lambda kv: -len(kv[0])), satuan)
    return _kamus_cache


ANGKA = r"\d+(?:[.,]\d+)*"


def lafal(teks: str) -> str:
    istilah, satuan = kamus()
    t = teks
    for tulis, baca in istilah:
        # singkatan huruf besar dicocokkan persis (WHO, bukan "who"); istilah lain tanpa beda huruf besar/kecil
        flags = 0 if tulis.isupper() else re.I
        t = re.sub(rf"(?<![\w-]){re.escape(tulis)}(?![\w-])", baca, t, flags=flags)
    # uang: Rp10.000 / Rp 10 ribu / $5
    t = re.sub(rf"Rp\.?\s*({ANGKA})(\s*(?:ribu|juta|miliar|triliun))?", lambda m: f"{_angka(m.group(1))}{m.group(2) or ''} rupiah", t)
    t = re.sub(rf"(?<!\w)(?:US)?\$\s*({ANGKA})(\s*(?:ribu|juta|miliar))?", lambda m: f"{_angka(m.group(1))}{m.group(2) or ''} dolar", t)
    # persen dan derajat
    t = re.sub(rf"({ANGKA})\s*%", lambda m: f"{_angka(m.group(1))} persen", t)
    t = re.sub(rf"({ANGKA})\s*°\s*C\b", lambda m: f"{_angka(m.group(1))} derajat celsius", t)
    t = re.sub(rf"({ANGKA})\s*°", lambda m: f"{_angka(m.group(1))} derajat", t)
    # angka + satuan (km, kg, km/jam, ...)
    if satuan:
        pola = "|".join(re.escape(s) for s in sorted(satuan, key=len, reverse=True))
        t = re.sub(rf"({ANGKA})\s*({pola})(?![\w/])", lambda m: f"{_angka(m.group(1))} {satuan[m.group(2)]}", t)
        # "1,39 juta km": satuan setelah kata besaran
        t = re.sub(rf"\b(ribu|juta|miliar|triliun)\s+({pola})(?![\w/])", lambda m: f"{m.group(1)} {satuan[m.group(2)]}", t)
    # rentang angka: 2-3 -> dua sampai tiga
    t = re.sub(rf"(?<![\w.,])({ANGKA})\s*[-–]\s*({ANGKA})(?![\w])", lambda m: f"{_angka(m.group(1))} sampai {_angka(m.group(2))}", t)
    # urutan: ke-3 -> ketiga
    t = re.sub(r"\bke-(\d+)\b", lambda m: "ke" + ("satu" if m.group(1) == "1" else terbilang(int(m.group(1)))).replace(" ", " "), t)
    # sisa angka
    t = re.sub(rf"(?<![\w]){ANGKA}(?![\w])", lambda m: _angka(m.group(0)), t)
    return re.sub(r"\s{2,}", " ", t).strip()


if __name__ == "__main__":
    for s in sys.argv[1:] or [sys.stdin.read()]:
        print(lafal(s))
