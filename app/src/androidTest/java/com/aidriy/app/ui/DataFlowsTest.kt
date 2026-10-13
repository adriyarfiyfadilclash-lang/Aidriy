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

    @Test
    fun create_edit_reopen_and_delete_a_note() {
        composeRule.onNodeWithTag("nav_notes").performClick()
        composeRule.onNodeWithTag("notes_new").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput("Catatan uji")
        composeRule.onNodeWithTag("editor_content").performTextInput("Isi pertama")
        composeRule.onNodeWithTag("editor_save").performClick()
        waitForText("Tersimpan")
        composeRule.onNodeWithTag("editor_back").performClick()

        waitForText("Catatan uji")
        composeRule.onNodeWithText("Catatan uji").performClick()
        composeRule.onNodeWithTag("editor_title").assertTextContains("Catatan uji")
        composeRule.onNodeWithTag("editor_title").performTextClearance()
        composeRule.onNodeWithTag("editor_title").performTextInput("Catatan diedit")
        composeRule.onNodeWithTag("editor_save").performClick()
        waitForText("Tersimpan")
        composeRule.onNodeWithTag("editor_back").performClick()

        waitForText("Catatan diedit")
        composeRule.onNodeWithText("Catatan diedit").performClick()
        composeRule.onNodeWithTag("editor_menu").performClick()
        composeRule.onNodeWithTag("menu_delete").performClick()
        composeRule.onNodeWithText("Hapus catatan?").assertIsDisplayed()
        composeRule.onNodeWithText("Hapus").performClick()

        waitForTag("screen_notes")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Catatan diedit").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun leaving_with_unsaved_changes_asks_first_and_discard_saves_nothing() {
        composeRule.onNodeWithTag("nav_notes").performClick()
        composeRule.onNodeWithTag("notes_new").performClick()
        composeRule.onNodeWithTag("editor_content").performTextInput("draf sementara")
        composeRule.onNodeWithTag("editor_back").performClick()

        composeRule.onNodeWithText("Simpan perubahan?").assertIsDisplayed()
        composeRule.onNodeWithText("Buang").performClick()

        waitForTag("screen_notes")
        composeRule.onNodeWithText("Belum ada catatan").assertIsDisplayed()
    }

    @Test
    fun capture_an_idea_and_change_its_status() {
        composeRule.onNodeWithTag("nav_ideas").performClick()
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
        composeRule.onNodeWithTag("nav_journal").performClick()
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
