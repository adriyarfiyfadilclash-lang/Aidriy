"""Unduh ulang sound effect Mixkit dari daftar scripts/mixkit-sfx.json.

  python3 scripts/mixkit_sfx_download.py

Hasil: public/mixkit/sfx-kategori/<kategori>/<id>-<judul>.mp3 (tidak di-commit, lihat MIXKIT.md).
"""
import json
import re
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
items = json.loads((ROOT / "scripts" / "mixkit-sfx.json").read_text(encoding="utf-8"))
for it in items:
    slug = re.sub(r"[^a-z0-9]+", "-", it["title"].lower()).strip("-")
    out = ROOT / "public" / "mixkit" / "sfx-kategori" / it["categories"][0] / f'{it["id"]}-{slug}.mp3'
    if out.exists():
        continue
    out.parent.mkdir(parents=True, exist_ok=True)
    url = f'https://assets.mixkit.co/active_storage/sfx/{it["id"]}/{it["id"]}-preview.mp3'
    urllib.request.urlretrieve(url, out)
    print(out.relative_to(ROOT))
    time.sleep(0.3)
