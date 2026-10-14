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
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.ui.ai.AiActions
import com.aidriy.app.ui.ai.AiChatRoute
import com.aidriy.app.ui.ai.AiHistoryScreen
import com.aidriy.app.ui.ai.AiInbox
import com.aidriy.app.ui.ai.AiRequest
import com.aidriy.app.ui.ai.AiToolsScreen
import com.aidriy.app.ui.files.FileViewerRoute
import com.aidriy.app.ui.files.FilesActions
import com.aidriy.app.ui.files.FilesRoute
import com.aidriy.app.ui.home.HomeActions
import com.aidriy.app.ui.home.HomeRoute
import com.aidriy.app.ui.ideas.IdeaEditorRoute
import com.aidriy.app.ui.ideas.IdeasRoute
import com.aidriy.app.ui.journal.JournalEditorRoute
import com.aidriy.app.ui.journal.JournalRoute
import com.aidriy.app.ui.notes.NoteEditorRoute
import com.aidriy.app.ui.notes.NotesRoute
import com.aidriy.app.ui.notes.templates.TemplatesScreen
import com.aidriy.app.ui.scan.ScanRoute
import com.aidriy.app.ui.search.SearchRoute
import com.aidriy.app.ui.settings.AboutScreen
import com.aidriy.app.ui.settings.AiSettingsScreen
import com.aidriy.app.ui.settings.AppearanceSettingsScreen
import com.aidriy.app.ui.settings.FileSettingsScreen
import com.aidriy.app.ui.settings.NoteSettingsScreen
import com.aidriy.app.ui.settings.NotificationSettingsScreen
import com.aidriy.app.ui.settings.ProfileSettingsScreen
import com.aidriy.app.ui.settings.SecuritySettingsScreen
import com.aidriy.app.ui.settings.SettingsActions
import com.aidriy.app.ui.settings.SettingsHomeScreen
import java.time.LocalDate

private const val FADE_MILLIS = 180

@Composable
fun AidriyNavHost(
    navController: NavHostController,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = navController
    val back: () -> Unit = { nav.popBackStack() }
    val openNote: (String) -> Unit = { nav.navigate(Routes.noteEditor(it)) }
    val createNote: () -> Unit = { nav.navigate(Routes.noteEditor()) }
    val createNoteFromText: (String, String) -> Unit = { title, content ->
        nav.navigate(Routes.noteEditor(prefill = PrefillStore.put(title, content)))
    }
    val openIdea: (String) -> Unit = { nav.navigate(Routes.ideaEditor(it)) }
    val openJournal: (LocalDate) -> Unit = { nav.navigate(Routes.journalEditor(it)) }
    val openFile: (String) -> Unit = { nav.navigate(Routes.fileViewer(it)) }
    val openSource: (SourceRef) -> Unit = { ref ->
        when (ref.type) {
            SourceType.NOTE -> openNote(ref.id)
            SourceType.IDEA -> openIdea(ref.id)
            SourceType.JOURNAL -> runCatching { LocalDate.parse(ref.id) }.getOrNull()?.let(openJournal)
            SourceType.FILE -> openFile(ref.id)
        }
    }
    val goToAi: (AiRequest) -> Unit = { request ->
        AiInbox.post(request)
        nav.navigateToTopLevel(TopLevelDestination.AI)
    }

    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(FADE_MILLIS)) },
        exitTransition = { fadeOut(tween(FADE_MILLIS)) },
    ) {
        // ------------------------------------------------------------------ tabs
        composable(TopLevelDestination.HOME.route) {
            HomeRoute(
                actions = HomeActions(
                    openDrawer = onOpenDrawer,
                    openProfile = { nav.navigate(Routes.SETTINGS_PROFILE) },
                    openSearch = { nav.navigate(Routes.SEARCH) },
                    openAllNotes = { nav.navigateToTopLevel(TopLevelDestination.NOTES) },
                    createNote = createNote,
                    openNote = openNote,
                    openScan = { nav.navigate(Routes.SCAN) },
                    createNoteFromText = createNoteFromText,
                    openTemplates = { nav.navigate(Routes.TEMPLATES) },
                    openIdeas = { nav.navigate(Routes.IDEAS) },
                    openJournal = openJournal,
                ),
            )
        }
        composable(TopLevelDestination.NOTES.route) {
            NotesRoute(onBack = null, onOpenNote = openNote, onCreateNote = createNote)
        }
        composable(TopLevelDestination.AI.route) {
            AiChatRoute(
                actions = AiActions(
                    openTools = { nav.navigate(Routes.AI_TOOLS) },
                    openHistory = { nav.navigate(Routes.AI_HISTORY) },
                    openAiSettings = { nav.navigate(Routes.SETTINGS_AI) },
                    openSource = openSource,
                    saveAsNote = createNoteFromText,
                ),
            )
        }
        composable(TopLevelDestination.FILES.route) {
            FilesRoute(
                actions = FilesActions(
                    openNotes = { nav.navigateToTopLevel(TopLevelDestination.NOTES) },
                    openArchive = { nav.navigate(Routes.notesList(NoteScope.ARCHIVED)) },
                    openCategory = { nav.navigate(Routes.filesFolder(kind = it)) },
                    openFolder = { nav.navigate(Routes.filesFolder(folderId = it)) },
                    openFile = openFile,
                ),
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsHomeScreen(
                actions = SettingsActions(
                    openProfile = { nav.navigate(Routes.SETTINGS_PROFILE) },
                    openAi = { nav.navigate(Routes.SETTINGS_AI) },
                    openAppearance = { nav.navigate(Routes.SETTINGS_APPEARANCE) },
                    openNotes = { nav.navigate(Routes.SETTINGS_NOTES) },
                    openFiles = { nav.navigate(Routes.SETTINGS_FILES) },
                    openSecurity = { nav.navigate(Routes.SETTINGS_SECURITY) },
                    openNotifications = { nav.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                    openAbout = { nav.navigate(Routes.SETTINGS_ABOUT) },
                    openSearch = { nav.navigate(Routes.SEARCH) },
                ),
            )
        }

        // ------------------------------------------------------------------ notes, ideas, journal
        composable(
            route = Routes.NOTES_LIST,
            arguments = listOf(navArgument(Routes.ARG_SCOPE) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            NotesRoute(onBack = dropUnlessResumed { back() }, onOpenNote = openNote, onCreateNote = createNote)
        }
        composable(
            route = Routes.NOTE_EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_NOTE_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_TEMPLATE) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_PREFILL) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            // dropUnlessResumed prevents a double pop if close is triggered twice.
            NoteEditorRoute(
                onClose = dropUnlessResumed { back() },
                onOpenFile = openFile,
                onOpenAiSettings = { nav.navigate(Routes.SETTINGS_AI) },
            )
        }
        composable(Routes.IDEAS) {
            IdeasRoute(onBack = dropUnlessResumed { back() }, onOpenIdea = openIdea)
        }
        composable(
            route = Routes.IDEA_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_IDEA_ID) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            IdeaEditorRoute(onClose = dropUnlessResumed { back() }, onOpenNote = openNote)
        }
        composable(Routes.JOURNAL) {
            JournalRoute(onBack = dropUnlessResumed { back() }, onOpenDate = openJournal)
        }
        composable(
            route = Routes.JOURNAL_EDITOR,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.StringType }),
        ) {
            JournalEditorRoute(onClose = dropUnlessResumed { back() })
        }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(
                onBack = dropUnlessResumed { back() },
                onUseTemplate = { id ->
                    nav.navigate(Routes.noteEditor(template = id)) {
                        popUpTo(Routes.TEMPLATES) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SCAN) {
            ScanRoute(
                onBack = dropUnlessResumed { back() },
                onSaveAsNote = { text ->
                    nav.navigate(Routes.noteEditor(prefill = PrefillStore.put("", text))) {
                        popUpTo(Routes.SCAN) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SEARCH) {
            SearchRoute(
                onBack = dropUnlessResumed { back() },
                onOpenNote = openNote,
                onOpenIdea = openIdea,
                onOpenJournal = openJournal,
            )
        }

        // ------------------------------------------------------------------ AI
        composable(Routes.AI_TOOLS) {
            AiToolsScreen(
                onBack = dropUnlessResumed { back() },
                onPickSuggestion = { id ->
                    AiInbox.post(AiRequest(suggestionId = id))
                    back()
                },
                onOpenAiSettings = { nav.navigate(Routes.SETTINGS_AI) },
            )
        }
        composable(Routes.AI_HISTORY) {
            AiHistoryScreen(
                onBack = dropUnlessResumed { back() },
                onOpen = { id ->
                    AiInbox.post(AiRequest(conversationId = id))
                    back()
                },
            )
        }

        // ------------------------------------------------------------------ files
        composable(
            route = Routes.FILES_FOLDER,
            arguments = listOf(
                navArgument(Routes.ARG_FOLDER_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(Routes.ARG_KIND) { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            FilesRoute(
                actions = FilesActions(
                    openNotes = { nav.navigateToTopLevel(TopLevelDestination.NOTES) },
                    openArchive = { nav.navigate(Routes.notesList(NoteScope.ARCHIVED)) },
                    openCategory = { nav.navigate(Routes.filesFolder(kind = it)) },
                    openFolder = { nav.navigate(Routes.filesFolder(folderId = it)) },
                    openFile = openFile,
                    back = dropUnlessResumed { back() },
                ),
            )
        }
        composable(
            route = Routes.FILE_VIEWER,
            arguments = listOf(navArgument(Routes.ARG_FILE_ID) { type = NavType.StringType }),
        ) { entry ->
            FileViewerRoute(
                fileId = entry.arguments?.getString(Routes.ARG_FILE_ID).orEmpty(),
                onBack = dropUnlessResumed { back() },
                onAskAi = { ref -> goToAi(AiRequest(suggestionId = "analyze_doc", source = ref)) },
            )
        }

        // ------------------------------------------------------------------ settings
        composable(Routes.SETTINGS_PROFILE) { ProfileSettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_AI) { AiSettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_APPEARANCE) { AppearanceSettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_NOTES) {
            NoteSettingsScreen(onBack = dropUnlessResumed { back() }, onOpenTemplates = { nav.navigate(Routes.TEMPLATES) })
        }
        composable(Routes.SETTINGS_FILES) { FileSettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_SECURITY) { SecuritySettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationSettingsScreen(onBack = dropUnlessResumed { back() }) }
        composable(Routes.SETTINGS_ABOUT) { AboutScreen(onBack = dropUnlessResumed { back() }) }
    }
}

/**
 * Switches tabs the standard Android way: one copy of each tab on the back stack,
 * each tab's scroll and state saved and restored, and Back from any tab returns to Catatan.
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
