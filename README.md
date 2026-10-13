# AIDRIY — Second Brain AI

*Your Knowledge. One Intelligent Space.*

Aplikasi Android native untuk manajemen pengetahuan pribadi: catatan, ide, journal, dokumen, dan bantuan AI opsional. Offline-first, data milik pengguna.

## Status: Fase 1 — Fondasi aplikasi

| Bagian | Status |
|---|---|
| Proyek Android (Kotlin, Compose, Gradle Kotlin DSL) | Selesai |
| Design system (warna, tipografi, bentuk, spacing) | Selesai |
| Bottom navigation 5 tujuan | Selesai |
| Layar Beranda, Catatan, Ide, Journal, Insight | Layout + empty state |
| Konvensi ViewModel & repository | Selesai (kontrak repository; implementasi di Fase 2) |
| Build & test | **Belum dijalankan** — lihat bagian "Verifikasi" |

## Menjalankan

**Android Studio** (Ladybug 2024.2.1 atau lebih baru): *File → Open* → pilih folder ini → tunggu sinkronisasi Gradle → Run.

**Command line** (butuh JDK 17+ dan Android SDK 35; set `ANDROID_HOME` atau buat `local.properties` berisi `sdk.dir=...`):

```bash
./gradlew testDebugUnitTest      # unit test JVM
./gradlew lintDebug              # Android lint
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest   # UI test, butuh emulator/HP terhubung
```

**GitHub Actions**: push ke GitHub dan workflow `.github/workflows/android.yml` otomatis menjalankan unit test, lint, membuat APK debug, lalu menjalankan UI test navigasi di emulator. APK dan laporan test bisa diunduh dari tab *Actions*.

## Arsitektur

```
app/src/main/java/com/aidriy/app/
├── AidriyApplication.kt      # membuat AppContainer
├── MainActivity.kt           # edge-to-edge, setContent
├── di/                       # dependency injection manual
├── core/time/                # TimeProvider, Greeting, IndonesianDateFormatter
├── domain/
│   ├── model/                # Note, Idea(+IdeaStatus), JournalEntry, Tag
│   └── repository/           # kontrak NoteRepository, IdeaRepository, JournalRepository
└── ui/
    ├── AidriyApp.kt          # Scaffold + bottom navigation
    ├── navigation/           # TopLevelDestination, NavHost, ikon
    ├── theme/                # Color, Type, Shape, Spacing, Theme
    ├── components/           # ScreenHeader, SectionHeader, AidriyCard, IconBadge, EmptyState
    └── home | notes | ideas | journal | insight
```

**Lapisan:** Presentation (Compose + ViewModel) → Domain (model + interface repository) → Data (Room, Fase 2). UI tidak pernah mengakses database langsung.

**Konvensi ViewModel:** dependensi lewat konstruktor; state berupa satu `StateFlow` berisi data class immutable; Composable hanya menampilkan state dan meneruskan event. Setiap layar punya `XxxRoute` (stateful, memegang ViewModel) dan `XxxScreen` (stateless, bisa di-preview).

**Konvensi repository:** baca = `Flow`, tulis = `suspend`, database lokal adalah sumber kebenaran, tidak ada method yang butuh internet.

## Keputusan & trade-off

- **DI manual, bukan Hilt.** Satu `AppContainer` cukup untuk ukuran aplikasi ini dan menghindari annotation processing. Bisa dimigrasi ke Hilt kalau modul bertambah banyak.
- **minSdk 26.** `java.time` tersedia tanpa desugaring dan ikon adaptif native. Menjangkau sebagian besar perangkat Android yang aktif.
- **Rute navigasi berbasis string**, bukan type-safe route, supaya tidak menambah plugin kotlinx-serialization. Rute dipusatkan di `TopLevelDestination`.
- **`material-icons-extended`** dipakai untuk ikon yang konsisten. Library ini besar, tapi R8 membuang ikon yang tidak dipakai di build release. Build debug jadi lebih besar.
- **Formatter tanggal Indonesia sendiri**, bukan `DateTimeFormatter` + locale, agar hasilnya sama di semua perangkat dan di unit test.
- **Tema terang sebagai default, tanpa dynamic color**, agar aksen biru brand konsisten. Skema gelap bisa ditambahkan nanti lewat pengaturan.
- **Backup Android dimatikan** (`allowBackup=false` + `data_extraction_rules.xml`). Catatan pribadi tidak boleh tersalin ke cloud secara otomatis; ekspor/backup lokal yang eksplisit dibuat di fase berikutnya.
- **Tidak ada izin (permission) apa pun** di Fase 1.
- **Tidak ada tombol yang belum berfungsi.** Tombol buat/cari/edit baru muncul di Fase 2 bersama Room. Akses cepat di Beranda saat ini membuka tab terkait.

## Test

Unit test (JVM): `GreetingTest`, `IndonesianDateFormatterTest`, `HomeViewModelTest`, `TopLevelDestinationTest`, `IdeaStatusTest`.

Instrumented test (Compose): `NavigationTest` — layar awal Beranda, kelima tab, akses cepat, kartu Insight, dan tombol Back kembali ke Beranda.

## Verifikasi

Kode Fase 1 ditulis di lingkungan tanpa akses ke Android SDK maupun repositori Maven/Google, sehingga **build dan test belum pernah dijalankan**. Jalankan perintah di atas (atau push ke GitHub) untuk verifikasi pertama. Jika ada error, laporkan output-nya agar diperbaiki sebelum Fase 2.

## Fase berikutnya

**Fase 2 — Data lokal:** entity & DAO Room, migrasi, implementasi repository, CRUD catatan/ide/journal, tag, favorit, arsip, pencarian lokal, perlindungan perubahan belum disimpan, dan test persistensi.
