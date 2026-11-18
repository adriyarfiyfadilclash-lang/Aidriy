# Video promo NotesByAdriy (HyperFrames)

Versi HyperFrames dari komposisi Remotion `PromoSuara` di `../../video/`: 7 scene, dialog Ongky dan
Adriy, dan sound effect yang diselaraskan ke gerakan. Durasi dan waktu scene sama persis (40,2 detik).

`index.html` dibuat oleh `build.py`. Jangan edit langsung: ubah `build.py` lalu jalankan ulang.

```bash
python3 build.py                                    # susun index.html
npx hyperframes check                               # periksa
npx hyperframes render -o out/promo-hyperframes.mp4 # render
```

## Aset yang tidak di-commit

- `assets/vo/*.wav`: voiceover tiruan suara Adriy dan Ongky. Salin dari `../../video/public/vo/`
  setelah dibuat dengan `../../video/scripts/tts_batch.py`.
- `assets/mixkit/*.mp3`: sound effect Mixkit. Salin dari `../../video/public/mixkit/sfx/`
  setelah diunduh lewat `../../video/MIXKIT.md`.

## Beda dengan versi Remotion

Animasi memakai easing GSAP (`back.out`, `power4.out`, `power3.out`), bukan spring Remotion, jadi
gerakannya sedikit berbeda. Sound effect tetap tepat, karena `build.py` menghitung saat mendarat dari
easing GSAP yang sama, sehingga bisa bergeser 0,1 sampai 0,2 detik dari versi Remotion.
