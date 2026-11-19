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

## 0. Art direction dan alur cerita

Prioritas: storytelling di atas dekorasi, visual yang relevan di atas efek, variasi suasana di atas satu latar,
kejelasan di atas keramaian, gerak bertujuan di atas animasi acak. Perencana (`art_direct` di `pipeline.py`)
memberi tiap adegan babak cerita, suasana, dan latar:

| Babak | Isi | Suasana | Latar | Komposisi khas |
|---|---|---|---|---|
| pembuka | memancing rasa penasaran, langsung menunjukkan situasi | penasaran | tema video | close-up ADRI bertanya, atau wide shot lingkungan (berganti antarvideo) |
| eksplorasi | membawa penonton masuk ke topik | eksploratif | topik kalimat / tema | wide shot lingkungan, close-up objek, peta |
| penjelasan | membantu memahami | tenang | putih-biru muda atau tema, berselang | diagram, infografis, potongan |
| menarik | kejutan, angka, perbandingan, penemuan | energik | oranye-kuning, data putih, atau dua kondisi | objek penuh layar, angka, perbandingan |
| kesimpulan | menutup cerita secara visual | elegan | minimal putih | adegan minimalis, atau close-up ADRI menyimpulkan (berganti antarvideo) |
| penutup | brand | cerah | biru brand | ADRI merayakan |

Tema video diambil terutama dari judul. Latar gelap paling banyak 35% adegan (malam, teknologi, brand).

### Latar adaptif

| Latar | Warna | Untuk | Ilustrasi |
|---|---|---|---|
| `cerah` | putih, biru muda | penjelasan ringan, fakta sehari-hari | hampir kosong |
| `arsip` | krem, cokelat, kertas | sejarah, penemuan, masa lalu | tekstur kertas, cap, peta lama |
| `alam` | hijau, turquoise | alam, lingkungan | bukit, pohon, matahari, daun melayang |
| `sains` | cyan | eksperimen, sains | meja lab, labu, tabung, gelembung |
| `hangat` | oranye, kuning | penemuan, kejutan, momen penting | sinar dari bawah |
| `pastel` | ungu, pink | psikologi, kreativitas | profil kepala, gumpalan pikiran |
| `langit` | biru langit | langit, cuaca, atmosfer | matahari, awan berjalan, cakrawala |
| `dapur` | krem hangat | makanan | rak stoples, meja, uap |
| `kota` | biru muda siang | kota, kendaraan | gedung di kejauhan |
| `data` | putih | angka sebagai fokus | titik halus |
| `banding` | sejuk / hangat | perbandingan | dua bidang |
| `bahaya` | merah muda + merah | peringatan saja | garis peringatan |
| `minimal` | putih elegan | kesimpulan | satu busur |
| `brand` | biru brand | pembuka/penutup brand | logo A samar |
| `malam`, `teknologi` | biru gelap | malam, angkasa, teknologi, dramatis | bintang; perangkat dan antarmuka |

Nama lama (`umum`, `terang`, `sejarah`, `psikologi`, `fakta`) tetap diterima. Kuning brand tetap jadi stabilo di
semua latar; di latar terang, kata aktif subtitle memakai biru brand supaya terbaca.

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
| `jauh` | 320 px | sosok kecil di wide shot lingkungan (jarak kamera, bukan ukuran berbeda) |
| close-up | 1900 px, kaki di bawah layar | karakter sebagai fokus (bertanya, menyimpulkan) |

ADRI tidak dipaksakan di semua adegan: close-up objek selalu tanpa ADRI, dan grafik, lingkaran, peta, linimasa,
potongan, serta wide shot bergantian tanpa ADRI supaya visualnya jadi fokus (`adri: false`). ADRI tetap muncul
paling tidak tiap 3 adegan.

Pose tambahan: `tanya` (bertanya), `banding` (dua telapak naik-turun seperti timbangan), `rayakan` (tinju ke atas,
melompat), `simpulkan` (jari ke atas), `tunjukkan` (mempersembahkan objek). Perencana memilih pose dari narasi:
pertanyaan -> tanya, peringatan -> angkat, fakta -> kaget, penemuan -> rayakan, kesimpulan -> simpulkan,
penutup -> rayakan. ADRI juga berpindah posisi di tengah adegan (`moveTo`), misalnya berjalan ke sisi pemenang
perbandingan sebelum menunjuknya.

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
| Posisi bervariasi | Subtitle di atas (`subtitleZone: "atas"`) bila komposisinya menaruh judul atau karakter di bawah (wide shot, close-up karakter); judul juga bisa di bawah atau di tengah kiri |

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
| `cut` | potongan langsung tepat di batas kalimat | lanjutan dalam satu babak |
| `match` | adegan lama mendorong masuk ke objeknya, adegan baru menyambung dari skala yang sama | pergantian babak, objek |
| `color` | layar dibanjiri warna latar adegan berikutnya, lalu adegan baru muncul | pergantian suasana |
| `envshift` | lingkungan baru naik dari bawah seperti cakrawala | pergantian lingkungan/babak |

Transisi dipilih dari alur cerita: pergantian babak memakai `color`, `envshift`, atau `match`; di dalam babak memakai
potongan langsung atau gerak ringan. Tiap jenis paling banyak 25% dari transisi video.

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
| `objek` (`layout: "closeup"`) | objek sangat besar terpotong di tepi kanan, judul di tengah kiri, tanpa ADRI | eksplorasi |
| `karakter` (`layout: "closeup"`) | ADRI dari pinggang ke atas, kalimat di gelembung bicara, subtitle di atas | pembuka (pertanyaan), kesimpulan |
| `lingkungan` | wide shot: ilustrasi lingkungan sesuai topik memenuhi layar (matahari, pohon, jalan dan mobil, panci, labu lab, peta gulung, dua kepala, ponsel, bulan), judul di bawah, subtitle di atas, ADRI kecil atau tanpa ADRI | pembuka atau eksplorasi |
| `objek` (`layout: "full"`) | objek memenuhi layar, dibuka dengan zoom reveal; latar kontras; ADRI kecil terkejut | fakta ("ternyata", "tahukah", "faktanya") |
| `minimalis` | ruang kosong luas, satu kalimat inti, satu garis aksen; ADRI kecil mengajak penonton | kesimpulan ("jadi", "intinya"), atau kalimat sebelum penutup |

Urutan pemilihan menurut jenis narasi: pertanyaan pembuka -> `karakter`; kesimpulan -> `minimalis`;
perbandingan ("sedangkan", "dibanding", "vs") -> `perbandingan` dengan kiri/kanan diambil dari kalimat;
sejarah -> `timeline` atau `peta`; angka -> `grafik`, `lingkaran`, `persentase`, `angka`; fakta -> objek penuh
layar; cara melakukan atau proses -> `diagram` langkah demi langkah.

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
| Teks bertumpuk dengan objek utama | irisan teks dengan objek (bukan objek yang sengaja terpotong) |
| Elemen adegan sebelumnya tertinggal | elemen dari adegan lain masih terlihat setelah transisi selesai |
| Blur tidak sengaja | filter blur pada teks di tengah adegan |
| Urutan lapisan | titik tengah subtitle harus mengenai subtitle itu sendiri |
| Komposisi berat sebelah | titik berat elemen terlalu ke tepi (catatan) |
| Latar terputus | tepi tegak tajam di bawah pita subtitle (mis. lantai habis saat kamera bergeser) |
| Gambar buram | variasi tepi sangat rendah di tengah adegan |
| Transisi terlalu panjang, komposisi atau kamera berulang | dari `scenes.json` |
| Latar gelap terlalu banyak, satu transisi mendominasi, ADRI di semua adegan | cek standar rencana (`--until scenes`) |

Hasil: gambar dan `laporan.json` di `media/<nama>/qa/`. Gambar titik tengah transisi disimpan untuk diperiksa
dengan mata.

## 12. Tema konten

Setiap video Adriy.project masuk ke salah satu dari 16 tema konten di bawah. Aturannya ada di
`pipeline/tema-konten.json`: pola kata di judul, style, latar, kicker pembuka, alur cerita, adegan, dan sound effect.

Pipeline mengenali tema dari **judul** dan memeriksa tema dari atas ke bawah. Tema pertama yang cocok yang
dipakai, jadi tema yang paling spesifik diletakkan di atas, dan "Fakta unik: kok bisa?" menjadi cadangan untuk
judul "kenapa ...". Hasilnya:

- **Style**: dipakai bila `--style auto` dan tidak ada kata kunci style yang cocok. `--style` dan `"style"` di
  naskah tetap menang.
- **Latar**: dipakai bila judul tidak menyebut topik latar (sejarah, teknologi, sains, ...).
- **Kicker pembuka**: label di adegan pertama ("Mitos atau fakta?", "Rekor dunia", ...).
- Log `[scenes] tema konten: ...` menunjukkan tema yang terdeteksi.

Kolom alur dan adegan adalah panduan menulis naskah. Kategori sound effect tema dipakai sound effect otomatis
untuk kalimat pembuka (bagian 15).

| # | Tema | Style | Latar | Kicker | Alur cerita | Adegan yang cocok | Kategori sound effect |
|---|---|---|---|---|---|---|---|
| 1 | Mitos versus fakta | adriy | banding | Mitos atau fakta? | sebut mitosnya → kenapa banyak yang percaya → faktanya + bukti/angka → kesimpulan: benar/salah/sebagian | karakter tanya, perbandingan mitos | fakta (beri angka supaya jadi VS), angka/persentase bukti, minimalis kesimpulan | game-wrong, technology-correct, transition-hit |
| 2 | Perbandingan skala | adriy | data | Seberapa besar? | benda yang dikenal penonton sebagai patokan → naik/turun skala bertahap → angka pembanding (berapa kali lipat) → penutup yang bikin takjub | objek patokan, grafik/perbandingan berangka, angka besar, lingkungan wide shot | transition-swell, transition-zoom, transition-boom |
| 3 | Perbandingan A versus B | adriy | banding | Mana yang menang? | perkenalkan A dan B → bandingkan 2-3 aspek dengan angka → pemenang tiap aspek → pemenang akhir / tergantung kebutuhan | perbandingan VS (kalimat pakai 'sedangkan'/'dibanding' + angka), grafik, karakter simpulkan | transition-hit, transition-impact, human-crowd, game-win |
| 4 | Rekor dunia | adriy | kota | Rekor dunia | tebak dulu (pancing penonton) → pemegang rekor + angkanya → pembanding agar terasa besarnya → fakta tambahan / siapa yang mendekati | angka besar, perbandingan berangka, objek/foto pemegang rekor | instrument-drum, human-applause, game-win, transition-boom |
| 5 | Hitung-hitungan sehari-hari | adriy | data | Coba hitung | situasi sehari-hari → angka per hari → dikali seminggu/sebulan/setahun → hasil akhir yang mengejutkan + saran | angka, alur (langkah hitungan), grafik naik, persentase | game-coin, kantor-write, technology-countdown, notification |
| 6 | Tips praktis | bersih | cerah | Tips praktis | masalah yang sering dialami → tips 1-3 (satu kalimat satu tips) → kenapa berhasil → ajak coba | karakter tanya, diagram/alur langkah, karakter tunjukkan, minimalis | pop, click, notification, technology-correct |
| 7 | Bongkar cara kerja | sains | sains | Bongkar cara kerja | benda yang dipakai setiap hari → bagian-bagiannya → proses langkah demi langkah → kenapa dirancang begitu | objek close-up, potongan (bagian dalam), diagram/alur proses, karakter jelaskan | pertukangan-tools, pertukangan-metal, technology-appliances, click |
| 8 | Luar angkasa | antariksa | antariksa | Luar angkasa | pertanyaan besar → fakta dengan skala/angka → perbandingan dengan Bumi → apa artinya bagi kita | lingkungan antariksa, angka besar, perbandingan skala, karakter astronot | technology-sci-fi, transport-rocket, lainnya-asteroid, transition-swell |
| 9 | Fisika simpel | laboratorium | lab | Fisika simpel | kejadian sehari-hari → konsep fisikanya dalam satu kalimat → percobaan sederhana → contoh lain di sekitar kita | karakter tunjukkan, diagram gaya/arah, objek percobaan, minimalis rumus singkat | nature-drop, nature-splash, transition-swoosh, technology-high-tech |
| 10 | Penemuan dan penemu | ekspedisi-sejarah | jurnal | Kisah penemuan | masalah sebelum ada penemuan → siapa penemunya dan kapan → momen kunci / kegagalan → dampaknya sekarang | timeline, karakter arkeolog/jurnal, objek penemuan, angka tahun | kantor-paper, kantor-write, instrument-bell, transition-cinematic |
| 11 | Psikologi sehari-hari | adriy | pastel | Psikologi sehari-hari | situasi yang relate → nama fenomenanya → apa yang terjadi di otak → cara menghadapinya | karakter pikir/tanya, diagram dua kepala/proses, persentase survei, karakter simpulkan | human-heartbeat, human-breathe, human-gasp, transition-swell |
| 12 | Kebiasaan kecil | adriy | hangat | Kebiasaan kecil | kebiasaan yang sering dianggap sepele → efeknya ke tubuh/pikiran → angka setelah seminggu/sebulan → ajak mulai hari ini | karakter senang/sapa, timeline (hari 1 - minggu 4), grafik naik, minimalis ajakan | kantor-clock, rumah-bedroom, pop, notification |
| 13 | Sains sehari-hari | laboratorium | lab | Sains sehari-hari | benda/kejadian di rumah → pertanyaan 'kenapa' → penjelasan sains singkat → fakta bonus | karakter tanya, objek close-up, diagram proses, karakter jelaskan | nature-bubbles, rumah-kitchen, nature-water, nature-fire |
| 14 | Hal yang jarang disadari | adriy | cerah | Jarang disadari | benda/kejadian biasa → detail yang terlewat → alasannya → ajak cek sendiri | objek close-up (detail), karakter kaget, potongan, minimalis | human-gasp, pop, transition-zoom, game-sparkle |
| 15 | Fakta mengejutkan | adriy | malam | Fakta mengejutkan | pancing dengan fakta paling mengejutkan → konteks → bukti/angka → fakta penutup yang lebih mengejutkan | karakter kaget, angka besar, lingkungan, objek | transition-boom, transition-impact, human-gasp, instrument-suspense-music |
| 16 | Fakta unik: kok bisa? | adriy | dari topik | Kok bisa? | fakta unik dalam satu kalimat → kok bisa? (pertanyaan) → penjelasan → kesimpulan singkat | karakter tanya, objek, diagram/potongan, minimalis | human-cartoon, pop, game-magic, transition-swoosh |

Musik latar per tema (kolom `musik`): ceria, penasaran, epik, tenang, tegang, lucu, futuristik, inspiratif; masing-masing
6 lagu Mixkit di `video/public/mixkit/musik/<mood>/` (daftar dan durasi di `musik.json`). Belum dipasang otomatis.

Catatan:
- Adegan perbandingan (VS) baru terbentuk bila kalimatnya memakai "sedangkan"/"dibanding"/"vs" **dan** ada angka.
  Untuk mitos versus fakta dan A versus B, tulis kalimat seperti "Mitosnya 8 jam, sedangkan faktanya 7 jam".
- Judul pendek berbentuk pertanyaan paling mudah dikenali ("Benarkah ...?", "Seberapa besar ...?", "Cara kerja ...").
- Menambah tema: tambah satu objek di `tema-konten.json`. Pola kata ditulis sebagai regex dengan `\b` di
  batas kata (di JSON ditulis `\\b`), lalu dicoba dengan beberapa judul supaya tidak menangkap tema lain.

## 13. Judul yang sudah pernah dibuat

**Judul (dan topik) yang sudah pernah dibuat tidak boleh dibuat lagi.** Daftarnya ada di
`pipeline/judul-terpakai.json`: 34 judul dari pemilik channel ditambah setiap video baru yang selesai dirender
(dicatat otomatis setelah langkah `finish`, kecuali folder `_...` dan `contoh...`).

Sebelum langkah pertama, pipeline membandingkan judul naskah dengan daftar itu dan **berhenti** bila sama atau
mirip, misalnya "Kenapa Gurita Punya Tiga Jantung?" ditolak karena sudah ada "Gurita dan Tiga Jantungnya".
Pemeriksaannya:
- kata inti judul (tanpa kata umum seperti kenapa/yang/bisa, imbuhan dipotong, angka disamakan:
  Rp10.000 = 10 ribu, sinonim sehari = per hari) hampir sama, atau
- judul baru memuat semua pola `inti` milik judul lama (mis. `gurita` + `jantung`, `madu` + `basi|awet`).

Pemeriksaannya sengaja ketat: lebih baik sesekali menolak topik yang sebenarnya beda daripada membuat ulang
topik yang sama. Bila memang sengaja membuat ulang (atau salah tolak), tambahkan `--judul-boleh-sama`.
Membuat ulang video yang sama (`--name` yang sama, misalnya `--from scenes`) tidak ditolak.

Saat mengusulkan topik atau menulis naskah baru, cek daftar ini dulu. Judul baru yang dibuat di luar pipeline
bisa ditambahkan manual ke `judul-terpakai.json` (kolom `judul`, `kategori`, `sumber`, dan `inti` bila perlu).

## 14. Outfit ADRIY sesuai tema

Outfit tidak boleh asal pakai. Aturannya:

1. **Satu video, satu outfit, sesuai tema.** Luar angkasa = astronot, bawah laut = penyelam, sejarah/arkeologi =
   arkeolog, sains/eksperimen = jas lab, bisnis/keuangan = jas formal, perjalanan/alam liar = travel.
   Tidak ada tema kostum yang jelas = **santai** (bawaan), bukan kostum acak.
2. **Tidak pernah ditukar karena pose/ekspresi kurang.** Bila outfit tidak punya pose yang diminta (mis. astronot
   tidak punya close-up "kaget"), yang dipakai pose netral outfit yang **sama**. Baju penyelam tidak boleh muncul
   di video luar angkasa hanya karena ekspresinya lebih lengkap. Butuh ekspresi lain: tambahkan gambarnya ke
   outfit itu di repo aset.
3. **Aset outfit tidak ada = berhenti**, bukan diganti outfit lain. Pipeline menolak render dengan pesan yang
   menyebut outfit dan asetnya (jalankan `video/scripts/ambil_aset.sh`).
4. **Berganti outfit di tengah video** dilaporkan cek standar (`outfit berganti di tengah video`).

Cara memilih (`pilih_outfit` di `pipeline.py`, kata kunci di `OUTFIT_TEMA`):
- Style tematik (Bawah Laut, Antariksa, Laboratorium, Dokumenter Alam, Ekspedisi Sejarah) menentukan outfit.
- Selain itu skor bukti: kata tema di judul = 3, tema konten yang cocok = 4 (luar angkasa -> astronot; fisika,
  sains sehari-hari, cara kerja -> jas lab), kata tema di isi naskah = 1 per kata berbeda.
- Outfit dipakai bila skornya minimal 3 **dan** jelas unggul (minimal 1,5 kali outfit kedua). Satu kata lepas di
  naskah ("ikan" di video masak, "bulan depan", "bakteri" sekali) tidak cukup. Dua tema bersaing = santai.
- Frasa yang hanya mirip tidak dihitung: "bunga matahari" bukan luar angkasa, "bulan" hanya "ke/di bulan" atau
  "bulan purnama", "laut" dihitung tapi "ikan" saja tidak.
- Log `[scenes] outfit ADRIY: ... (alasan)` menunjukkan pilihan dan buktinya. Naskah bisa menentukan sendiri
  dengan `"outfit"` di `scene`, tetap satu untuk seluruh video.

## 15. Sound effect otomatis

Kalimat yang tidak mengisi `"sfx"` di naskah mendapat sound effect otomatis (`pipeline/sfx_otomatis.py`, aturan di
`pipeline/sfx-aturan.json`) dari pustaka Mixkit (`video/public/mixkit/`) dan sound effect bebas pakai di repo aset
(`video/public/adri/sfx/`: OpenGameArt CC0, SoundBible PD/CC BY). Pustakanya diindeks sekali (±1 menit,
`video/public/.sfx-indeks.json`); setelah itu pemilihan selesai dalam hitungan detik.

Prioritas per kalimat:
1. **Kata di kalimat**: potong/iris -> suara mencincang (diutamakan kategori dapur), uang -> koin, kucing -> meong,
   ternyata/padahal -> efek kejutan, gas/uap -> desis, dapur -> peralatan dapur, dan seterusnya.
2. **Jenis adegan**: angka/persentase -> pop/ding, perbandingan -> hit, timeline -> whoosh.
3. **Babak**: pembuka -> kategori sound effect tema konten atau swell; kesimpulan -> ding/chime.
4. **Pergantian adegan** -> whoosh pendek.

Batasan supaya tidak ramai dan tidak salah:
- Paling banyak satu bunyi per kalimat, jarak minimal 4 detik, sekitar 7 bunyi per menit (minimal 2 per video).
- File yang sama tidak dipakai dua kali; satu jenis suara paling banyak 2 kali per video.
- Pola dicocokkan di **awal kata** ("cat" tidak cocok dengan "notification"/"catapult"); nama hewan sebagai kata utuh.
- Suara senjata, perkelahian, horor, kentut, dan sejenisnya tidak pernah dipilih, kecuali aturannya memang meminta
  (ledakan untuk "meletus").
- Bila kategori yang diutamakan (mis. dapur untuk "potong") habis, kalimat itu **tanpa bunyi**, bukan bunyi yang salah.
- Kata bermakna ganda diperhatikan: "menguap" (uap) bukan "menguap" (ngantuk).

Kendali dari naskah: `"sfx": "path/file.mp3"` atau objek (`file`, `role`, `at`, `max`) per kalimat menang atas
pilihan otomatis; `"sfx": false` membuat kalimat itu tanpa bunyi; `"sfxOtomatis": false` di naskah atau
`--tanpa-sfx-otomatis` mematikan semuanya. Pilihan dan alasannya tercatat di log `[sfx]` dan `media/<nama>/sfx-cues.json`.
Menambah atau memperbaiki aturan: edit `sfx-aturan.json` (lalu `--from sfx`).

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
