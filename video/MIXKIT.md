# Aset Mixkit (tidak di-commit)

File asli Mixkit tidak disimpan di repo karena repo ini public dan lisensi Mixkit
melarang file aslinya disebarkan ulang. Memakainya di dalam video hasil render boleh.

## Isi

| Folder | Isi | Bisa dipakai Remotion? |
|---|---|---|
| `public/mixkit/light-leaks/` | 6 klip cahaya 1920×1080 (5 transisi 2 detik + 1 flare 5 detik) | Ya |
| `public/mixkit/images/` | PNG logo contoh dan lower third | Ya |
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
