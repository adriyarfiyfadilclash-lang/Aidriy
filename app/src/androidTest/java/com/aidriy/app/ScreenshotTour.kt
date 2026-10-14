package com.aidriy.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
    }
}
