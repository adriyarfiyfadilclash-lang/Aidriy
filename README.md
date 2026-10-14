# AIDRIY — Second Brain AI

*Your Knowledge. One Intelligent Space.*

Aplikasi Android native (Kotlin, Jetpack Compose, Room) untuk catatan, ide, journal, berkas, dan bantuan AI opsional. Offline-first: semua data tersimpan di perangkat.

## Unduh

Setiap push ke `main` membuat APK debug di [Releases](https://github.com/adriyarfiyfadilclash-lang/Aidriy/releases) (`build-N`). Pasang di atas versi lama: database dimigrasikan otomatis, catatan tidak hilang.

## Fitur

**Navigasi:** Beranda · Catatan · Ai · Berkas · Pengaturan. Menu samping untuk Disematkan, Arsip, Ide, Journal, Template, Cari, dan Riwayat AI.

- **Beranda:** salam, pencarian, akses cepat (Catatan Baru, Pindai Teks, Rekam Suara, Template, Ide, Journal), catatan terbaru.
- **Catatan:**
  - Filter Semua / Disematkan / Folder / Tag / Arsip, urutan, pencarian.
  - Catatan bisa dipindah ke folder. Foldernya sama dengan folder di Berkas.
- **Mode Baca:** catatan lama dibuka sebagai Markdown yang dirender. Checklist bisa dicentang langsung.
- **Mode Edit:**
  - Toolbar mengambang di atas keyboard.
  - Tab Teks · Gambar · Checklist · Berkas · Lainnya.
  - Urungkan/ulangi.
  - Gambar di antara teks: bisa diubah ukurannya, diberi keterangan, dipindah, dan dihapus.
  - Sisipkan gambar dari Galeri, Kamera, Scan Dokumen (dengan OCR), atau Berkas.
- **Rapikan Format (AI):** lihat bagian di bawah.
- **Ai:** chat dengan API key milik pengguna (OpenAI, Anthropic, Gemini). Catatan hanya dikirim setelah pengguna konfirmasi.
- **Berkas:** impor berkas, folder, penampil PDF, ekstraksi teks, dan OCR.
- **Pengaturan:** profil, AI, tema, ukuran teks, simpan otomatis, cadangan zip, kunci PIN/biometrik, dan pengingat.

### Markdown yang didukung

| Jenis | Sintaks |
|---|---|
| Heading | `#` sampai `######` |
| Gaya teks | `**tebal**`, `*miring*` / `_miring_`, `__garis bawah__`, `~~coret~~`, `==sorot==`, `` `kode` `` |
| Daftar | `-` / `*` / `+`, `1.` / `1)`, `- [ ]` / `- [x]`, bersarang dengan indentasi |
| Blok | `> kutipan`, `> [!INFO]` / `[!TIP]` / `[!PENTING]` / `[!PERINGATAN]`, blok ` ``` `, `---` |
| Tabel | `\| tabel \|` dengan perataan `:--` / `:-:` / `--:` |
| Tautan | `[tautan](https://…)` dan URL langsung |
| Gambar | `![keterangan](berkas://id "kecil\|sedang\|penuh")` |
| Escape | `\` |

### Rapikan Format

1. Gambar dan tautan berkas diganti placeholder sebelum dikirim ke AI.
2. Jawaban AI diperiksa: setiap kata harus sama dan urutannya tetap. Yang boleh berubah hanya huruf kapital, tanda baca, dan penanda Markdown.
3. Jika kata berubah, pratinjau menampilkan peringatan berisi daftar kata yang hilang atau bertambah.
4. Jika gambar hilang, hasil ditolak.
5. Hasil hanya diterapkan setelah pratinjau Sebelum/Sesudah dan konfirmasi, dan bisa diurungkan.

## Build & test

Build dan test berjalan di GitHub Actions (`.github/workflows/android.yml`):

| Job | Isi |
|---|---|
| `apk` | `assembleDebug` lalu rilis `build-N`. Jika gagal, log dikirim ke branch `ci-logs`. |
| `checks` | `testDebugUnitTest` dan `lintDebug`. Jika gagal, log dikirim ke branch `ci-logs-checks`. |
| `ui-test` | Emulator API 34 menjalankan `ScreenshotTour` (screenshot dikirim ke branch `ci-screens`), lalu `connectedDebugAndroidTest`. |

Lokal (JDK 17, Android SDK 35): `./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest`.

**Test yang ada:**
- **Unit:**
  - Parser Markdown dan pemeriksa Rapikan Format.
  - Editor: mode baca/edit, undo/redo, centang tugas, folder, alur Rapikan Format.
  - Beranda, aturan input, tanggal, pencarian, tag.
- **Instrumented:**
  - Repository Room dan transaksi.
  - Migrasi v1 ke v3.
  - Navigasi 5 tab.
  - Alur buat → baca → edit → hapus, centang dari mode baca, sheet editor.

## Database

Room versi 3, tanpa migrasi destruktif.

| Versi | Perubahan |
|---|---|
| v2 | `notes.reminder_at`, `folders`, `files`, `ai_conversations`, `ai_messages` |
| v3 | `notes.folder_id` (FK ke `folders`, `ON DELETE SET NULL`) |

## Keterbatasan yang diketahui

- Tidak ada sinkronisasi cloud. Cadangan dibuat manual berupa zip yang tidak terenkripsi.
- Mode Edit menampilkan penanda Markdown (diredupkan), sedangkan Mode Baca menampilkan hasil akhirnya.
- Gambar dari internet tidak dimuat. Hanya gambar di Berkas yang ditampilkan.
- Kualitas Rapikan Format bergantung pada model AI. ID model bisa diubah di Pengaturan, tapi ketersediaannya belum diverifikasi untuk tiap akun.
- Bar "Poin Penting" dari desain belum dibuat karena fungsinya belum ditentukan.
