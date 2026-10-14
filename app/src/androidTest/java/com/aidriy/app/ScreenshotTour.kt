package com.aidriy.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aidriy.app.di.DefaultAppContainer
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Not a correctness test: walks through the main screens and saves PNG screenshots to
 * files/screens so CI can publish them for design review. The example records are written
 * by this test only (fresh install on the CI emulator); the app itself never seeds data.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreenshotTour {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app = ApplicationProvider.getApplicationContext<AidriyApplication>()
    private val container get() = app.container as DefaultAppContainer
    private val outDir = File(app.filesDir, "screens").apply { mkdirs() }

    private fun waitForTag(tag: String) =
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis = 8_000)

    private fun shot(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        // Whole screen, so bottom sheets and dialogs (separate windows) are included.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A real Back key, so open sheets and dialogs (own windows) receive it first. */
    private fun back() {
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        composeRule.waitForIdle()
    }

    /** A simple drawn picture (sky, ground, buildings) so the reader can show an image block. */
    private fun sampleImageUri(): android.net.Uri {
        val bmp = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, 600f, 0xFF7FB2F0.toInt(), 0xFFDDEBFA.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1200f, 600f, p)
        p.shader = null
        p.color = 0xFF5B6472.toInt()
        listOf(80f to 260f, 260f to 200f, 420f to 300f, 640f to 180f, 860f to 240f, 1020f to 280f).forEach { (x, top) ->
            c.drawRect(x, top, x + 140f, 520f, p)
        }
        p.color = 0xFF3D4450.toInt()
        listOf(300f, 700f, 900f).forEach { x -> c.drawRect(x, 60f, x + 26f, 220f, p) }
        p.color = 0xFF8A8F98.toInt()
        c.drawRect(0f, 520f, 1200f, 600f, p)
        val file = File(app.cacheDir, "contoh-area.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return android.net.Uri.fromFile(file)
    }

    @Test
    fun tour() {
        val richId = runBlocking {
            container.database.clearAllTables()
            container.settingsRepository.setProfileName("Adriy")
            val folder = (container.fileRepository.createFolder("Kuliah") as com.aidriy.app.domain.DataResult.Success).value
            val image = (container.fileRepository.import(sampleImageUri()) as com.aidriy.app.domain.DataResult.Success).value
            val notes = container.noteRepository
            notes.create(NoteDraft("Rencana konten minggu ini", "- Review aplikasi catatan\n- Tips produktivitas\n- [ ] Edit video Jumat", listOf("Konten")), isFavorite = true)
            notes.create(NoteDraft("Ringkasan rapat", "**Keputusan:** rilis versi 0.2 minggu depan.", listOf("Kerja")))
            val rich = (
                notes.create(
                    NoteDraft(
                        "Materi Coking Plant",
                        """
                        ## 1. Pengertian Coking Plant
                        Coking plant adalah fasilitas yang mengubah **batubara** menjadi *kokas* melalui pemanasan tanpa udara.

                        ![Gambar 1. Area oven (gambar contoh)](berkas://${image.id} "penuh")

                        ## 2. Proses Utama
                        1. Batubara dimasukkan ke oven
                        2. Dipanaskan pada ==1.000–1.200 °C==
                        3. Kokas didinginkan

                        > [!PENTING]
                        > Gas samping seperti tar dan benzena perlu diolah.

                        | Produk | Kegunaan |
                        |---|---|
                        | Kokas | Bahan bakar tanur |
                        | Tar | Bahan kimia |

                        - [x] Baca bab 1
                        - [ ] Ringkas bab 2
                        """.trimIndent(),
                        listOf("Kerja", "Industri"),
                    ),
                ) as com.aidriy.app.domain.DataResult.Success
                ).value
            notes.setFolder(rich.id, folder.id)
            notes.create(
                NoteDraft(
                    "Contoh Markdown",
                    """
                    [TOC]

                    ## Kode
                    ```kotlin
                    // Hitung total belanja
                    fun total(harga: List<Int>): Int = harga.sum()
                    val pesan = "Total: ${'$'}{total(listOf(5_000, 12_500))}"
                    ```

                    ## Kutipan bertingkat
                    > Catatan dosen:
                    >> Ulangi bab 2 sebelum ujian.

                    ## Callout
                    > [!CONTOH]- Klik untuk membuka contoh
                    > Isi contoh tersembunyi.

                    > [!SUKSES] Tugas selesai
                    > Lihat juga [[Ringkasan rapat]].
                    """.trimIndent(),
                    listOf("Belajar"),
                ),
            )
            container.ideaRepository.create(IdeaDraft("Fitur tandai warna catatan", "Biar mudah dikelompokkan"))
            container.journalRepository.save(LocalDate.now(), JournalDraft("", "Hari ini fokus menyelesaikan desain ulang AIDRIY."), null)
            rich.id
        }

        waitForTag("screen_beranda")
        shot("01_beranda")

        composeRule.onNodeWithTag("nav_catatan").performClick()
        waitForTag("screen_catatan")
        shot("02_catatan")

        composeRule.onNodeWithTag("mode_folder").performClick()
        Thread.sleep(300)
        shot("03_catatan_folder")
        composeRule.onNodeWithTag("mode_all").performClick()

        composeRule.onNodeWithText("Materi Coking Plant").performClick()
        waitForTag("note_reader")
        Thread.sleep(800) // image decode
        shot("04_mode_baca")

        composeRule.onNodeWithTag("editor_edit").performClick()
        waitForTag("editor_toolbar")
        Thread.sleep(500)
        shot("05_mode_edit")

        composeRule.onNodeWithTag("tool_image").performClick()
        waitForTag("sheet_image")
        Thread.sleep(400)
        shot("06_sisipkan_gambar")
        back()
        Thread.sleep(400)

        composeRule.onNodeWithTag("tool_more").performClick()
        waitForTag("sheet_more")
        Thread.sleep(400)
        shot("07_lainnya")
        back()
        Thread.sleep(400)

        composeRule.onNodeWithTag("editor_ai").performClick()
        waitForTag("sheet_format")
        Thread.sleep(400)
        shot("08_rapikan_format")
        back()
        Thread.sleep(400)
        back() // leave edit mode
        waitForTag("note_reader")
        back()
        waitForTag("screen_catatan")

        composeRule.onNodeWithTag("nav_beranda").performClick()
        waitForTag("screen_beranda")
        composeRule.onNodeWithTag("quick_notes").performClick()
        waitForTag("editor_toolbar")
        shot("09_catatan_baru")
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("home_menu").performClick()
        waitForTag("drawer_notes")
        shot("10_menu_samping")
        back()

        composeRule.onNodeWithTag("nav_ai").performClick()
        waitForTag("screen_ai")
        shot("11_ai")

        composeRule.onNodeWithTag("nav_berkas").performClick()
        waitForTag("screen_berkas")
        shot("12_berkas")

        composeRule.onNodeWithTag("nav_pengaturan").performClick()
        waitForTag("screen_pengaturan")
        shot("13_pengaturan")
        check(richId.isNotEmpty())

        aiTour()
    }

    /** A small real PDF so the chat can show an attached document. */
    private fun samplePdfUri(): android.net.Uri {
        val doc = android.graphics.pdf.PdfDocument()
        val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = android.graphics.Paint().apply { textSize = 16f }
        listOf("Materi Coking Plant", "Proses pemanasan batubara di oven kokas.", "Produk utama: kokas.").forEachIndexed { i, line ->
            page.canvas.drawText(line, 48f, 80f + i * 28f, paint)
        }
        doc.finishPage(page)
        val file = File(app.cacheDir, "Materi_Coking_Plant.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return android.net.Uri.fromFile(file)
    }

    private fun aiTour() {
        val dao = container.database.aiDao()
        val now = System.currentTimeMillis()
        runBlocking {
            // The example key only lets the screens show their "configured" state; it is removed at the end.
            container.secretStore.setKey(com.aidriy.app.domain.model.AiProviderId.OPENAI, "sk-contoh-untuk-screenshot")
            val pdf = (container.fileRepository.import(samplePdfUri()) as com.aidriy.app.domain.DataResult.Success).value
            suspend fun conv(id: String, title: String, at: Long) =
                dao.insertConversation(com.aidriy.app.data.local.entity.AiConversationEntity(id, title, at, at))
            fun msg(id: String, conv: String, role: String, content: String, at: Long, sources: String = "", error: Boolean = false) =
                com.aidriy.app.data.local.entity.AiMessageEntity(id, conv, role, content, if (role == "assistant") "openai" else null,
                    if (role == "assistant") "gpt-4o-mini" else null, sources, false, error, at)

            conv("c-off", "Ditanya saat offline", now - 60_000)
            dao.insertMessage(msg("m-off-1", "c-off", "user", "Apa itu ViewModel?", now - 60_000))
            dao.insertMessage(msg("m-off-2", "c-off", "assistant", "offline", now - 59_000, error = true))

            conv("c-pdf", "Materi Coking Plant", now - 3_600_000)
            dao.insertMessage(
                msg("m-pdf-1", "c-pdf", "user", "Tolong jelaskan isi dokumen ini dan buat rangkuman poin pentingnya.", now - 3_600_000,
                    sources = "file:${pdf.id}|${pdf.name}"),
            )
            dao.insertMessage(
                msg("m-pdf-2", "c-pdf", "assistant",
                    "Berikut rangkuman isi dokumen **Materi_Coking_Plant.pdf**:\n\n### Poin penting\n- Proses pemanasan batubara di oven kokas\n- Produk utama: kokas\n\nJika kamu ingin penjelasan lebih detail, sebutkan bagiannya.",
                    now - 3_590_000),
            )

            conv("c-kotlin", "Belajar Kotlin Android", now - 7_200_000)
            dao.insertMessage(msg("m-k-1", "c-kotlin", "user", "Jelaskan perbedaan Activity dan ViewModel di Android.", now - 7_200_000))
            dao.insertMessage(
                msg("m-k-2", "c-kotlin", "assistant",
                    "Berikut perbedaan **Activity** dan **ViewModel**:\n\n1. **Activity**\n   - Menangani tampilan dan interaksi pengguna\n   - Mengikuti siklus hidup (lifecycle)\n2. **ViewModel**\n   - Menyimpan dan mengelola data UI\n   - Tetap ada saat rotasi layar\n\n> [!TIP]\n> Simpan logika di ViewModel, tampilan di Activity/Composable.\n\n```kotlin\nclass CatatanViewModel : ViewModel() {\n    val judul = MutableStateFlow(\"\") // bertahan saat rotasi\n}\n```",
                    now - 7_190_000),
            )
        }

        composeRule.onNodeWithTag("nav_ai").performClick()
        waitForTag("ai_empty")
        shot("14_ai_utama")

        composeRule.onNodeWithTag("ai_menu").performClick()
        waitForTag("ai_sidebar")
        Thread.sleep(400)
        shot("15_ai_riwayat")

        composeRule.onAllNodesWithText("Materi Coking Plant").onFirst().performTouchInput { longClick() }
        waitForTag("ai_delete_dialog")
        shot("16_ai_hapus")
        composeRule.onNodeWithText("Batal").performClick()

        composeRule.onNodeWithTag("ai_history_search").performTextInput("viewmodel")
        Thread.sleep(600)
        shot("17_ai_cari")
        composeRule.onNodeWithTag("ai_history_search").performTextClearance()
        Thread.sleep(400)

        composeRule.onAllNodesWithText("Belajar Kotlin Android").onFirst().performClick()
        waitForTag("ai_answer")
        Thread.sleep(600)
        shot("18_ai_percakapan")

        composeRule.onNodeWithTag("ai_menu").performClick()
        waitForTag("ai_sidebar")
        Thread.sleep(400)
        composeRule.onAllNodesWithText("Materi Coking Plant").onFirst().performClick()
        waitForTag("ai_attachment")
        Thread.sleep(600)
        shot("19_ai_berkas")

        composeRule.onNodeWithTag("ai_menu").performClick()
        waitForTag("ai_sidebar")
        Thread.sleep(400)
        composeRule.onAllNodesWithText("Ditanya saat offline").onFirst().performClick()
        waitForTag("ai_error")
        Thread.sleep(400)
        shot("20_ai_offline")

        composeRule.onNodeWithTag("ai_new_chat").performClick()
        waitForTag("ai_card_notes")
        composeRule.onNodeWithTag("ai_card_notes").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("picker_item"), 8_000)
        Thread.sleep(400)
        composeRule.onAllNodes(hasTestTag("picker_item")).onFirst().performClick()
        Thread.sleep(400)
        composeRule.onNodeWithTag("ai_send").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("ai_confirm_send"), 8_000)
        Thread.sleep(400)
        shot("21_ai_izin")
        back()
        Thread.sleep(400)

        composeRule.onNodeWithTag("ai_more").performClick()
        composeRule.onNodeWithTag("menu_ai_settings").performClick()
        waitForTag("screen_settings_ai")
        Thread.sleep(400)
        shot("22_ai_pengaturan")

        runBlocking { container.secretStore.removeKey(com.aidriy.app.domain.model.AiProviderId.OPENAI) }
        back()
        Thread.sleep(400)

        // Markdown reader: outline, highlighted code, nested quote, foldable callout, note link.
        composeRule.onNodeWithTag("nav_catatan").performClick()
        waitForTag("screen_catatan")
        composeRule.onAllNodesWithText("Contoh Markdown").onFirst().performClick()
        waitForTag("note_reader")
        Thread.sleep(600)
        shot("23_markdown")
        back()
        Thread.sleep(400)

        // Account: signed-out form, then a local validation message (nothing is sent).
        composeRule.onNodeWithTag("nav_pengaturan").performClick()
        waitForTag("settings_account")
        composeRule.onNodeWithTag("settings_account").performClick()
        waitForTag("screen_settings_account")
        Thread.sleep(400)
        shot("24_akun_masuk")
        composeRule.onNodeWithTag("acc_email").performTextInput("bukan-email")
        composeRule.onNodeWithTag("acc_password").performTextInput("rahasia123")
        composeRule.onNodeWithTag("acc_submit").performClick()
        waitForTag("acc_error")
        shot("25_akun_validasi")
        composeRule.onNodeWithTag("acc_to_register").performClick()
        Thread.sleep(300)
        shot("26_akun_daftar")
    }
}
