# Pipeline video

Satu perintah dari sumber sampai video jadi, dengan subtitle yang menyala per kata:

```
yt-dlp / VoxCPM -> ffmpeg -> Whisper -> pages.json -> sound effect -> Open Montage -> Remotion + HyperFrames -> ffmpeg
ambil / suara      siapkan   transkrip   subtitle     dicampur        latar gambar   dua versi video          -14 LUFS, thumbnail
```

```bash
python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --title "Kenapa kaca mobil berkabut?"
python3 pipeline/pipeline.py video.mp4   --name klip                  # video sumber jadi latar
python3 pipeline/pipeline.py "https://..." --name klip                # yt-dlp (video milik sendiri)
python3 pipeline/pipeline.py pipeline/contoh/naskah-notesbyadriy.json --name iklan --until sfx
```

## Dari naskah

Naskah `.json` berisi kalimat, siapa yang mengucapkan (profil di `video/voices/`), dan sound effect
opsional. Lihat `contoh/naskah-notesbyadriy.json`.

```json
{"title": "NotesByAdriy", "lines": [
  {"voice": "ongky", "text": "Duh, catatanku hilang lagi.", "sfx": "public/sfx/kertas-remas.wav"},
  {"voice": "adriy", "text": "Unduh gratis sekarang!", "sfx": {"file": "public/mixkit/sfx/sukses-jawaban-benar.mp3", "at": "end"}}
]}
```

- VoxCPM membuat suara per kalimat. Kalimat yang tidak berubah tidak dibuat ulang.
- Whisper dipakai untuk waktu tiap kata, tapi ejaan subtitle diambil dari naskah ("NotesByAdriy",
  bukan "Notes by Edri" seperti yang didengar Whisper). Hasil asli Whisper di `transcript-whisper.json`.
- Sound effect: `"at": "start"` (bawaan) menaruh puncak bunyi 0,1 detik sebelum kalimat dimulai,
  `"at": "end"` setelah kalimat selesai. Volume diatur otomatis seperti komponen `Sfx` di Remotion
  (`"role": "accent"` atau `"underVoice"`). Letak dan volumenya dicatat di `sfx-cues.json`, dan
  `sfx-only.wav` bisa diperiksa dengan `video/scripts/sfx_check.py voice.wav sfx-only.wav`.
- `--until sfx` berhenti sebelum render, supaya suaranya bisa didengar dulu.

## Latar dari gambar (Open Montage)

Beri `"visual"` pada kalimat di naskah (gambar atau klip, relatif ke file naskah). Kalimat tanpa `"visual"`
melanjutkan gambar sebelumnya. Untuk rekaman atau video biasa, pakai `--visuals FOLDER`: gambarnya dibagi
rata sepanjang audio.

Open Montage merangkai gambar jadi video latar 1080x1920 (crossfade 0,4 detik, zoom pelan). Tiap klip
dipanjangkan sebesar crossfade, sehingga gambar baru mulai muncul tepat saat kalimatnya dimulai.
Remotion dan HyperFrames lalu memakai `video.mp4` itu sebagai latar, dengan subtitle dan audio di atasnya.
Gambar contoh ada di `contoh/visual/`. Ganti dengan screenshot aplikasi untuk video sungguhan.

Hasil ada di `media/<name>/` (tidak di-commit): `final-remotion.mp4`, `thumbnail.jpg`, dan semua file
antara (`transcript.json`, `pages.json`, dan lain-lain).

Bawaannya hanya versi Remotion. Pilih mesin lain dengan `--renderer hyperframes`, atau `--renderer both`
untuk dua versi sekaligus (`final-hyperframes.mp4` ikut dibuat). Data keduanya sama persis, jadi versi
lain bisa dibuat belakangan tanpa mengulang suara, transkrip, atau montase.

## Memeriksa sambungan

```bash
python3 pipeline/pipeline.py --check
```

Memeriksa semua alat (yt-dlp, ffmpeg, Whisper, VoxCPM, Remotion, HyperFrames), model dan profil suara,
sambungan antar-alat (komposisi Subtitle, `pages.json`, `sfx_analyze.py`, font dan ukuran sound effect
bersama), telemetri HyperFrames, dan skill. Tidak membuat video atau suara.

## Mengulang sebagian

Langkah yang hasilnya sudah ada dan masih baru dilewati otomatis.

| Mau | Perintah |
|---|---|
| Betulkan kata di subtitle | edit `media/<name>/transcript.json`, lalu `--from pages` |
| Ganti tampilan subtitle | ubah `video/src/pipeline/SubtitleVideo.tsx` atau bagian HyperFrames di `pipeline.py`, lalu `--from remotion` |
| Dengar suara dulu, render belakangan | `--until sfx`, lalu jalankan lagi tanpa `--until` |
| Versi HyperFrames juga | `--renderer both` (atau `--renderer hyperframes` saja) |
| Satu langkah saja | `--only transcribe` |

## Yang dibutuhkan

- Node.js 22 dan `npm install` di `video/` dan `hyperframes/`
- ffmpeg dan ffprobe
- Python dengan `transformers` dan `torch` untuk Whisper, plus `voxcpm` untuk naskah (lihat `video/requirements-tts.txt`). Atur dengan `PIPELINE_PYTHON=/path/ke/python`.
  Model diatur dengan `WHISPER_MODEL` (default `openai/whisper-large-v3-turbo`).
- OpenMontage (opsional, untuk merangkai gambar dan klip jadi montase): `pip install "open-montage[render]==0.4.0"`.
  Dipakai langkah `montage` kalau naskah punya `"visual"` atau ada `--visuals`. Atur lokasinya dengan
  `OPENMONTAGE_BIN` kalau tidak ada di PATH.
- yt-dlp, hanya kalau sumbernya URL. YouTube memblokir server cloud, jadi jalankan dari komputer sendiri.

## Data bersama

`pages.json` dibuat sekali, lalu dibaca oleh Remotion (`video/src/pipeline/SubtitleVideo.tsx`) dan
HyperFrames (HTML yang dibuat `pipeline.py`), sehingga subtitle kedua versi sama persis.
Setiap halaman berisi paling banyak 4 kata atau 24 karakter, dan dipecah di jeda bicara atau tanda baca.
