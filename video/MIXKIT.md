# Aset Mixkit (tidak di-commit)

File asli Mixkit tidak disimpan di repo karena repo ini public dan lisensi Mixkit
melarang file aslinya disebarkan ulang. Memakainya di dalam video hasil render boleh.

## Isi

| Folder | Isi | Bisa dipakai Remotion? |
|---|---|---|
| `public/mixkit/light-leaks/` | 6 klip cahaya 1920×1080 (5 transisi 2 detik + 1 flare 5 detik) | Ya |
| `public/mixkit/images/` | PNG logo contoh dan lower third | Ya |
| `public/mixkit/sfx-kategori/` | 2998 sound effect dari semua 276 tag Mixkit, dikelompokkan per folder (lihat "Semua sound effect per kategori") | Ya |
| `public/mixkit/sfx/` | 19 sound effect (transisi, pop, klik, AI, sukses) | Ya |
| `mixkit-templates/` | Template `.moti` asli | Tidak, hanya Apple Motion / Final Cut Pro (Mac) |

## Memakai light leak

Klipnya berlatar hitam. Tumpuk di atas scene dengan `mixBlendMode: "screen"`
supaya hitamnya hilang dan tinggal cahayanya:

```tsx
import { Video } from "@remotion/media";
import { staticFile } from "remotion";

<Video
  src={staticFile("mixkit/light-leaks/light-leak-transition-1.mp4")}
  muted
  style={{ mixBlendMode: "screen", width: "100%", height: "100%", objectFit: "cover" }}
/>
```

## Memulihkan di komputer lain

Ekstrak zip Mixkit ke sini dengan nama file seperti di atas:
- `mixkit-light-leak-motion-opener-728.zip` → `728/Solids/*.mp4` ke `public/mixkit/light-leaks/`
- `mixkit-business-logo-lower-third-672.zip`, `mixkit-minimal-shape-logo-reveal-657.zip` → PNG ke `public/mixkit/images/`
- Semua zip → `mixkit-templates/`

## Sound effect dari situs Mixkit

Diunduh dari `https://assets.mixkit.co/active_storage/sfx/<id>/<id>-preview.mp3`.
Unduh ulang semuanya ke `public/mixkit/sfx/`:

```bash
mkdir -p public/mixkit/sfx && cd public/mixkit/sfx
while IFS='|' read id name; do
  curl -sSf -o "$name.mp3" "https://assets.mixkit.co/active_storage/sfx/$id/$id-preview.mp3"
done <<'LIST'
1490|transisi-whoosh-cepat
166|transisi-sweep-kecil
3120|transisi-teknologi-slide
175|transisi-sweep-pendek
3115|transisi-swoosh-cepat
3005|pop-whoosh-ringan
2358|pop-panjang
2357|pop-gelembung-notifikasi
2356|pop-kering-notifikasi
2568|klik-antarmuka
1120|klik-centang-kotak
3124|klik-teknologi-modern
2531|ketik-keyboard-laptop
1133|jepret-kamera
2350|ai-magic-sparkle-whoosh
2344|ai-magic-notifikasi
2870|sukses-jawaban-benar
951|sukses-notifikasi-positif
2867|sukses-konfirmasi
LIST
```

## Semua sound effect per kategori

Semua tag yang ditautkan dari halaman sound effect Mixkit (276 tag) sudah diambil: 2998 sound effect.
Daftar lengkapnya (ID, judul, durasi, folder) ada di `scripts/mixkit-sfx.json`.
Tiap file hanya disimpan sekali, di folder tag pertama tempat ia ditemukan, jadi satu suara bisa ada
di folder yang tidak terduga (misalnya suara perang di `human-fight` atau `transition-impact`).

| Kelompok | Jumlah | Folder |
|---|---|---|
| Awal | 164 | `click`, `notification`, `transition`, `whoosh`, `pop` |
| Manusia | 654 | `human`, `human-<tag>` |
| Transisi | 393 | `transition-<tag>` |
| Teknologi | 362 | `technology`, `technology-<tag>` |
| Alam | 305 | `nature`, `nature-<tag>` |
| Transportasi | 286 | `transport`, `transport-<tag>` |
| Hewan | 231 | `animals`, `animals-<tag>` |
| Game | 174 | `game`, `game-<tag>` |
| Alat musik | 118 | `instrument`, `instrument-<tag>` |
| Rumah dan dapur | 106 | `rumah-<tag>` |
| Pertukangan dan benda | 76 | `pertukangan-<tag>` |
| Kantor dan sekolah | 57 | `kantor-<tag>` |
| Perang | 32 | `warfare`, `warfare-<tag>` |
| Lain-lain | 19 | `lainnya-<tag>` |
| Pesta | 12 | `pesta-<tag>` |
| Olahraga | 9 | `olahraga-<tag>` |

Sebagian isi tag tidak persis sesuai kelompoknya (misalnya efek game di tag `cartoon`, atau air keran
di tag `water`). Sound effect yang tidak diberi tag apa pun di Mixkit tidak ikut terambil.

Unduh ulang semuanya dengan:

```bash
python3 scripts/mixkit_sfx_download.py
```
