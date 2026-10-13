package com.aidriy.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aidriy.app.ui.home.HomeActions
import com.aidriy.app.ui.home.HomeRoute
import com.aidriy.app.ui.ideas.IdeaEditorRoute
import com.aidriy.app.ui.ideas.IdeasRoute
import com.aidriy.app.ui.insight.InsightScreen
import com.aidriy.app.ui.journal.JournalEditorRoute
import com.aidriy.app.ui.journal.JournalRoute
import com.aidriy.app.ui.notes.NoteEditorRoute
import com.aidriy.app.ui.notes.NotesRoute
import com.aidriy.app.ui.search.SearchRoute
import java.time.LocalDate

private const val FADE_MILLIS = 180

@Composable
fun AidriyNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val openNote: (String) -> Unit = { navController.navigate(Routes.noteEditor(it)) }
    val createNote: () -> Unit = { navController.navigate(Routes.noteEditor()) }
    val openIdea: (String) -> Unit = { navController.navigate(Routes.ideaEditor(it)) }
    val createIdea: () -> Unit = { navController.navigate(Routes.ideaEditor()) }
    val openJournal: (LocalDate) -> Unit = { navController.navigate(Routes.journalEditor(it)) }

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(FADE_MILLIS)) },
        exitTransition = { fadeOut(tween(FADE_MILLIS)) },
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeRoute(
                actions = HomeActions(
                    openDestination = navController::navigateToTopLevel,
                    openSearch = { navController.navigate(Routes.SEARCH) },
                    createNote = createNote,
                    openNote = openNote,
                    createIdea = createIdea,
                    openIdea = openIdea,
                    openJournal = openJournal,
                ),
            )
        }
        composable(TopLevelDestination.NOTES.route) {
            NotesRoute(onOpenNote = openNote, onCreateNote = createNote)
        }
        composable(TopLevelDestination.IDEAS.route) {
            IdeasRoute(onOpenIdea = openIdea)
        }
        composable(TopLevelDestination.JOURNAL.route) {
            JournalRoute(onOpenDate = openJournal)
        }
        composable(TopLevelDestination.INSIGHT.route) {
            InsightScreen()
        }

        composable(
            route = Routes.NOTE_EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_NOTE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // dropUnlessResumed prevents a double pop if close is triggered twice.
            NoteEditorRoute(onClose = dropUnlessResumed { navController.popBackStack() })
        }
        composable(
            route = Routes.IDEA_EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_IDEA_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            IdeaEditorRoute(
                onClose = dropUnlessResumed { navController.popBackStack() },
                onOpenNote = openNote,
            )
        }
        composable(
            route = Routes.JOURNAL_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.StringType }),
        ) {
            JournalEditorRoute(onClose = dropUnlessResumed { navController.popBackStack() })
        }
        composable(Routes.SEARCH) {
            SearchRoute(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenNote = openNote,
                onOpenIdea = openIdea,
                onOpenJournal = openJournal,
            )
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
