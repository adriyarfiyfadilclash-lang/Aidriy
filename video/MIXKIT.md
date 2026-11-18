# Aset Mixkit (tidak di-commit)

File asli Mixkit tidak disimpan di repo karena repo ini public dan lisensi Mixkit
melarang file aslinya disebarkan ulang. Memakainya di dalam video hasil render boleh.

## Isi

| Folder | Isi | Bisa dipakai Remotion? |
|---|---|---|
| `public/mixkit/light-leaks/` | 6 klip cahaya 1920×1080 (5 transisi 2 detik + 1 flare 5 detik) | Ya |
| `public/mixkit/images/` | PNG logo contoh dan lower third | Ya |
| `public/mixkit/sfx-kategori/` | 2033 sound effect: 654 terkait manusia (folder `human` dan 39 folder `human-<tag>`), 286 transportasi (folder `transport` dan 22 folder `transport-<tag>`), 231 hewan (folder `animals` dan 10 folder `animals-<tag>`), 305 alam (folder `nature` dan 22 folder `nature-<tag>`), 430 transisi (folder `transition` dan 18 folder `transition-<tag>`, ditambah `whoosh`), click (56), notification (41), transition (37), whoosh (20), pop (10). Tag girl, yawn, car-horn, motorcycle, race-car, cat, chicken, horse, lion, monkey, pig, wolf, morning, scary-woods, dan transition-whoosh tidak punya folder karena isinya sudah ada di folder lain. | Ya |
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

Daftar lengkap 2033 sound effect dari kategori whoosh, transition, pop, click, notification, human,
serta 31 tag suara manusia (applause, baby, blow, breathe, cartoon, cheer, children, clapping, cough,
crowd, cry, drink, eat, fight, footsteps, gasp, girl, heartbeat, hurt, kiss, laugh, run, scream, sneeze,
teeth, thank-you, voices, walk, whistle, wow, yawn, choir, fart, moan, punch, stomp, funny, happy, sad,
cute, public-places). Sebagian isi tag tambahan ini bukan suara manusia (efek game, hewan, suasana kota).
Transportasi: transport, airport, bike, boat, bus, car, car-horn, drone, emergency, engine, flying,
helicopter, motorcycle, police, race-car, rocket, ship, siren, skateboard, tanks, tractor, traffic, train,
tram, truck.
Hewan: animals, bird, cat, chicken, cow, crickets, dog, farm, horse, insect, jungle, lion, monkey, pig,
roar, safari, wild, wolf.
Alam: nature, beach, bubbles, desert, drop, fire, forest, garden, lightning, morning, mountain, night,
ocean, rain, rivers, scary-woods, sea, splash, storm, thunder, volcano, water, waterfall, waves, wind.
Transisi: transition, boom, cinematic, glitch, hit, impact, intro, movie, rewind, slide, spin, sweep,
sweeping, swell, swish, swoosh, thud, whoosh, woosh, zoom. Tiap file disimpan sekali, di kategori/tag pertama
tempat ia ditemukan,
ada di `scripts/mixkit-sfx.json`. Unduh ulang dengan:

```bash
python3 scripts/mixkit_sfx_download.py
```
