# Standar Visual Animasi Adriy.project

Tujuan: video animasi edukasi yang profesional, dinamis, sinematik, dan punya identitas visual konsisten.

Standar ini dijalankan oleh style `adriy` (`adriy.json`), pustaka adegan di
`video/src/adriy/`, dan perencana adegan di `pipeline.py` (langkah `scenes`). Tabel di bawah
menunjukkan bagaimana tiap butir diterapkan:

- **Otomatis:** dijalankan oleh sistem.
- **Dicek:** pipeline memperingatkan kalau dilanggar.
- **Naskah:** perlu diisi di naskah.
- **Belum:** belum bisa dibuat.

## 1. Identitas visual

| Standar | Penerapan | Status |
|---|---|---|
| Gaya 2D modern dan semi-realistis | ADRI dan semua adegan bergaya 2D modern (vector datar). Versi semi-realistis butuh gambar dari ilustrator: isi `character.assets` dengan PNG per pose | Otomatis (vector) / Belum (semi-realistis) |
| Hindari tampilan seperti slide | Kamera selalu bergerak, tiga lapis parallax, elemen masuk bertahap; tidak ada tata letak "judul + gambar + teks" yang statis | Otomatis |
| Komposisi editorial | Tiap jenis adegan punya komposisi sendiri (lihat bagian 5) | Otomatis |
| Tipografi, warna, karakter konsisten | Satu keluarga huruf (Plus Jakarta Sans), palet tetap di `palette`, identitas ADRI tetap di `character` | Otomatis |
| Jangan memakai bingkai gambar berulang | Tidak ada bingkai bersama; tiap jenis adegan menyajikan gambar dengan caranya sendiri, dan jenis adegan tidak boleh berulang berturut-turut | Otomatis + Dicek |

## 2. Komposisi adegan

| Standar | Penerapan | Status |
|---|---|---|
| Satu fokus visual utama | Tiap komponen adegan dibangun di sekitar satu elemen utama (ADRI, objek, angka, langkah aktif, kata kunci) | Otomatis |
| Variasi ukuran dan posisi | ADRI berganti sisi dan ukuran (1050 / 620 / 560 / 480 px); latar bergantian gelap, biru, terang; kamera bergantian | Otomatis + Dicek |
| Lapisan depan, tengah, belakang | `Shell`: belakang (cahaya lembut, grid titik), tengah (isi), depan (partikel, ADRI), masing-masing dengan faktor parallax `motion.parallax` | Otomatis |
| Hindari ruang kosong tanpa fungsi | Tata letak tiap adegan diatur supaya mengisi layar; diperiksa lewat `--preview-scenes` | Otomatis (diperiksa dengan mata) |
| Jangan penuhi layar dengan teks | Judul dipotong 7 kata, subtitle maksimal 2 baris dan 4 kata per halaman, poin maksimal 4 | Otomatis |

## 3. Sistem animasi

| Standar | Penerapan | Status |
|---|---|---|
| Masuk dan keluar halus | Masuk 0,5 dtk (ease-out), keluar 0,35 dtk, adegan bertumpuk 0,35 dtk (`motion`) | Otomatis |
| Zoom, posisi, rotasi, parallax | Kamera `push`, `pull`, `pan-left`, `pan-right`, `rise`; rotasi halus pada objek dan ikon | Otomatis |
| Objek mengikuti narasi | Adegan mulai bersama kalimatnya; di diagram, langkah aktif berpindah mengikuti waktu; mulut ADRI bergerak saat narasi | Otomatis |
| Teks bertahap | Kicker, judul, poin, dan kata muncul berurutan (`motion.stagger`) | Otomatis |
| Sorotan informasi penting | Stabilo (`Marker`) pada `highlight`; angka otomatis jadi sorotan | Otomatis / Naskah (`highlight`) |
| Transisi sesuai konteks | `slide` = pindah topik, `fade` = lanjutan, `zoom` = penekanan, `wipe` = data | Otomatis / Naskah (`"topic": true`) |

## 4. Karakter

| Standar | Penerapan | Status |
|---|---|---|
| ADRI sebagai presenter utama | Muncul di adegan karakter, objek, infografis, diagram, dan angka; minimal tiap 3 adegan | Otomatis + Dicek |
| Identitas wajah, rambut, pakaian tetap | Didefinisikan sekali di `character.identity` dan `character.colors` | Otomatis |
| Pose relevan | 6 pose: sapa (pembuka), jelaskan, tunjuk (ke objek/diagram), pikir (pertanyaan), kaget (angka), senang (penutup). Dipilih otomatis dari isi kalimat, atau ditentukan di `scene.pose` | Otomatis / Naskah |
| Hindari siluet generik | ADRI menggantikan siluet di semua adegan; ikon dipilih dari isi kalimat | Otomatis |
| Berinteraksi dengan objek dan lingkungan | ADRI menunjuk objek dan diagram, ikon melayang di sekitarnya, bereaksi pada angka. Interaksi tangan yang memegang objek butuh gambar per adegan | Sebagian |

## 5. Variasi adegan

| Jenis | Komposisi | Kapan dipilih otomatis |
|---|---|---|
| `karakter` | ADRI besar di satu sisi, kartu topik di sisi lain, ikon melayang | pembuka, pertanyaan, penutup |
| `objek` | satu objek mengambang, titik sorot dengan label penunjuk, ADRI menunjuk | kalimat dengan gambar, atau penjelasan benda |
| `infografis` | poin berikon yang muncul bertahap dengan garis penghubung | kalimat dengan 2+ bagian |
| `diagram` | langkah zig-zag dengan panah yang tergambar, langkah aktif disorot | "langkah", "pertama", "kedua", atau 3+ bagian dengan "lalu" |
| `perbandingan` | dua kolom, lencana VS, yang unggul disorot | hanya dari naskah (butuh `left` dan `right`) |
| `tipografi` | kata besar muncul satu per satu, kata kunci distabilo | kalimat pendek (maks. 12 kata) |
| `sinematik` | gambar penuh layar, bilah film, dorongan kamera, vignette | kalimat penegasan ("ingat", "aman", "penting") |
| `angka` | angka menghitung naik, cincin progres, ADRI bereaksi | kalimat dengan angka |

Aturan variasi: jenis yang sama tidak boleh muncul lagi dalam 2 adegan terakhir, tiap jenis paling banyak sekitar 30%,
dan minimal 4 jenis per video.

## 6. Kualitas akhir

| Standar | Penerapan | Status |
|---|---|---|
| Gerakan halus | Semua gerakan memakai easing (tidak linear) dan nilai kecil (zoom 1,00-1,07) | Otomatis |
| Hindari animasi berlebihan | Satu gerakan kamera per adegan; partikel dan napas karakter sangat halus | Otomatis |
| Hindari pengulangan komposisi | Jenis adegan, latar, dan kamera dicek tidak sama berturut-turut | Dicek |
| Teks terbaca di HP | Subtitle 58 px dengan plat gelap tipis, ukuran minimum 34 px, area aman 80 px dari tepi | Otomatis |
| Setiap adegan punya tujuan | Tiap jenis adegan punya fungsi (menjelaskan, menunjukkan, membandingkan, menegaskan); isi terbaik datang dari naskah | Naskah |
| Kejelasan di atas efek | Subtitle tidak pernah ditutup efek; adegan tipografi tidak menampilkan subtitle dobel | Otomatis |

## Cara memakai

```bash
python3 pipeline/pipeline.py naskah.json --name video --style adriy --until scenes   # rencana + cek standar
python3 pipeline/pipeline.py --preview-scenes --name video                           # gambar diam tiap adegan
python3 pipeline/pipeline.py naskah.json --name video --style adriy                  # baru render video
```

Hasil terbaik datang dari naskah yang mengisi `"scene"` per kalimat (lihat `contoh/naskah-adriy.json`):
jenis adegan, judul singkat, kata yang disorot, poin, langkah, angka, atau data perbandingan.
Tanpa itu, perencana memilih sendiri dari isi kalimat. Hasilnya cukup bervariasi, tapi judulnya masih
potongan narasi dan datanya terbatas, jadi anggap sebagai draf.
