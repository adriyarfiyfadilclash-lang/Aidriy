# Pipeline video

Satu perintah dari sumber sampai video jadi, dengan subtitle yang menyala per kata:

```
yt-dlp -> ffmpeg -> Whisper -> pages.json -> Remotion + HyperFrames -> ffmpeg
ambil     siapkan   transkrip   subtitle     dua versi video          -14 LUFS, thumbnail
```

```bash
python3 pipeline/pipeline.py rekaman.mp3 --name kaca-mobil --title "Kenapa kaca mobil berkabut?"
python3 pipeline/pipeline.py video.mp4   --name klip                  # video sumber jadi latar
python3 pipeline/pipeline.py "https://..." --name klip                # yt-dlp (video milik sendiri)
```

Hasil ada di `media/<name>/` (tidak di-commit): `final-remotion.mp4`, `final-hyperframes.mp4`,
`thumbnail.jpg`, dan semua file antara (`transcript.json`, `pages.json`, dan lain-lain).

## Mengulang sebagian

Langkah yang hasilnya sudah ada dan masih baru dilewati otomatis.

| Mau | Perintah |
|---|---|
| Betulkan kata di subtitle | edit `media/<name>/transcript.json`, lalu `--from pages` |
| Ganti tampilan subtitle | ubah `video/src/pipeline/SubtitleVideo.tsx` atau bagian HyperFrames di `pipeline.py`, lalu `--from remotion` |
| Hanya satu versi | `--skip hyperframes` atau `--skip remotion` |
| Satu langkah saja | `--only transcribe` |

## Yang dibutuhkan

- Node.js 22 dan `npm install` di `video/` dan `hyperframes/`
- ffmpeg dan ffprobe
- Python dengan `transformers` dan `torch` untuk Whisper. Atur dengan `PIPELINE_PYTHON=/path/ke/python`.
  Model diatur dengan `WHISPER_MODEL` (default `openai/whisper-large-v3-turbo`).
- yt-dlp, hanya kalau sumbernya URL. YouTube memblokir server cloud, jadi jalankan dari komputer sendiri.

## Data bersama

`pages.json` dibuat sekali, lalu dibaca oleh Remotion (`video/src/pipeline/SubtitleVideo.tsx`) dan
HyperFrames (HTML yang dibuat `pipeline.py`), sehingga subtitle kedua versi sama persis.
Setiap halaman berisi paling banyak 4 kata atau 24 karakter, dan dipecah di jeda bicara atau tanda baca.
