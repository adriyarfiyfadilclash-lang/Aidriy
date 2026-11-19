"""Olah lembar aset ADRIY (gambar semi-realistis) jadi satu file PNG transparan per pose.

    /root/voxcpm-venv/bin/python scripts/adri_aset.py [folder] [--peta]   # bawaan: public/adri/santai

Langkah:
  1. Latar: lembar tanpa transparansi punya pola kotak-kotak abu-abu (239-255) sebagai piksel. Latar diambil
     dengan isian banjir dari tepi gambar (piksel terang dan tidak berwarna); ADRIY punya garis tepi gelap jadi
     isian berhenti di sana. Kantong latar yang terkurung (misalnya di antara lengan dan badan) dikenali dari
     campuran dua abu-abu khas pola kotak-kotak.
  2. Pinggiran: lembar yang sudah transparan sering punya awan pinggiran merah/kuning sisa penghapusan latar.
     Piksel setengah transparan dibuang, lalu piksel merah/kuning pekat di tepi, lalu potongan kecil yang lepas.
  3. Potong: lembar dibagi per baris lalu per kolom di celah kosong terdekat dari posisi yang diharapkan.
  4. Simpan pose/<lembar>-<n>.png (dipangkas) dan pose/index.json (ukuran, tinggi baris asal untuk skala).
Hasilnya tetap di public/adri/ (tidak masuk git).
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else ROOT / "public" / "adri" / "santai"
OUT = SRC / "pose"

# tata letak tiap lembar: jumlah gambar per baris
LAYOUT = {"00-penuh-berdiri.webp": [1], "dekat/setengah-badan.webp": [1], "dekat/dekat-09.png": [3, 2]}
for i in range(1, 10):
    LAYOUT[f"lembar-{i:02d}.png"] = [4]
for i in range(1, 13):
    LAYOUT.setdefault(f"dekat/dekat-{i:02d}.png", [2, 2])


def background_mask(rgb):
    """True = latar kotak-kotak."""
    mx, mn = rgb.max(2), rgb.min(2)
    # ambang terang mengikuti abu-abu tergelap pola kotak-kotak di sudut gambar (tiap lembar berbeda)
    corner = np.concatenate([rgb[:40, :40].reshape(-1, 3), rgb[:40, -40:].reshape(-1, 3)]).min(1)
    thr = int(min(226, max(205, np.percentile(corner, 2) - 5)))
    cand = (mn >= thr) & ((mx - mn) <= 14)
    # bila latarnya kotak-kotak (variasi terang-gelap tinggi di sudut), isian hanya boleh lewat area yang
    # bermotif: bidang putih rata (sepatu, kaos) tidak ikut terhapus walau garis tepinya tidak tertutup
    lum0 = rgb.mean(2)
    var21 = ndi.uniform_filter(lum0 * lum0, 21) - ndi.uniform_filter(lum0, 21) ** 2
    if np.median(np.concatenate([var21[:40, :40].ravel(), var21[:40, -40:].ravel()])) > 25:
        cand &= ndi.binary_dilation(var21 > 12, iterations=3)
    lab, n = ndi.label(cand)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    # kantong terkurung (di antara kaki, lengan dan badan): dikenali dari pola kotak-kotak itu sendiri, yaitu
    # variasi terang-gelap lokal yang tinggi. Bidang putih rata (sepatu, kaos) variasinya rendah.
    lum = rgb.mean(2)
    var = ndi.uniform_filter(lum * lum, 9) - ndi.uniform_filter(lum, 9) ** 2
    idx = np.arange(1, n + 1)
    sizes = ndi.sum(np.ones_like(lum), lab, index=idx)
    mvar = ndi.mean(var, lab, index=idx)
    for k in idx:
        if k in border or sizes[k - 1] < 300:
            continue
        if mvar[k - 1] <= 25:
            continue
        v = lum[lab == k]
        lo, hi = ((v >= thr) & (v <= 247)).mean(), (v >= 250).mean()
        if lo > 0.2 and hi > 0.2:  # dua abu-abu kotak-kotak sama-sama banyak (sepatu putih: hampir semua >= 250)
            bg |= lab == k
    return bg


def clean(alpha, rgb, from_alpha):
    m = alpha > (200 if from_alpha else 0)
    if from_alpha:
        # pinggiran merah/kuning pekat di dekat tepi (sisa penghapusan latar sebelumnya)
        edge = ndi.distance_transform_edt(m) <= 4
        r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
        red = (r > 170) & (g < 110) & (b < 110)
        yel = (r > 190) & (g > 160) & (b < 90)
        m &= ~(edge & (red | yel))
    # kupas: piksel abu-abu terang di tepi luar (sisa kotak-kotak) dibuang berulang sampai bertemu garis tepi
    # gelap ADRIY. Kaos putih/sepatu tidak terkena karena dibatasi garis tepi hitam.
    mx, mn = rgb.max(2).astype(int), rgb.min(2).astype(int)
    # hanya pola kotak-kotak (terang-gelap bergantian, variasi lokal tinggi), bukan bidang putih rata (sepatu)
    lum = rgb.mean(2)
    var = ndi.uniform_filter(lum * lum, 9) - ndi.uniform_filter(lum, 9) ** 2
    light = (mn >= 195) & ((mx - mn) <= 18) & (var > 60)
    for _ in range(40 if not from_alpha else 0):
        edge = m & ~ndi.binary_erosion(m)
        peel = edge & light
        if not peel.any():
            break
        m = m & ~peel
    m = ndi.binary_opening(m, iterations=1)
    lab, n = ndi.label(m)
    if n:
        sizes = ndi.sum(m, lab, index=np.arange(1, n + 1))
        keep = np.isin(lab, np.arange(1, n + 1)[sizes >= max(400, sizes.max() * 0.004)])
        m &= keep
    # lubang kecil (bukan kantong latar) diisi
    holes = ndi.binary_fill_holes(m) & ~m
    hl, hn = ndi.label(holes)
    if hn:
        hs = ndi.sum(holes, hl, index=np.arange(1, hn + 1))
        m |= np.isin(hl, np.arange(1, hn + 1)[hs < 250])
    return m


def cuts(profile, parts):
    """Titik potong di celah paling kosong dekat posisi yang diharapkan."""
    L = len(profile)
    sm = np.convolve(profile, np.ones(9) / 9, mode="same")
    out = []
    for j in range(1, parts):
        c = int(L * j / parts)
        lo, hi = max(1, c - int(L * 0.14)), min(L - 1, c + int(L * 0.14))
        out.append(lo + int(np.argmin(sm[lo:hi])))
    return [0, *out, L]


def process(rel):
    path = SRC / rel
    im = Image.open(path).convert("RGBA")
    arr = np.array(im)
    rgb = arr[..., :3]
    a = arr[..., 3]
    from_alpha = (a < 10).mean() > 0.2
    m = clean(a, rgb, True) if from_alpha else clean(np.where(background_mask(rgb), 0, 255).astype(np.uint8), rgb, False)
    alpha = np.where(m, 255, 0).astype(np.uint8)
    # tepi halus 1 px
    alpha = np.array(Image.fromarray(alpha).filter(__import__("PIL.ImageFilter", fromlist=["x"]).GaussianBlur(0.7)))
    alpha[~ndi.binary_dilation(m, iterations=1)] = 0
    rows = LAYOUT[rel]
    H, W = m.shape
    row_cuts = cuts(m.sum(1).astype(float), len(rows)) if len(rows) > 1 else [0, H]
    stem = Path(rel).name.split(".")[0]
    items, k = [], 0
    for r, count in enumerate(rows):
        y0, y1 = row_cuts[r], row_cuts[r + 1]
        band = m[y0:y1]
        col_cuts = cuts(band.sum(0).astype(float), count) if count > 1 else [0, W]
        for c in range(count):
            x0, x1 = col_cuts[c], col_cuts[c + 1]
            sub = m[y0:y1, x0:x1]
            ys, xs = np.nonzero(sub)
            k += 1
            if not len(ys):
                continue
            by0, by1, bx0, bx1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
            pad = 6
            Y0, Y1 = max(0, y0 + by0 - pad), min(H, y0 + by1 + pad)
            X0, X1 = max(0, x0 + bx0 - pad), min(W, x0 + bx1 + pad)
            piece = np.dstack([rgb[Y0:Y1, X0:X1], alpha[Y0:Y1, X0:X1]])
            # hanya isi kolom/baris potongan ini (tetangga yang masuk bantalan dibuang)
            piece[..., 3][:, : max(0, x0 - X0)] = 0
            piece[..., 3][:, max(0, x1 - X0):] = 0
            # potongan kecil dari gambar tetangga di tepi kotak dibuang (sisakan bagian >= 3% dari yang terbesar)
            pm = piece[..., 3] > 0
            pl, pn = ndi.label(pm)
            if pn > 1:
                ps = ndi.sum(pm, pl, index=np.arange(1, pn + 1))
                piece[..., 3][~np.isin(pl, np.arange(1, pn + 1)[ps >= ps.max() * 0.03])] = 0
            name = f"{stem}-{k}.png"
            Image.fromarray(piece).save(OUT / name, optimize=True)
            items.append({"file": f"pose/{name}", "w": int(X1 - X0), "h": int(Y1 - Y0), "rowH": int(y1 - y0),
                          "bottomGap": int((y1 - y0) - (by1)), "dariTransparan": bool(from_alpha)})
    return items


def main_siap():
    """Outfit yang gambarnya sudah satu pose per file (tata.json "siap": true): buat index dari pose/*.png.
    Gambar ini dipangkas ke isi masing-masing, jadi tinggi asal pose jongkok/duduk ditentukan dari "tinggi"
    (bagian dari tinggi berdiri) supaya skala orang tetap sama di video."""
    t = json.loads(_TATA.read_text())
    items = []
    for f in sorted(OUT.glob("*.png")):
        w, h = Image.open(f).size
        frac = next((v for k, v in t.get("tinggi", {}).items() if f.stem.startswith(k)), 1.0)
        items.append({"file": f"pose/{f.name}", "w": w, "h": h, "rowH": int(round(h / frac)), "bottomGap": 0, "dariTransparan": True})
    (OUT / "index.json").write_text(json.dumps({"pose": items}, indent=1))
    print(f"{len(items)} pose siap pakai")


def main():
    OUT.mkdir(exist_ok=True)
    if _TATA.exists() and json.loads(_TATA.read_text()).get("siap"):
        return main_siap()
    index = {}
    for rel in LAYOUT:
        if not (SRC / rel).exists():
            continue
        items = process(rel)
        index[rel] = items
        print(f"{rel:28s} -> {len(items)} pose")
    (OUT / "index.json").write_text(json.dumps(index, indent=1))


# ---------------------------------------------------------------- peta pose -> gambar (dibaca video)
PENUH = {"sapa": "lembar-01-4", "jelaskan": "lembar-09-2", "tunjuk": "lembar-09-4", "angkat": "lembar-08-3",
         "jalan": ["lembar-02-1", "lembar-02-2", "lembar-02-3", "lembar-02-4"], "kaget": "lembar-08-2", "pikir": "lembar-09-3",
         "pegang": "lembar-06-3", "sentuh": "lembar-09-2", "lihat": "lembar-04-1", "ajak": "lembar-09-4", "senang": "lembar-05-2",
         "tanya": "lembar-04-2", "banding": "lembar-09-2", "rayakan": "lembar-07-3", "simpulkan": "lembar-08-3",
         "tunjukkan": "lembar-08-4", "netral": "lembar-09-1"}
DEKAT = {"sapa": "dekat-08-1", "jelaskan": "dekat-12-1", "tunjuk": "dekat-02-1", "angkat": "dekat-11-4", "kaget": "dekat-01-3",
         "pikir": "dekat-01-1", "tanya": "dekat-10-1", "rayakan": "dekat-06-4", "senang": "dekat-07-4", "simpulkan": "dekat-02-2",
         "ajak": "dekat-02-1", "tunjukkan": "dekat-06-1", "lihat": "dekat-12-4", "banding": "dekat-12-2", "pegang": "dekat-06-3",
         "sentuh": "dekat-07-1", "jalan": "dekat-12-1", "netral": "dekat-12-1"}
BICARA = ["dekat-09-1", "dekat-09-4", "dekat-09-2", "dekat-09-3", "dekat-09-5"]  # A, setengah, E, U, O
KEDIP = "dekat-12-3"
BICARA_PENUH = []  # bentuk mulut versi badan penuh (pose tubuh sama), untuk bicara di adegan biasa

# Outfit lain: folder punya tata.json sendiri {"layout": {file: [baris]}, "penuh": {...}, "dekat": {...},
# "bicara": [...], "kedip": ...}. Kunci yang tidak ada memakai bawaan outfit santai di atas.
_TATA = SRC / "tata.json"
if _TATA.exists():
    _t = json.loads(_TATA.read_text())
    LAYOUT = _t.get("layout", LAYOUT)
    PENUH = _t.get("penuh", PENUH)
    DEKAT = _t.get("dekat", {})
    BICARA = _t.get("bicara", [])
    KEDIP = _t.get("kedip")
    BICARA_PENUH = _t.get("bicaraPenuh", [])


def write_map():
    index = json.loads((OUT / "index.json").read_text())
    info = {Path(it["file"]).stem: it for items in index.values() for it in items}
    base = f"adri/{SRC.name}"  # dibaca video dari public/adri/<outfit>/ (salinan repo aset)
    ent = lambda k: {**{x: info[k][x] for x in ("w", "h", "rowH")}, "file": f"{base}/{info[k]['file']}"}  # noqa: E731
    pick = lambda v: [ent(k) for k in v] if isinstance(v, list) else [ent(v)]  # noqa: E731
    peta = {"penuh": {p: pick(v) for p, v in PENUH.items() if all(k in info for k in (v if isinstance(v, list) else [v]))},
            "dekat": {p: pick(v) for p, v in DEKAT.items() if v in info},
            "bicara": [ent(k) for k in BICARA if k in info], "kedip": ent(KEDIP) if KEDIP in info else None,
            "bicaraPenuh": [ent(k) for k in BICARA_PENUH if k in info]}
    (SRC / "peta.json").write_text(json.dumps(peta, indent=1))
    print("peta.json:", len(peta["penuh"]), "pose badan penuh,", len(peta["dekat"]), "pose dekat")


if __name__ == "__main__":
    if "--peta" not in sys.argv:  # --peta: hanya tulis ulang peta.json dari potongan yang sudah ada
        main()
    write_map()
