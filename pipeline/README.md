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

## Style visual

Setiap style adalah satu file di `styles/`: warna, font, latar, perlakuan gambar, tekstur, bentuk dan
animasi subtitle, pengaturan montase, sound effect yang cocok, dan kata kunci topik (`useFor`).

| Style | Untuk | Tampilan |
|---|---|---|
| `bersih` | promo aplikasi, tutorial, umum | biru NotesByAdriy, Plus Jakarta Sans |
| `wayang` | sejarah, kerajaan, legenda, cerita rakyat | layar kelir dengan lampu blencong berkedip, siluet, gunungan, bingkai kawung, huruf Cinzel emas |
| `koran` | tokoh publik, politik, berita, biografi | kertas koran, kop surat kabar, foto halftone bertepi sobek, subtitle potongan kertas |
| `horor` | cerita hantu, misteri, urban legend | gelap, bulan pucat, kabut, kilat, garis VHS, judul Creepster merah, subtitle mesin tik yang muncul samar |
| `anak` | edukasi anak, dongeng, lagu, pengetahuan dasar | pastel cerah, bintang dan gelembung melayang, gambar seperti stiker, subtitle balon yang memantul (Fredoka) |
| `teknologi` | gadget, AI, internet, review | grid neon bergerak, garis sirkuit, bingkai HUD dengan garis pemindai, subtitle terminal yang muncul dengan glitch |
| `olahraga` | pertandingan, atlet, klub | garis diagonal dan garis kecepatan, bingkai miring, subtitle bilah merah seperti papan skor yang menghantam masuk (Anton) |
| `religi` | kajian, renungan, kisah teladan | gradasi senja, pola bintang bersegi delapan, bingkai lengkung, subtitle Lora yang muncul pelan; sound effect tanpa musik |
| `kuliner` | resep, review makanan, street food | krem dan terakota, taplak kotak-kotak, uap mengepul, gambar di piring bundar, judul Pacifico, subtitle label pill |
| `travel` | wisata, liburan, pendakian | peta dengan rute yang tergambar dan kompas, gambar kartu pos dengan perangko dan cap pos, judul Caveat, subtitle tiket yang meluncur |
| `bisnis` | keuangan, investasi, UMKM, karier | grafik batang yang tumbuh, garis tren, ticker angka, gambar kartu presentasi, subtitle lower third rata kiri yang terbuka dari kiri |
| `sains` | fisika, astronomi, biologi, fakta unik | cetak biru dengan grid, atom yang mengorbit, rumus samar, bingkai dengan garis dimensi, kata muncul satu per satu |

```bash
python3 pipeline/pipeline.py --list-styles          # daftar style
python3 pipeline/pipeline.py --preview-styles       # gambar pratinjau di media/style-preview/ (bukan video)
python3 pipeline/pipeline.py naskah.json --name x --style wayang
```

- Tanpa `--style` atau `"style"` di naskah, style dipilih otomatis dari kata kunci di judul dan isi
  (misalnya "Majapahit" -> wayang, "presiden" -> koran, "angker" -> horor, "dongeng" -> anak). Kalau tidak
  ada yang cocok, dipakai `bersih`. Kata kunci dicocokkan sebagai kata utuh ("ai" tidak cocok dengan "pakai"). Kalau seri (misalnya "legenda" + "kutukan"), yang menang adalah topik yang
  disebut lebih dulu, dan pipeline memberi tahu bahwa hasilnya seri.
- Di naskah, `"sfx": "style:lineStart"` memakai sound effect dari style yang terpilih.
- Membuat style baru: salin salah satu file di `styles/`, ubah isinya, lalu cek dengan `--preview-styles`.
  Pilihan yang tersedia:
  - latar (`background.kind`): `gradient`, `wayang`, `koran`, `horor`, `anak`, `tekno`, `olahraga`, `religi`,
    `kuliner`, `travel`, `bisnis`, `sains`
  - bingkai gambar (`visual.frame`): `none`, `kelir`, `torn`, `fade`, `sticker`, `hud`, `slant`, `arch`, `plate`,
    `postcard`, `card`, `blueprint`
  - tekstur (`overlays`): `grain`, `vignette`, `paper`, `fog`, `flicker`, `scanlines`
  - kotak subtitle (`subtitle.box`): `none`, `glow`, `strip`, `bubble`, `terminal`, `bar`, `tag`, `ticket`,
    `lowerthird`, `note`
  - animasi masuk (`subtitle.entrance`): `pop`, `flicker`, `stamp`, `creep`, `bounce`, `glitch`, `slam`, `fade`,
    `slide`, `wipe`, `type`
  - `titleFont` (opsional): font judul yang berbeda dari font subtitle
- Style baru tersedia di Remotion. Versi HyperFrames masih memakai tampilan bersih.
- Semua tampilan memakai CSS dan SVG biasa (tanpa WebGL), jadi render tetap andal tanpa GPU.
- Untuk tokoh nyata: pakai suara narator (Adriy/Ongky), bukan tiruan suara tokohnya; kutipan harus asli
  dan bersumber; foto harus boleh dipakai.

## Aset ADRIY semi-realistis

Gambar ADRIY (wajah, pose, close-up, bentuk mulut) disimpan di repo privat
`adriyarfiyfadilclash-lang/adriy-karakter-aset`, tidak di repo ini. Ambil sebelum membuat video:

```bash
bash video/scripts/ambil_aset.sh
```

Dengan aset: style `adriy` dan style tanpa kostum (wayang, koran, horor, anak, sains, teknologi, olahraga, bersih)
memakai ADRIY semi-realistis; bisnis, kuliner, travel, dan religi tetap memakai ADRI vector berkostum.
Tanpa aset: semua memakai ADRI vector. Aset baru: lihat `video/scripts/adri_aset.py`.

## ADRI di style lain

Video dengan style lain (wayang, koran, horor, anak, sains, teknologi, bisnis, kuliner, travel, olahraga, religi)
juga menampilkan ADRI bila naskah tidak memberi gambar atau klip. Identitasnya tetap (wajah, rambut, kacamata, hoodie
berlogo A); yang menyesuaikan adalah penyelesaian gambar dan kostumnya (`video/src/adriy/StyleCharacter.tsx`):

| Style | ADRI |
|---|---|
| wayang | siluet hitam di kelir, disinari lampu, dengan tuding |
| koran | hitam-putih seperti cetakan koran, digunting dengan pinggiran kertas |
| horor | pucat, disinari bulan, terkejut |
| anak | stiker berpinggiran putih, bergoyang |
| sains | warna asli dengan garis tepi putih tipis ala cetak biru dan garis ukur |
| teknologi | warna asli dengan garis tepi neon cyan |
| bisnis | dasi dan kartu identitas |
| kuliner | topi koki dan celemek, memegang piring |
| travel | topi dan ransel |
| olahraga | ikat kepala, nomor 10, garis tepi komik |
| religi | peci, pendar keemasan |
| bersih | ADRI biasa |

Gambar atau klip dari naskah tetap memakai bingkai khas style (kartu pos, piring, HUD, guntingan koran, ...).
Kaki ADRI selalu berpijak di atas blok subtitle, jadi subtitle tidak menutupinya.

## Standar Adriy.project (style `adriy`)

Style `adriy` bukan sekadar tampilan subtitle: videonya disusun dari **16 jenis adegan** (karakter, objek,
infografis, diagram proses, perbandingan, tipografi kinetik, sinematik, angka, grafik batang, diagram
lingkaran, linimasa, peta, diagram alir, potongan bagian dalam, persentase, minimalis) dengan presenter ADRI
berbadan penuh (12 pose, tiga ukuran menurut peran, menunjuk tepat ke objek, berjalan masuk dan keluar), kamera bertujuan, latar sesuai topik, dan transisi yang
menyambung antaradegan. Aturan lengkapnya ada di `styles/ADRIY-STANDARD.md`.

```bash
python3 pipeline/pipeline.py naskah.json --name video --style adriy --until scenes   # rencana + cek standar
python3 pipeline/pipeline.py --preview-scenes --name video                           # gambar diam tiap adegan
python3 pipeline/pipeline.py --qa --name video                                       # periksa tabrakan, teks terpotong, sisa adegan, blur
```

- Langkah `scenes` membuat `scenes.json` dan memeriksa standar: jenis adegan dan transisi tidak berulang
  berturut-turut, ADRI minimal tiap 3 adegan, durasi adegan 1,6-6,5 dtk, minimal 4 jenis adegan.
- Perencana mengenali isi kalimat: dua tahun atau lebih jadi linimasa, nama kota jadi peta, beberapa angka
  jadi grafik atau diagram lingkaran, "terdiri dari" jadi potongan, "jika ... lalu" jadi diagram alir.
- Isi `"scene"` per kalimat di naskah untuk hasil terbaik (contoh: `contoh/naskah-adriy.json`).
- ADRI saat ini versi vector 2D. Untuk versi semi-realistis, isi `character.assets` di `styles/adriy.json`
  dengan PNG transparan per pose.

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

## Judul yang sudah pernah dibuat

`pipeline/judul-terpakai.json` berisi judul video yang sudah dibuat. Pipeline menolak judul yang sama atau mirip
(lihat bagian 13 di `styles/ADRIY-STANDARD.md`); `--judul-boleh-sama` untuk membuat ulang dengan sengaja. Video baru
yang selesai dirender dicatat otomatis.
