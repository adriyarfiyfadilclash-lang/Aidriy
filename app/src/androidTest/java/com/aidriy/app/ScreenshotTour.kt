package com.aidriy.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun back() = composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun tour() {
        runBlocking {
            container.database.clearAllTables()
            container.settingsRepository.setProfileName("Adriy")
            val notes = container.noteRepository
            notes.create(NoteDraft("Rencana konten minggu ini", "- Review aplikasi catatan\n- Tips produktivitas\n- [ ] Edit video Jumat", listOf("Konten")), isFavorite = true)
            notes.create(NoteDraft("Ringkasan rapat", "**Keputusan:** rilis versi 0.2 minggu depan.\nPIC: desain dan pengujian.", listOf("Kerja")))
            notes.create(NoteDraft("Daftar belanja", "Telur, roti, kopi susu, gula aren"))
            container.ideaRepository.create(IdeaDraft("Fitur tandai warna catatan", "Biar mudah dikelompokkan"))
            container.journalRepository.save(LocalDate.now(), JournalDraft("", "Hari ini fokus menyelesaikan desain ulang AIDRIY."), null)
        }

        waitForTag("screen_catatan")
        shot("01_catatan")

        composeRule.onNodeWithTag("home_menu").performClick()
        waitForTag("drawer_notes")
        shot("02_menu_samping")
        back()

        composeRule.onNodeWithTag("quick_notes").performClick()
        waitForTag("screen_note_editor")
        shot("03_editor_catatan")
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_catatan")

        composeRule.onNodeWithTag("quick_templates").performClick()
        waitForTag("screen_templates")
        shot("04_template")
        back()
        waitForTag("screen_catatan")

        composeRule.onNodeWithTag("quick_scan").performClick()
        waitForTag("screen_scan")
        shot("05_pindai_teks")
        back()
        waitForTag("screen_catatan")

        composeRule.onNodeWithTag("quick_ideas").performClick()
        waitForTag("screen_ideas")
        shot("06_ide")
        back()
        waitForTag("screen_catatan")

        composeRule.onNodeWithTag("nav_ai").performClick()
        waitForTag("screen_ai")
        shot("07_ai")

        composeRule.onNodeWithTag("nav_berkas").performClick()
        waitForTag("screen_berkas")
        shot("08_berkas")

        composeRule.onNodeWithTag("nav_pengaturan").performClick()
        waitForTag("screen_pengaturan")
        shot("09_pengaturan")
    }
}
