# AIDRIY — Second Brain AI

*Your Knowledge. One Intelligent Space.*

Aplikasi Android native untuk manajemen pengetahuan pribadi: catatan, ide, journal, dokumen, dan bantuan AI opsional. Offline-first, data milik pengguna.

## Status

| Fase | Isi | Status |
|---|---|---|
| 1 — Fondasi | Proyek, design system, 5 tab navigasi | Kode selesai, **belum di-build** |
| 2 — Data lokal | Room, CRUD, pencarian, tag, favorit, arsip | Kode selesai, **belum di-build** |
| 3 — Dokumen | Impor PDF | Belum |
| 4 — AI | Provider, API key, Ask My Brain | Belum |

### Verifikasi yang sudah dan belum dilakukan

Kode ditulis di lingkungan tanpa akses ke Android SDK maupun repositori Maven/Google. Karena itu:

- **Belum pernah dijalankan:** kompilasi Kotlin, `assembleDebug`, unit test, instrumented test, dan lint. Jalankan perintah di bawah, lalu laporkan error yang muncul.
- **Sudah dijalankan:** ke-42 query `@Query` di DAO diekstrak dari source dan dieksekusi pada SQLite asli (Python `sqlite3`) memakai skema yang sama. Hasilnya: semua query valid, dan perilaku berikut sudah terverifikasi:
  - filter aktif/favorit/arsip;
  - sort diubah/dibuat;
  - pencarian case-insensitive di judul dan isi;
  - escape `%` dan `_`;
  - filter tag;
  - satu journal per tanggal (unique index);
  - urutan riwayat dan rentang tanggal;
  - cascade relasi tag;
  - pembersihan tag yang tidak dipakai.
- **Pemeriksaan statis:** semua `R.string` terdefinisi, tanda kurung seimbang, XML valid, dan tidak ada simbol tanpa import (selain false positive yang sudah dicek manual).

## Menjalankan

**Android Studio** (Ladybug 2024.2.1+): *File → Open* → folder ini → Run.

**Command line** (JDK 17+, Android SDK 35):

```bash
./gradlew testDebugUnitTest            # unit test JVM
./gradlew lintDebug
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest    # test Room + UI, butuh emulator/HP
```

Build pertama membuat `app/schemas/com.aidriy.app.data.local.AidriyDatabase/1.json`. **Commit file ini.** File tersebut menjadi dasar test migrasi saat skema berubah.

**GitHub Actions:** setiap push menjalankan unit test, lint, dan build APK, lalu test instrumented di emulator API 34.

## Arsitektur

```
app/src/main/java/com/aidriy/app/
├── core/                     # IdGenerator, TimeProvider, format tanggal, konversi DatePicker
├── domain/
│   ├── DataResult.kt         # Success / Failure(DataError) untuk setiap penulisan
│   ├── InputRules.kt         # validasi, normalisasi tag, escape pencarian (Kotlin murni)
│   ├── model/                # Note, Idea, JournalEntry, Tag, filter, draft
│   └── repository/           # kontrak repository
├── data/
│   ├── local/                # AidriyDatabase (v1), entity, DAO, mapper, TransactionRunner
│   ├── repository/           # implementasi repository + TagWriter
│   └── LocalDataModule.kt    # perakitan repository (dipakai app & test)
├── di/                       # AppContainer (DI manual)
└── ui/
    ├── common/               # UiText, ListUiState, EditorDraft, EditorEvent
    ├── components/           # kartu, field, dialog, chip, editor scaffold, tag editor
    ├── home | notes | ideas | journal | search | insight
    └── navigation/           # 5 tab + rute editor/pencarian
```

### Skema database (versi 1)

| Tabel | Kunci & indeks |
|---|---|
| `notes` | PK `id`; indeks `updated_at`, `created_at`, (`is_archived`, `is_favorite`) |
| `ideas` | PK `id`; indeks `status`, `updated_at`, `created_at`; status ∈ `INBOX`, `IN_PROGRESS`, `COMPLETED` |
| `journal_entries` | PK `id`; **unique** `date` (ISO `yyyy-MM-dd`, tanggal lokal) |
| `tags` | PK `id`; **unique** `normalized_name` |
| `note_tags`, `idea_tags`, `journal_tags` | PK gabungan; FK ke induk dan tag dengan `ON DELETE CASCADE`; indeks `tag_id` |

Tidak ada `fallbackToDestructiveMigration`. Jika versi naik tanpa migrasi, Room gagal secara jelas, tidak menghapus data diam-diam.

## Keputusan & trade-off Fase 2

- **Simpan eksplisit, bukan autosave.**
  - Tombol ✓ menyimpan perubahan.
  - Keluar dengan perubahan yang belum disimpan memunculkan dialog *Simpan / Buang / Batal*.
  - Teks editor ada di `SavedStateHandle`, jadi tetap utuh saat rotasi maupun saat proses dimatikan sistem.
  - "Tersimpan" baru tampil setelah Room mengonfirmasi penulisan. Jika gagal, teks tetap ada dan pesan error ditampilkan.
- **`DataResult` + `DataError`.** Setiap error punya pesan Bahasa Indonesia (`UiText`). Exception database diubah menjadi `DataError.Storage` dan dicatat di log hanya berupa nama kelasnya, tanpa isi catatan.
- **Transaksi untuk operasi multi-tabel:**
  - simpan record + tag;
  - hapus + pembersihan tag;
  - ubah ide menjadi catatan (catatan dan relasi tagnya dibuat atomik, ide asli tidak dihapus).
- **Journal duplikat.**
  - Membuka tanggal yang sudah punya entri akan memuat entri tersebut.
  - Jika entri untuk tanggal itu muncul dari tempat lain saat editor terbuka, repository menolak menimpa. Editor lalu mengarah ke entri tersebut, mempertahankan teks pengguna, dan menjelaskan bahwa menyimpan lagi akan mengganti isinya.
- **Tanggal journal** adalah `LocalDate` lokal yang disimpan sebagai teks, tidak pernah diturunkan dari timestamp UTC.
  - DatePicker Material bekerja dalam milidetik UTC. Konversinya diuji agar tanggal yang dipilih tidak bergeser di zona waktu mana pun.
  - Tanggal di masa depan ditolak.
- **Favorit/arsip tidak mengubah `updated_at`.** Keduanya metadata. Tanpa ini, daftar akan berpindah urutan setiap kali bintang ditekan. Perubahan judul, isi, tag, dan status ide tetap memperbarui `updated_at`.
- **Pencarian:**
  - `LIKE` dengan parameter terikat dan `ESCAPE '!'`;
  - input di-trim dan spasi berlebih dirapikan;
  - debounce 250 ms;
  - query kosong langsung tampil tanpa jeda.
  - Belum memakai FTS; cukup untuk ribuan record.
- **Tag** dinormalisasi: `#Kerja`, `kerja`, dan `KERJA` adalah satu tag. Tag yang tidak dipakai lagi dihapus otomatis.

## Test

**Unit test JVM** (`app/src/test`):
- `SearchQueryTest`, `TagNamesTest`, `InputRulesTest`, `IdeaStatusTest`
- `DatePickerDatesTest`, `IndonesianDateFormatterTest`, `GreetingTest`
- `EditorDraftTest`, `NoteEditorViewModelTest`, `HomeViewModelTest`, `TopLevelDestinationTest`

**Instrumented** (`app/src/androidTest`):
- **Room in-memory:** `NoteRepositoryTest`, `IdeaRepositoryTest`, `JournalRepositoryTest`, `SearchAndTransactionTest` (termasuk rollback transaksi).
- **UI:**
  - `NavigationTest`: 5 tab, akses cepat, pencarian, Back.
  - `DataFlowsTest`: buat, edit, buka ulang, dan hapus catatan; dialog perubahan belum disimpan; tangkap ide dan ubah statusnya; journal hari ini; pencarian global.

### Checklist manual offline

Aplikasi **tidak meminta izin INTERNET sama sekali**, jadi semua fitur Fase 2 berjalan offline. Untuk memastikannya di HP:

1. Aktifkan mode pesawat.
2. Buat catatan, ide, dan journal hari ini. Edit salah satunya, tandai favorit, arsipkan satu catatan.
3. Tutup paksa aplikasi lewat *Settings → Apps → AIDRIY → Force stop*, lalu buka lagi. Semua data harus masih ada.
4. Cari teks yang tadi diketik dari Beranda.

## Keterbatasan yang diketahui

- Semua kode Fase 1 dan 2 belum dikompilasi atau diuji di perangkat. Lihat bagian *Verifikasi*.
- `LIKE` di SQLite hanya case-insensitive untuk huruf ASCII. Huruf beraksen seperti "é" dan "É" dianggap berbeda.
- Filter tag tersedia di Catatan dan Ide. Journal sudah mendukung penambahan tag dan filter di repository, tapi chip filternya belum ditampilkan.
- Belum ada layar kelola tag (ganti nama, warna, hapus manual). Kolom `color` sudah ada di skema.
- Isi editor lebih dari 20.000 karakter tidak ikut disimpan di saved state. Teksnya tetap aman saat rotasi, tapi jika proses dimatikan sistem, editor memuat ulang versi yang tersimpan terakhir.
- Entri journal tidak bisa dipindah ke tanggal lain.
- Status ide diubah dari editor. Belum ada aksi cepat di daftar.
- Ekspor dan backup lokal direncanakan di fase berikutnya, sesuai master prompt bagian 11.

## Langkah berikutnya

Verifikasi build dan test Fase 2. Setelah lolos, lanjut ke **Fase 3 — Dokumen** (impor PDF lewat Storage Access Framework, ekstraksi teks, status ekstraksi).
