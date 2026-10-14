package com.aidriy.app.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.MainActivity
import com.aidriy.app.ui.navigation.TopLevelDestination
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) =
        composeRule.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis = 5_000)

    /** The splash shows briefly on launch. */
    @Before
    fun waitForHome() = waitForTag("screen_beranda")

    private fun pressBack() = composeRule.runOnUiThread {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun app_starts_on_beranda() {
        composeRule.onNodeWithTag("screen_beranda").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_beranda").assertIsSelected()
    }

    @Test
    fun every_bottom_navigation_item_opens_its_screen() {
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.route}").performClick()
            waitForTag("screen_${destination.route}")
            composeRule.onNodeWithTag("screen_${destination.route}").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_${destination.route}").assertIsSelected()
        }
    }

    @Test
    fun quick_actions_open_their_screens_and_hide_the_bottom_bar() {
        composeRule.onNodeWithTag("quick_notes").performClick()
        waitForTag("screen_note_editor")
        composeRule.onNodeWithTag("nav_beranda").assertDoesNotExist()
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("quick_templates").performClick()
        waitForTag("screen_templates")
        composeRule.onNodeWithTag("header_back").performClick()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("quick_scan").performClick()
        waitForTag("screen_scan")
        pressBack()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("quick_ideas").performClick()
        waitForTag("screen_ideas")
        composeRule.onNodeWithTag("header_back").performClick()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("quick_journal").performClick()
        waitForTag("screen_journal_editor")
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_beranda")
    }

    @Test
    fun drawer_opens_journal_and_search() {
        composeRule.onNodeWithTag("home_menu").performClick()
        waitForTag("drawer_journal")
        composeRule.onNodeWithTag("drawer_journal").performClick()
        waitForTag("screen_journal")
        composeRule.onNodeWithTag("header_back").performClick()
        waitForTag("screen_beranda")

        composeRule.onNodeWithTag("home_search").performClick()
        waitForTag("screen_search")
        composeRule.onNodeWithTag("editor_back").performClick()
        waitForTag("screen_beranda")
    }

    @Test
    fun back_from_a_tab_returns_to_beranda() {
        composeRule.onNodeWithTag("nav_berkas").performClick()
        waitForTag("screen_berkas")

        pressBack()

        waitForTag("screen_beranda")
        composeRule.onNodeWithTag("nav_beranda").assertIsSelected()
    }
}
