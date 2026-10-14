package com.aidriy.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.AidriyApplication
import com.aidriy.app.MainActivity
import com.aidriy.app.di.DefaultAppContainer
import com.aidriy.app.domain.model.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end flows through the real UI and the app's real Room database.
 * Tables are cleared before each test so runs are independent.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DataFlowsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container: DefaultAppContainer
        get() = ApplicationProvider.getApplicationContext<AidriyApplication>().container as DefaultAppContainer

    @Before
    fun clearDatabase() {
        container.database.clearAllTables()
    }

    private fun waitForText(text: String) =
        composeRule.waitUntilAtLeastOneExists(hasText(text), timeoutMillis = 5_000)

    private fun waitForTag(tag: String) =
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis = 5_000)

    /** The splash shows briefly on launch. */
    @Before
    fun waitForHome() = waitForTag("screen_beranda")

    private fun openDrawerItem(tag: String) {
        composeRule.onNodeWithTag("home_menu").performClick()
        waitForTag(tag)
        composeRule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun create_read_edit_and_delete_a_note() {
        composeRule.onNodeWithTag("quick_notes").performClick()
        waitForTag("screen_note_editor")
        composeRule.onNodeWithTag("editor_title").performTextInput("Catatan uji")
        composeRule.onNodeWithTag("editor_content").performTextInput("Isi **pertama**")
        // New note + autosave: Back saves and closes.
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_beranda")
        waitForText("Catatan uji")

        // Existing notes open in the reader, with Markdown rendered.
        composeRule.onNodeWithText("Catatan uji").performClick()
        waitForTag("note_reader")
        composeRule.onNodeWithTag("reader_title").assertTextContains("Catatan uji")
        composeRule.onAllNodesWithText("Isi pertama").onFirst().assertIsDisplayed()

        composeRule.onNodeWithTag("editor_edit").performClick()
        waitForTag("editor_title")
        composeRule.onNodeWithTag("editor_title").performTextClearance()
        composeRule.onNodeWithTag("editor_title").performTextInput("Catatan diedit")
        composeRule.onNodeWithTag("editor_done").performClick()
        waitForTag("note_reader")
        composeRule.onNodeWithTag("reader_title").assertTextContains("Catatan diedit")

        composeRule.onNodeWithTag("editor_menu").performClick()
        composeRule.onNodeWithTag("menu_delete").performClick()
        composeRule.onNodeWithText("Hapus catatan?").assertIsDisplayed()
        composeRule.onNodeWithText("Hapus").performClick()

        waitForTag("screen_beranda")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Catatan diedit").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun catatan_tab_lists_notes_and_reader_ticks_tasks() {
        val id = runBlocking {
            (container.noteRepository.create(NoteDraft("Belanja", "- [ ] beli kopi")) as com.aidriy.app.domain.DataResult.Success).value.id
        }
        composeRule.onNodeWithTag("nav_catatan").performClick()
        waitForTag("screen_catatan")
        waitForText("Belanja")
        composeRule.onNodeWithTag("notes_new").assertIsDisplayed()

        composeRule.onNodeWithText("Belanja").performClick()
        waitForTag("task_0")
        composeRule.onNodeWithTag("task_0").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking { container.noteRepository.getById(id)?.content } == "- [x] beli kopi"
        }
    }

    @Test
    fun drawer_opens_the_catatan_tab() {
        runBlocking { container.noteRepository.create(NoteDraft("Daftar belanja", "telur")) }
        openDrawerItem("drawer_notes")
        waitForTag("screen_catatan")
        waitForText("Daftar belanja")
    }

    @Test
    fun editor_tools_open_their_sheets() {
        composeRule.onNodeWithTag("quick_notes").performClick()
        waitForTag("editor_toolbar")
        composeRule.onNodeWithTag("tool_image").performClick()
        waitForTag("sheet_image")
        composeRule.onNodeWithText("Pilih dari Galeri").assertIsDisplayed()
        // A real Back key reaches the sheet's window first (the activity dispatcher would skip it).
        androidx.test.espresso.Espresso.pressBackUnconditionally()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("sheet_image")).fetchSemanticsNodes().isEmpty() }

        composeRule.onNodeWithTag("tool_more").performClick()
        waitForTag("sheet_more")
        composeRule.onNodeWithTag("more_divider").performClick()
        composeRule.onNodeWithTag("editor_content").assertTextContains("---", substring = true)
    }

    @Test
    fun capture_an_idea_and_change_its_status() {
        openDrawerItem("drawer_ideas")
        waitForTag("screen_ideas")
        composeRule.onNodeWithTag("idea_capture_input").performTextInput("Ide uji")
        composeRule.onNodeWithTag("idea_capture_send").performClick()
        waitForTag("idea_item")

        composeRule.onNodeWithText("Ide uji").performClick()
        composeRule.onNodeWithTag("editor_status_IN_PROGRESS").performScrollTo().performClick()
        composeRule.onNodeWithTag("editor_save").performClick()
        waitForText("Tersimpan")
        composeRule.onNodeWithTag("editor_back").performClick()

        waitForTag("screen_ideas")
        composeRule.onNodeWithTag("status_IN_PROGRESS").performScrollTo().performClick()
        waitForText("Ide uji")
        composeRule.onNodeWithTag("status_COMPLETED").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Ide uji").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun write_todays_journal_then_reopen_the_same_entry() {
        openDrawerItem("drawer_journal")
        waitForTag("screen_journal")
        composeRule.onNodeWithTag("journal_today").performClick()
        composeRule.onNodeWithTag("editor_content").performTextInput("Hari ini menyenangkan")
        composeRule.onNodeWithTag("editor_save").performClick()
        waitForText("Tersimpan")
        composeRule.onNodeWithTag("editor_back").performClick()

        waitForTag("journal_item")
        composeRule.onAllNodesWithText("Hari ini menyenangkan", substring = true).onFirst().assertIsDisplayed()

        // Opening today again loads the existing entry instead of starting a duplicate.
        composeRule.onNodeWithTag("journal_today").performClick()
        composeRule.onNodeWithTag("editor_content").assertTextContains("Hari ini menyenangkan")
        composeRule.onNodeWithTag("editor_delete").assertIsDisplayed()
    }

    @Test
    fun global_search_finds_stored_content() {
        runBlocking {
            container.noteRepository.create(NoteDraft("Resep kopi susu", "gula aren"))
            container.noteRepository.create(NoteDraft("Olahraga", "lari"))
        }
        composeRule.onNodeWithTag("home_search").performClick()
        composeRule.onNodeWithTag("search_input").performTextInput("gula AREN")
        waitForTag("search_result_note")
        composeRule.onNodeWithText("Resep kopi susu").assertIsDisplayed()
    }
}
