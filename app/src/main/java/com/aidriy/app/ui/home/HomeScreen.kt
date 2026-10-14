package com.aidriy.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.domain.model.Note
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.common.rememberSpeechToText
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.ProfileAvatar
import com.aidriy.app.ui.components.QuickActionCard
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.SectionTitle
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Navigation callbacks from the Catatan home screen. */
data class HomeActions(
    val openDrawer: () -> Unit,
    val openProfile: () -> Unit,
    val openSearch: () -> Unit,
    val openAllNotes: () -> Unit,
    val createNote: () -> Unit,
    val openNote: (String) -> Unit,
    val openScan: () -> Unit,
    val createNoteFromText: (title: String, content: String) -> Unit,
    val openTemplates: () -> Unit,
    val openIdeas: () -> Unit,
    val openJournal: (LocalDate) -> Unit,
)

/** Stateful entry point: wires the ViewModel to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    val recordVoice = rememberSpeechToText(
        onResult = { text -> actions.createNoteFromText(context.getString(R.string.voice_note_title), text) },
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.speech_unavailable)) } },
    )

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            uiState = uiState,
            content = content,
            profile = profile,
            actions = actions,
            onRecordVoice = recordVoice,
            onToggleFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::archive,
            onDelete = viewModel::delete,
        )
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    content: HomeContentState,
    profile: Profile,
    actions: HomeActions,
    onRecordVoice: () -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onArchive: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_catatan"),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.openDrawer, modifier = Modifier.testTag("home_menu")) {
                    Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.drawer_open))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = actions.openProfile) {
                    ProfileAvatar(name = profile.name, photoPath = profile.photoPath, size = 34.dp)
                }
            }
        }
        item {
            val greeting = stringResource(uiState.greeting.labelRes())
            Text(
                text = if (profile.name.isBlank()) "$greeting 👋" else "$greeting, ${profile.name} 👋",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = actions.openSearch,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("home_search"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            stringResource(R.string.home_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Surface(
                    onClick = actions.openAllNotes,
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.home_filter))
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    QuickActionCard(
                        Icons.Outlined.EditNote, AidriyColors.accent(AccentKey.BLUE),
                        stringResource(R.string.qa_new_note), stringResource(R.string.qa_new_note_sub),
                        actions.createNote, Modifier.weight(1f).testTag("quick_notes"),
                    )
                    QuickActionCard(
                        Icons.Outlined.DocumentScanner, AidriyColors.accent(AccentKey.TEAL),
                        stringResource(R.string.qa_scan), stringResource(R.string.qa_scan_sub),
                        actions.openScan, Modifier.weight(1f).testTag("quick_scan"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    QuickActionCard(
                        Icons.Outlined.Mic, AidriyColors.accent(AccentKey.RED),
                        stringResource(R.string.qa_voice), stringResource(R.string.qa_voice_sub),
                        onRecordVoice, Modifier.weight(1f).testTag("quick_voice"),
                    )
                    QuickActionCard(
                        Icons.Outlined.Dashboard, AidriyColors.accent(AccentKey.GREEN),
                        stringResource(R.string.qa_template), stringResource(R.string.qa_template_sub),
                        actions.openTemplates, Modifier.weight(1f).testTag("quick_templates"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    QuickActionCard(
                        Icons.Outlined.Lightbulb, AidriyColors.accent(AccentKey.YELLOW),
                        stringResource(R.string.qa_ideas), stringResource(R.string.qa_ideas_sub),
                        actions.openIdeas, Modifier.weight(1f).testTag("quick_ideas"),
                    )
                    QuickActionCard(
                        Icons.Outlined.Book, AidriyColors.accent(AccentKey.PURPLE),
                        stringResource(R.string.qa_journal), stringResource(R.string.qa_journal_sub),
                        { actions.openJournal(uiState.today) }, Modifier.weight(1f).testTag("quick_journal"),
                    )
                }
            }
        }
        item {
            SectionTitle(title = stringResource(R.string.home_recent_notes)) {
                TextButton(onClick = actions.openAllNotes) { Text(stringResource(R.string.home_see_all)) }
            }
        }
        when (content) {
            HomeContentState.Loading -> item { LoadingState() }
            HomeContentState.Error -> item { ErrorState(stringResource(R.string.error_load), onRetry = null) }
            is HomeContentState.Loaded -> if (content.notes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                    )
                }
            } else {
                items(content.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpen = { actions.openNote(note.id) },
                        onToggleFavorite = { onToggleFavorite(note) },
                        onArchive = { onArchive(note) },
                        onDelete = { pendingDelete = note },
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
        }
    }

    pendingDelete?.let { note ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.note_delete_title),
            message = stringResource(R.string.note_delete_body),
            onConfirm = {
                pendingDelete = null
                onDelete(note)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Note list row used on Catatan and in the full notes list: colored tile, preview, time, menu. */
@Composable
fun NoteRow(
    note: Note,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onRestore: (() -> Unit)? = null,
) {
    var menu by rememberSaveable(note.id) { mutableStateOf(false) }
    RecordCard(
        title = note.title,
        preview = note.content,
        meta = formatRelative(note.updatedAt),
        tags = note.tags,
        onClick = onOpen,
        modifier = modifier.testTag("note_item"),
        leading = { AccentTile(Icons.Outlined.Description, AidriyColors.accentFor(note.id), size = 40.dp) },
        trailing = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isFavorite) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.note_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (note.isFavorite) R.string.note_favorite_remove else R.string.note_favorite_add)) },
                        onClick = {
                            menu = false
                            onToggleFavorite()
                        },
                    )
                    if (onArchive != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_archive)) },
                            onClick = {
                                menu = false
                                onArchive()
                            },
                        )
                    }
                    if (onRestore != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.note_unarchive)) },
                            onClick = {
                                menu = false
                                onRestore()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        },
    )
}

@StringRes
private fun Greeting.labelRes(): Int = when (this) {
    Greeting.PAGI -> R.string.greeting_pagi
    Greeting.SIANG -> R.string.greeting_siang
    Greeting.SORE -> R.string.greeting_sore
    Greeting.MALAM -> R.string.greeting_malam
}
