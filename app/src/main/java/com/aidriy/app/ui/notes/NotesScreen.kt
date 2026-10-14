package com.aidriy.app.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.model.Tag
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
import com.aidriy.app.ui.components.TagFilterRow
import com.aidriy.app.ui.home.NoteRow
import com.aidriy.app.ui.theme.Spacing

@Composable
fun NotesRoute(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val options by viewModel.listOptions.collectAsStateWithLifecycle()
    val tags by viewModel.availableTags.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }

    NotesScreen(
        uiState = uiState,
        query = viewModel.query,
        options = options,
        tags = tags,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onScopeChange = viewModel::setScope,
        onSortChange = viewModel::setSort,
        onTagChange = viewModel::setTag,
        onRetry = viewModel::retry,
        onToggleFavorite = viewModel::toggleFavorite,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onDelete = viewModel::delete,
        onOpenNote = onOpenNote,
        onCreateNote = onCreateNote,
    )
}

@Composable
fun NotesScreen(
    uiState: ListUiState<Note>,
    query: String,
    options: NoteListOptions,
    tags: List<Tag>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScopeChange: (NoteScope) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onTagChange: (String?) -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onArchive: (Note) -> Unit,
    onRestore: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_notes"),
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
                    title = stringResource(R.string.notes_all_title),
                    navigation = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            }
            item {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.notes_search_hint),
                    modifier = Modifier.testTag("notes_search"),
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceChipRow(
                        options = listOf(
                            ChipOption(NoteScope.ACTIVE, stringResource(R.string.notes_scope_active), "scope_active"),
                            ChipOption(NoteScope.FAVORITES, stringResource(R.string.notes_scope_favorites), "scope_favorites"),
                            ChipOption(NoteScope.ARCHIVED, stringResource(R.string.notes_scope_archived), "scope_archived"),
                        ),
                        selected = options.scope,
                        onSelect = onScopeChange,
                        modifier = Modifier.weight(1f),
                    )
                    SortMenu(sort = options.sort, onSortChange = onSortChange)
                }
            }
            if (tags.isNotEmpty()) {
                item { TagFilterRow(tags = tags, selectedTagId = options.tagId, onSelect = onTagChange) }
            }

            when (uiState) {
                ListUiState.Loading -> item { LoadingState() }
                is ListUiState.Error -> item { ErrorState(uiState.message.asString(), onRetry) }
                is ListUiState.Empty -> item { NotesEmptyState(options.scope, uiState.isFiltered) }
                is ListUiState.Success -> items(uiState.items, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        onOpen = { onOpenNote(note.id) },
                        onToggleFavorite = { onToggleFavorite(note) },
                        onArchive = if (note.isArchived) null else ({ onArchive(note) }),
                        onRestore = if (note.isArchived) ({ onRestore(note) }) else null,
                        onDelete = { pendingDelete = note },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateNote,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = androidx.compose.foundation.shape.CircleShape,
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
                onDelete(note)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun NotesEmptyState(scope: NoteScope, isFiltered: Boolean) {
    val (title, body) = when {
        isFiltered && scope == NoteScope.ARCHIVED -> R.string.notes_archive_empty_title to R.string.notes_archive_empty_body
        isFiltered && scope == NoteScope.FAVORITES -> R.string.notes_favorites_empty_title to R.string.notes_favorites_empty_body
        isFiltered -> R.string.no_results_title to R.string.no_results_body
        else -> R.string.notes_empty_title to R.string.notes_empty_body
    }
    EmptyState(
        icon = when {
            scope == NoteScope.ARCHIVED -> Icons.Outlined.Inventory2
            isFiltered -> Icons.Outlined.SearchOff
            else -> Icons.Outlined.Description
        },
        title = stringResource(title),
        body = stringResource(body),
    )
}
