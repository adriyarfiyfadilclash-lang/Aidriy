package com.aidriy.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.MainActivity
import com.aidriy.app.ui.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_starts_on_beranda() {
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }

    @Test
    fun every_bottom_navigation_item_opens_its_screen() {
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.route}").performClick()
            composeRule.onNodeWithTag("screen_${destination.route}").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_${destination.route}").assertIsSelected()
        }
    }

    @Test
    fun home_quick_access_opens_matching_tab() {
        composeRule.onNodeWithTag("quick_ideas").performClick()
        composeRule.onNodeWithTag("screen_ideas").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_ideas").assertIsSelected()
    }

    @Test
    fun home_insight_entry_opens_insight_tab() {
        composeRule.onNodeWithTag("home_insight_entry").performClick()
        composeRule.onNodeWithTag("screen_insight").assertIsDisplayed()
    }

    @Test
    fun back_from_a_tab_returns_to_beranda() {
        composeRule.onNodeWithTag("nav_journal").performClick()
        composeRule.onNodeWithTag("screen_journal").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").assertIsSelected()
    }
}
