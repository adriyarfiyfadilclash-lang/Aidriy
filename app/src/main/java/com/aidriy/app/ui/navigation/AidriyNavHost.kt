package com.aidriy.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aidriy.app.ui.home.HomeRoute
import com.aidriy.app.ui.ideas.IdeasScreen
import com.aidriy.app.ui.insight.InsightScreen
import com.aidriy.app.ui.journal.JournalRoute
import com.aidriy.app.ui.notes.NotesScreen

private const val FADE_MILLIS = 180

@Composable
fun AidriyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(FADE_MILLIS)) },
        exitTransition = { fadeOut(tween(FADE_MILLIS)) },
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeRoute(onOpenDestination = navController::navigateToTopLevel)
        }
        composable(TopLevelDestination.NOTES.route) {
            NotesScreen()
        }
        composable(TopLevelDestination.IDEAS.route) {
            IdeasScreen()
        }
        composable(TopLevelDestination.JOURNAL.route) {
            JournalRoute()
        }
        composable(TopLevelDestination.INSIGHT.route) {
            InsightScreen()
        }
    }
}

/**
 * Switches tabs the standard Android way: one copy of each tab on the back stack,
 * each tab's scroll and state saved and restored, and Back from any tab returns to Beranda.
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
