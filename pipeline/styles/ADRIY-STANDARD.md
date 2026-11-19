# Standar Visual Animasi Adriy.project

Tujuan: video animasi edukasi yang profesional, dinamis, sinematik, dan punya identitas visual konsisten.

Standar ini dijalankan oleh style `adriy` (`adriy.json`), pustaka adegan di `video/src/adriy/`, dan
perencana adegan di `pipeline.py` (langkah `scenes`). Tabel di bawah menunjukkan bagaimana tiap butir
diterapkan:

- **Otomatis:** dijalankan oleh sistem.
- **Dicek:** pipeline memperingatkan kalau dilanggar.
- **Naskah:** perlu diisi di naskah.
- **Belum:** belum bisa dibuat.

## Susunan kode

| File | Isi |
|---|---|
| `motion.ts` | Tata letak layar (area konten y 140-1440, lantai y 1440, pita subtitle y 1480-1760), easing, spring, mask reveal, motion blur selektif, angka acak deterministik |
| `camera.tsx` | Kamera virtual: `establish`, `focus`, `track`, `pan`, `emphasis`, `perspective`, `drift`, `path`, dan lapisan parallax `Layer` |
| `Adri.tsx` | ADRI berbadan penuh: 12 pose, arah pandang, siklus berjalan, memegang objek, urutan pose berganti dengan spring |
| `environments.tsx` | 8 latar topik: umum, sains, sejarah, teknologi, alam, kota, data, terang |
| `infographics.tsx` | Grafik batang, donat, linimasa, peta, diagram alir, waffle persentase, label penunjuk, potongan objek, angka menghitung naik |
| `typography.tsx` | Judul kinetik per kata, kicker, stabilo, subtitle di pita aman |
| `transitions.tsx` | Transisi masuk/keluar yang saling menyambung |
| `scenes.tsx` | 16 jenis adegan (objek punya dua tata letak), ukuran ADRI per peran, 5 lapisan |
| `qa.tsx` + `video/scripts/qa.mjs` | Pemeriksa visual dan teknis (bagian 11) |
| `PoseSheet.tsx` | Gambar diam semua pose ADRI (komposisi `PoseSheet`) |

## 1. Identitas visual

| Standar | Penerapan | Status |
|---|---|---|
| Karakter ADRI tetap | Wajah oval sawo matang, rambut hitam berjambul ke kanan, kacamata bulat, hoodie biru tua dengan garis biru terang dan logo A, kaos putih. Satu definisi di `Adri.tsx` dan `character` di `adriy.json`, dipakai di semua pose | Otomatis |
| Brand ADRIY.PROJECT | Penutup menampilkan tagline, slogan, dan tulisan ADRIY.PROJECT | Otomatis |
| Warna biru, navy, putih, kuning | Semua latar berangkat dari navy brand; kuning `#FFB547` untuk aksen dan stabilo | Otomatis |
| Gaya 2D, tipografi tetap, 9:16 | Vector 2D, Plus Jakarta Sans, 1080x1920, 30 fps | Otomatis |
| Semi-realistis | Butuh gambar ilustrator: isi `character.assets` dengan PNG per pose | Belum |

## 2. Komposisi

| Standar | Penerapan | Status |
|---|---|---|
| Asimetris dan berlapis | Tiap adegan punya tata letak sendiri (ADRI di sisi, judul di sisi lain, bidang diagonal, jalur melengkung). Tiga kedalaman: latar (0,25), isi (1), depan (1,12) | Otomatis |
| Tidak ada kotak, kartu, lingkaran berulang | Ikon memakai bentuk bergantian (heksagon, belah ketupat, tetes, segitiga). Lencana VS belah ketupat. Lingkaran hanya di adegan donat | Otomatis |
| Tiap adegan punya karakter sendiri | 16 jenis adegan (lihat bagian 9); jenis yang sama tidak boleh berurutan | Otomatis + Dicek |
| 10 keluarga komposisi | `objek-penuh`, `presenter-objek`, `presenter` (layar terbagi ADRI + judul), `tipografi`, `infografis`, `linimasa`, `proses`, `perbandingan`, `sinematik`, `minimalis`. Dua adegan berurutan tidak sekeluarga, kecuali isinya menuntut (data yang bersambung); perencana menukar tata letak atau jenis | Otomatis + Dicek |
| Lima lapisan | 1 latar (gradasi + tekstur, diam), 2 dekorasi/lingkungan (kedalaman 0,3), 3 objek utama (1,0), 4 karakter (1,06), 5 teks/subtitle/penekanan (tidak ikut kamera). Parallax dibatasi supaya tidak berlebihan | Otomatis |
| Tidak ada elemen yang bertabrakan atau terpotong | Konten hanya di y 140-1440; ADRI selalu berpijak di lantai y 1440; zoom kamera berporos di lantai, jadi kaki ADRI tidak turun ke pita subtitle | Otomatis (diperiksa dengan pratinjau) |

## 3. Pose dan gerak ADRI

Ukuran ADRI mengikuti fungsinya, bukan angka bebas per adegan:

| Peran | Tinggi | Dipakai di |
|---|---|---|
| `kecil` | 500 px | pendamping infografis, diagram, peta, linimasa, perbandingan, kesimpulan |
| `sedang` | 800 px | presenter objek, angka, grafik, persentase, infografis |
| `besar` | 1150 px | pembuka, ekspresi, penekanan, penutup |

| Pose | Kegunaan |
|---|---|
| `sapa` | membuka video |
| `jelaskan` | berdiri menjelaskan (tangan bergerak saat bicara) |
| `tunjuk` | menunjuk objek |
| `angkat` | mengangkat tangan |
| `jalan` | berjalan (kaki dan lengan berayun, badan naik turun) |
| `kaget` | terkejut (angka mengejutkan) |
| `pikir` | berpikir (pertanyaan, perbandingan) |
| `pegang` | memegang objek (`holding`) |
| `sentuh` | berinteraksi dengan diagram atau grafik |
| `lihat` | melihat ke arah informasi |
| `ajak` | berbicara kepada penonton |
| `senang` | menutup video |

- Arah pandang: `maju`, `penonton`, `atas`, `bawah`. Pupil dan wajah bergeser, sehingga ADRI tampak menoleh.
- Pose bisa berupa urutan (`[{at: 0, pose: "lihat"}, {at: 1.6, pose: "sentuh"}]`). Perpindahan pose memakai spring.
- Saat diam ADRI tetap hidup: bernapas, berkedip, lengan bergoyang halus, dan mulut bergerak saat narasi.
- Kepala mengangguk kecil saat bicara. Saat menunjuk atau menyentuh, lengan diarahkan tepat ke titik target (`pointAt`), tubuh condong ke sana, dan mata mengikuti. Terkejut membuat tubuh tersentak ke belakang sebentar.
- Masuk dan keluar: berjalan masuk dari samping (`walkFrom`), mendekat ke objek sebelum menyentuhnya, dan berjalan keluar di akhir adegan karakter (`adriExit`).
- Menunjuk sinkron dengan informasi: di infografis ADRI menunjuk tiap poin saat poin itu muncul; di diagram dan linimasa ia menunjuk langkah atau peristiwa yang aktif.
- ADRI bukan hiasan sudut. Ia berjalan masuk, menyentuh batang grafik, berjalan di sepanjang linimasa dan proses, menunjuk pemenang perbandingan, dan bereaksi pada angka.

## 4. Animasi

| Standar | Penerapan |
|---|---|
| Masuk dan keluar halus | Adegan bertumpuk 0,7 dtk. Adegan baru masuk di atas adegan lama, dan adegan lama keluar dengan gerak yang melengkapi transisi berikutnya |
| Spring dan easing | `EASE.out`, `EASE.in`, `EASE.inOut`, `EASE.back`, spring dengan redaman tinggi; tidak ada gerak linear kecuali parallax latar |
| Zoom, pan, tracking, parallax | Lewat kamera (bagian 5) |
| Mask reveal | Judul naik dari balik mask; label terbuka dari kiri; peta tergambar dari barat ke timur |
| Kinetic typography dan stagger | Kata muncul berurutan; poin, batang, pin, dan langkah muncul bertahap |
| Motion blur selektif | Hanya saat bergerak cepat (`speedBlur`), yaitu transisi whip pan dan zoom-through |

## 5. Kamera

| Shot | Tujuan | Dipakai di |
|---|---|---|
| `focus` | mendekat ke titik saat informasi penting dijelaskan | objek, angka, grafik, potongan, persentase |
| `establish` | menjauh, memperlihatkan keseluruhan ilustrasi | lingkaran |
| `track` | mengikuti objek yang bergerak | infografis, alur, sinematik (ADRI berjalan) |
| `path` | berpindah antartitik secara horizontal untuk menunjukkan proses | diagram, timeline, peta |
| `emphasis` | menekankan karakter | karakter |
| `perspective` | berpindah sudut pandang | perbandingan |
| `drift` | gerak sangat halus untuk teks | tipografi |

| `panV` | menurun menyusuri isi dari atas ke bawah | pengganti untuk infografis, tipografi, alur |
| `reveal` | mulai dekat pada detail lalu mundur (zoom reveal) | objek penuh layar; pengganti untuk karakter, objek, angka, potongan |
| `rack` | bergeser antarlapisan (lapisan depan bergerak lebih jauh) | pengganti untuk perbandingan |

Zoom tiap jenis adegan berbeda. Kalau kamera bawaan sama dengan adegan sebelumnya, perencana memberi
`altShot: true` dan adegan memakai kamera penggantinya. `shot` di naskah mengganti keduanya. Semua zoom
berporos di lantai, dan kamera tidak pernah bergeser ke bawah, jadi kaki ADRI tidak masuk pita subtitle.

## 6. Infografis

Semua bergerak: batang tumbuh dengan spring dan angkanya menghitung naik, segmen donat tergambar satu per
satu dengan label penunjuk siku, linimasa tergambar sambil kamera menyusurinya, pin peta jatuh lalu rute
tergambar, node alir muncul dengan panah dan cabang Ya/Tidak, waffle 10x10 terisi dari bawah, cangkang objek
terbelah dan lapisan dalamnya muncul berurutan.

## 7. Tipografi dan subtitle

| Standar | Penerapan |
|---|---|
| Judul bertahap | Kicker (garis tumbuh lalu teks), lalu judul per kata |
| Warna aksen dan stabilo | Frasa `highlight` diberi stabilo kuning dan ukuran 1,14x di tempat frasa itu muncul |
| Per kata atau per kalimat | `KineticTitle mode="word"` atau `"line"` |
| Tidak menutupi objek atau karakter | Judul di lapisan 5 (tidak ikut kamera), di area atas |
| Subtitle sinkron dan aman | Kata yang diucapkan berwarna aksen. Kata kunci ditebalkan dan digaris bawah. Posisi selalu di pita y 1480-1760 |
| Tanpa pengulangan | Subtitle disembunyikan bila 80% isinya sudah tampil sebagai judul; adegan tipografi tidak menampilkan subtitle |

Mode subtitle (kolom `subtitle`, atau bawaan per jenis adegan):

| Mode | Tampilan | Bawaan untuk |
|---|---|---|
| `standar` | plat gelap tipis, kata yang diucapkan berwarna aksen | objek, infografis, perbandingan, potongan |
| `kata` | kata kunci besar berwarna, kata lain lebih kecil | angka, persentase |
| `kinetik` | kata muncul mengikuti ucapan, kata aktif terangkat | karakter |
| `minimalis` | kecil tanpa plat | diagram, grafik, lingkaran, linimasa, peta, alur, minimalis |
| `latar` | plat warna brand | penekanan (dari naskah) |
| `tanpa` | tanpa plat, bayangan tegas | sinematik, objek penuh layar |

## 8. Transisi

| Transisi | Gerak | Dipilih otomatis untuk |
|---|---|---|
| `slide` | geser horizontal | pembuka, pindah topik |
| `whip` | kamera menyapu cepat dengan motion blur | pindah topik |
| `iris` | lingkaran terbuka dari objek fokus, dengan cincin aksen | objek, potongan, peta, penekanan |
| `shape` | belah ketupat brand membesar | lanjutan, data |
| `morph` | gumpalan yang berubah bentuk | lanjutan |
| `wipe` | sapuan diagonal | data |
| `zoom` | menembus dari depan kamera | penekanan |
| `push` | adegan baru mendorong adegan lama ke atas | lanjutan, data |
| `smooth` | larut cepat dengan sedikit gerak naik | lanjutan, kesimpulan |
| `cover` | bentuk aksen brand menutup layar dari objek fokus, lalu membuka adegan baru (transisi objek) | fakta, kesimpulan |
| `fade` | larut | hanya jika diminta di naskah |

Transisi tidak pernah sama dengan transisi sebelumnya (Dicek). Lama transisi sama dengan tumpang tindih
nyata dua adegan (0,6 dtk, `motion.overlap` 0,3 di tiap sisi), dan adegan lama berhenti tepat saat adegan baru
utuh. Blur hanya muncul selama sapuan whip yang cepat; transisi lain tetap tajam supaya teks terbaca.

## 9. Jenis adegan dan pemilihan otomatis

| Jenis | Komposisi | Dipilih otomatis bila |
|---|---|---|
| `karakter` | ADRI besar berjalan masuk lalu bicara ke penonton; judul besar di sisi lain | pembuka, pertanyaan, penutup |
| `objek` | objek besar dengan label penunjuk; ADRI menyentuhnya | kalimat dengan gambar |
| `infografis` | poin berikon naik di jalur melengkung; ADRI melihat ke atas | kalimat dengan 2+ bagian |
| `diagram` | proses horizontal; kamera bergeser per langkah; ADRI berjalan mengikutinya | "langkah", "pertama", atau 3+ bagian dengan "lalu" |
| `perbandingan` | layar terbelah diagonal; ADRI berpikir lalu menunjuk pemenang | dari naskah (`left`, `right`) |
| `tipografi` | kata besar per kata, tanda kutip raksasa | kalimat pendek |
| `sinematik` | lanskap berparallax; ADRI berjalan melintas | kalimat penegasan ("ingat", "aman") |
| `angka` | angka raksasa menghitung naik dengan sinar dan pengukur; ADRI terkejut | satu angka |
| `grafik` | grafik batang; ADRI menyentuh batang tertinggi | 2+ angka berlabel |
| `lingkaran` | donat dengan label penunjuk | 2+ persen yang jumlahnya sekitar 100 |
| `timeline` | linimasa panjang; ADRI berjalan di bawahnya; latar sejarah | 2+ tahun dalam kalimat |
| `peta` | peta Indonesia sederhana, pin, rute | 2+ nama kota/pulau |
| `alur` | diagram alir dengan percabangan | "jika ... lalu/maka ..." |
| `potongan` | cangkang terbelah memperlihatkan lapisan dalam | "terdiri dari", "lapisan", "bagian dalam" |
| `persentase` | waffle 10x10 dan angka persen; ADRI mengangkat tangan | satu angka persen |
| `objek` (`layout: "full"`) | objek memenuhi layar, dibuka dengan zoom reveal; latar kontras; ADRI kecil terkejut | fakta ("ternyata", "tahukah", "faktanya") |
| `minimalis` | ruang kosong luas, satu kalimat inti, satu garis aksen; ADRI kecil mengajak penonton | kesimpulan ("jadi", "intinya"), atau kalimat sebelum penutup |

Urutan pemilihan menurut jenis narasi: pertanyaan pembuka -> `karakter`; kesimpulan -> `minimalis`;
perbandingan ("sedangkan", "dibanding", "vs") -> `perbandingan` dengan kiri/kanan diambil dari kalimat;
sejarah -> `timeline` atau `peta`; angka -> `grafik`, `lingkaran`, `persentase`, `angka`; fakta -> objek penuh
layar; cara melakukan atau proses -> `diagram` langkah demi langkah.

Latar mengikuti topik kalimat (atau topik video, berselang): sejarah (tekstur kertas, vinyet hangat), sains
(ruang eksperimen, heksagon, atom), teknologi (grid digital, sirkuit), alam, kota (gedung, hanya untuk topik
kota), psikologi (simbolik, minimalis). Komposisi juga punya latarnya: perbandingan `banding` (terbagi), fakta
`fakta` (kuning kontras), kesimpulan `minimal`, data `data`. Partikel hanya ada di sains, alam, dan teknologi.
Latar yang sama tidak dipakai dua kali berturut-turut, kecuali untuk linimasa.

Aturan variasi: jenis yang sama tidak muncul lagi dalam 2 adegan terakhir, tiap jenis paling banyak sekitar 30%,
minimal 4 jenis per video, dan ADRI muncul paling tidak tiap 3 adegan (Dicek).

## 10. Kualitas akhir

| Standar | Penerapan | Status |
|---|---|---|
| 1080x1920, 9:16, 30 fps | Komposisi `AdriyVideo` | Otomatis |
| Tidak ada tabrakan atau karakter terpotong | Zona konten, lantai, dan pita subtitle tetap; diperiksa dengan pratinjau | Otomatis + diperiksa dengan mata |
| Tidak ada transisi rusak | Adegan bertumpuk 0,7 dtk; tiap transisi diuji di titik tengahnya | Otomatis |
| Tiap elemen punya alasan | Tiap jenis adegan punya fungsi; isi terbaik datang dari naskah | Naskah |

## 11. Pemeriksaan visual dan teknis

```bash
python3 pipeline/pipeline.py --qa --name video   # video dari media/video/scenes.json
python3 pipeline/pipeline.py --qa                # contoh semua jenis adegan
```

Untuk tiap adegan diambil gambar diam di titik tengah transisi, tepat setelah transisi selesai, di tengah, dan
menjelang akhir. Komponen `QAProbe` mengukur semua elemen bertanda `data-qa` di browser (posisi akhir setelah
kamera, opacity, blur, nomor adegan). Yang diperiksa:

| Pemeriksaan | Cara |
|---|---|
| Teks, objek, karakter, atau subtitle keluar layar/terpotong | kotak elemen di luar 1080x1920 (badan ADRI, bukan ruang lengannya) |
| Teks terpotong di dalam kotaknya | `scrollWidth > clientWidth` |
| Subtitle bertabrakan | kotak subtitle beririsan dengan karakter, objek, atau teks |
| Teks tertutup karakter, teks saling bertabrakan | irisan kotak |
| Elemen adegan sebelumnya tertinggal | elemen dari adegan lain masih terlihat setelah transisi selesai |
| Blur tidak sengaja | filter blur pada teks di tengah adegan |
| Urutan lapisan | titik tengah subtitle harus mengenai subtitle itu sendiri |
| Komposisi berat sebelah | titik berat elemen terlalu ke tepi (catatan) |
| Latar terputus | tepi tegak tajam di bawah pita subtitle (mis. lantai habis saat kamera bergeser) |
| Gambar buram | variasi tepi sangat rendah di tengah adegan |
| Transisi terlalu panjang, komposisi atau kamera berulang | dari `scenes.json` |

Hasil: gambar dan `laporan.json` di `media/<nama>/qa/`. Gambar titik tengah transisi disimpan untuk diperiksa
dengan mata.

## Cara memakai

```bash
python3 pipeline/pipeline.py naskah.json --name video --style adriy --until scenes   # rencana + cek standar
python3 pipeline/pipeline.py --preview-scenes --name video                           # gambar diam tiap adegan
python3 pipeline/pipeline.py naskah.json --name video --style adriy                  # baru render video
```

Pratinjau tanpa naskah (gambar diam, dari folder `video/`):

```bash
npx remotion still ScenePreview out.png --props='{"type":"timeline","at":3.2}'      # satu jenis adegan
npx remotion still ScenePreview out.png --props='{"type":"objek","prev":"karakter"}' # titik tengah transisi
npx remotion still ScenePreview out.png --props='{"type":"objek","extra":{"layout":"full"}}'  # varian
npx remotion still PoseSheet pose.png                                                # semua pose ADRI
```

Hasil terbaik datang dari naskah yang mengisi `"scene"` per kalimat (lihat `contoh/naskah-adriy.json`):
jenis adegan, judul singkat, kata yang disorot, dan datanya (`points`, `steps`, `data`, `events`, `pins`,
`layers`, `decision`, `left`/`right`, `value`). Kolom `env`, `shot`, `transition`, `layout`, `subtitle`, dan
`adriExit` juga bisa diisi untuk mengganti pilihan otomatis. Tanpa itu, perencana memilih sendiri dari isi kalimat. Hasilnya cukup bervariasi,
tapi judulnya masih potongan narasi, jadi anggap sebagai draf.
