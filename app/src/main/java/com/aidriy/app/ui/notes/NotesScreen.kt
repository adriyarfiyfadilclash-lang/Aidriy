package com.aidriy.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.domain.model.Folder
import com.aidriy.app.domain.model.Note
import com.aidriy.app.ui.common.ListUiState
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.components.SortMenu
import com.aidriy.app.ui.files.NameDialog
import com.aidriy.app.ui.home.NoteRow
import com.aidriy.app.ui.theme.Spacing

/**
 * The Catatan list. As a bottom-bar tab ([onBack] null) it has no back arrow; opened from the
 * drawer (Disematkan, Arsip) it does.
 */
@Composable
fun NotesRoute(
    onBack: (() -> Unit)?,
    onOpenNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val options by viewModel.listOptions.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()
    val folders by viewModel.folderList.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    var moving by remember { mutableStateOf<Note?>(null) }
    // New-folder dialog; when opened from "Pindahkan", the note moves into the new folder.
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderNote by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (onBack == null) "screen_catatan" else "screen_notes"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                bottom = Spacing.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                AppHeaderBar(
                    title = stringResource(R.string.nav_notes),
                    navigation = if (onBack != null) {
                        {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        }
                    } else {
                        null
                    },
                    actions = { SortMenu(sort = options.sort, onSortChange = viewModel::setSort) },
                )
            }
            item {
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.notes_search_hint),
                    modifier = Modifier.testTag("notes_search"),
                )
            }
            item {
                ChoiceChipRow(
                    options = listOf(
                        ChipOption(NoteListMode.ALL, stringResource(R.string.filter_all), "mode_all"),
                        ChipOption(NoteListMode.PINNED, stringResource(R.string.notes_pinned), "mode_pinned"),
                        ChipOption(NoteListMode.FOLDER, stringResource(R.string.files_folder), "mode_folder"),
                        ChipOption(NoteListMode.TAG, stringResource(R.string.tags_label), "mode_tag"),
                        ChipOption(NoteListMode.ARCHIVED, stringResource(R.string.drawer_archive), "mode_archived"),
                    ),
                    selected = options.mode,
                    onSelect = viewModel::setMode,
                )
            }
            if (options.mode == NoteListMode.FOLDER) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (folders.isNotEmpty()) {
                            ChoiceChipRow(
                                options = folders.map { ChipOption<String?>(it.id, it.name, "folder_${it.name}") },
                                selected = options.folderId,
                                onSelect = viewModel::setFolder,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (viewModel.canCreateFolders) {
                            AssistChip(
                                onClick = {
                                    newFolderNote = null
                                    showNewFolder = true
                                },
                                label = { Text(stringResource(R.string.files_new_folder)) },
                                leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
            }
            if (options.mode == NoteListMode.TAG && tags.isNotEmpty()) {
                item {
                    ChoiceChipRow(
                        options = tags.map { ChipOption<String?>(it.id, "#${it.name}", "tag_${it.name}") },
                        selected = options.tagId,
                        onSelect = viewModel::setTag,
                    )
                }
            }

            when {
                options.mode == NoteListMode.FOLDER && folders.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = stringResource(R.string.notes_no_folders_title),
                        body = stringResource(R.string.notes_no_folders_body),
                    )
                }
                options.mode == NoteListMode.TAG && tags.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.Sell,
                        title = stringResource(R.string.notes_no_tags_title),
                        body = stringResource(R.string.notes_no_tags_body),
                    )
                }
                else -> when (val state = uiState) {
                    ListUiState.Loading -> item { LoadingState() }
                    is ListUiState.Error -> item { ErrorState(state.message.asString(), viewModel::retry) }
                    is ListUiState.Empty -> item { NotesEmptyState(options.mode, state.isFiltered) }
                    is ListUiState.Success -> items(state.items, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(note) },
                            onArchive = if (note.isArchived) null else ({ viewModel.archive(note) }),
                            onRestore = if (note.isArchived) ({ viewModel.restore(note) }) else null,
                            onDelete = { pendingDelete = note },
                            onMoveToFolder = { moving = note },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateNote,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
                .testTag("notes_new"),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.notes_new))
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    pendingDelete?.let { note ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.note_delete_title),
            message = stringResource(R.string.note_delete_body),
            onConfirm = {
                pendingDelete = null
                viewModel.delete(note)
            },
            onDismiss = { pendingDelete = null },
        )
    }
    moving?.let { note ->
        MoveToFolderDialog(
            folders = folders,
            current = note.folderId,
            canCreate = viewModel.canCreateFolders,
            onPick = { folderId ->
                moving = null
                viewModel.moveToFolder(note, folderId)
            },
            onCreate = {
                moving = null
                newFolderNote = note
                showNewFolder = true
            },
            onDismiss = { moving = null },
        )
    }
    if (showNewFolder) {
        NameDialog(
            title = stringResource(R.string.files_new_folder),
            initial = "",
            onConfirm = { name ->
                showNewFolder = false
                viewModel.createFolderAndMove(name, newFolderNote)
            },
            onDismiss = { showNewFolder = false },
        )
    }
}

@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    current: String?,
    canCreate: Boolean,
    onPick: (String?) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_move_folder)) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.files_no_folder)) },
                    leadingContent = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                    trailingContent = { if (current == null) Text("✓", color = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth().clickableRow { onPick(null) },
                )
                folders.forEach { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name) },
                        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        trailingContent = { if (current == folder.id) Text("✓", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth().clickableRow { onPick(folder.id) },
                    )
                }
                if (canCreate) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.files_new_folder), color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.fillMaxWidth().clickableRow(onCreate),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun NotesEmptyState(mode: NoteListMode, isFiltered: Boolean) {
    val (title, body) = when {
        mode == NoteListMode.ARCHIVED -> R.string.notes_archive_empty_title to R.string.notes_archive_empty_body
        mode == NoteListMode.PINNED -> R.string.notes_pinned_empty_title to R.string.notes_pinned_empty_body
        mode == NoteListMode.FOLDER -> R.string.notes_folder_empty_title to R.string.notes_folder_empty_body
        isFiltered -> R.string.no_results_title to R.string.no_results_body
        else -> R.string.notes_empty_title to R.string.notes_empty_body
    }
    EmptyState(
        icon = when (mode) {
            NoteListMode.ARCHIVED -> Icons.Outlined.Inventory2
            NoteListMode.PINNED -> Icons.Outlined.PushPin
            NoteListMode.FOLDER -> Icons.Outlined.Folder
            else -> if (isFiltered) Icons.Outlined.SearchOff else Icons.Outlined.Description
        },
        title = stringResource(title),
        body = stringResource(body),
    )
}
