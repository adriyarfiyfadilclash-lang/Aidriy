# Memasang sound effect supaya tepat

Sound effect terasa "asal masuk" karena empat hal: pilihannya tidak cocok dengan gerakan, waktunya
meleset, volumenya tidak seimbang dengan suara orang, atau ada jeda hening di awal file. Proyek ini
menangani keempatnya.

## 1. Pilih bunyi berdasarkan gerakannya

Satu bunyi untuk satu kejadian di layar, sesuai sifat gerakannya:

| Gerakan di layar | Jenis bunyi |
|---|---|
| Scene bergeser atau meluncur | sweep atau whoosh, puncaknya di tengah transisi |
| Elemen muncul dan memantul (ikon, logo) | pop, puncaknya saat elemen mendarat |
| Fitur tertentu | bunyi yang menggambarkan fiturnya (ketik, jepret kamera, sparkle) |
| Hasil atau ajakan (tombol, centang) | bunyi sukses atau konfirmasi |

Fade lembut tidak diberi bunyi. Tidak semua gerakan perlu bunyi.

## 2. Ukur file-nya dulu

```bash
python3 scripts/sfx_analyze.py public/sfx/kertas-remas.wav public/mixkit/sfx/*.mp3 > src/promo/sfx-meta.json
```

Dicatat untuk tiap file: hening di awal (`onset`), letak bagian terkeras (`peak`), durasi, kekerasan
rata-rata (`loudness`), dan hentakan terkeras (`peakDb`). Diukur di 48 kHz, supaya bunyi berfrekuensi
tinggi seperti kertas tidak terukur terlalu pelan.

## 3. Pasang dengan komponen `Sfx`

```tsx
<Sfx name="Pop logo" file="mixkit/sfx/pop-kering-notifikasi.mp3"
     at={landFrame(fps, 0, 0.8, SPRING_12)} align="peak" role="accent" />
```

- `at`: frame kejadiannya. `landFrame()` menghitung frame saat animasi mencapai 90% geraknya, dengan
  timing dan easing yang sama dengan scene. Kalau animasi di scene diubah, angka di `landFrame()`
  juga harus diubah.
- `align="peak"`: bagian terkeras bunyi jatuh tepat di `at`. Hening di awal file dipotong otomatis.
- `role`: `accent` (tidak bersamaan dengan suara orang) atau `underVoice` (saat orang bicara, jauh
  lebih pelan). Volume dihitung otomatis dari rata-rata dan hentakan terkerasnya.
- `max`: potong setelah sekian detik, dengan fade-out singkat.

## 4. Periksa hasilnya

```bash
npx remotion render PromoSuara out/stem-voice.wav --codec=wav --props='{"voice":true,"sfx":false}'
npx remotion render PromoSuara out/stem-sfx.wav   --codec=wav --props='{"voice":false,"sfx":true}'
python3 scripts/sfx_check.py out/stem-voice.wav out/stem-sfx.wav out/sfx-check.png
```

Script mencetak setiap bunyi beserta waktu puncaknya, dan menandai TERLALU KERAS kalau bunyi
bersamaan dengan suara orang dan kurang dari 8 dB di bawahnya. Ambil gambar video di detik puncak
tiap bunyi untuk mengecek waktunya cocok dengan gerakan. Penilaian akhir tetap dengan telinga.

Catatan: `PromoSuara` memakai sound effect Mixkit yang tidak di-commit. Unduh dulu lewat
`MIXKIT.md` sebelum me-render di komputer lain.
