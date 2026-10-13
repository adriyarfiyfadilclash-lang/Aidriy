package com.aidriy.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EditorBodyField
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.EditorTitleField
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.StatusBadge
import com.aidriy.app.ui.components.TagEditor
import com.aidriy.app.ui.components.UnsavedChangesDialog
import com.aidriy.app.ui.theme.Spacing

@Composable
fun NoteEditorRoute(
    onClose: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val draft = viewModel.draft
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onClose()
                is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
                is EditorEvent.OpenNote -> Unit
            }
        }
    }

    val requestClose: () -> Unit = {
        if (uiState.status == EditorStatus.Ready && draft.hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = uiState.status == EditorStatus.Ready && draft.hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    EditorScaffold(
        title = stringResource(if (uiState.noteId == null) R.string.note_editor_new else R.string.note_editor_edit),
        onBack = requestClose,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_note_editor"),
        actions = {
            if (uiState.status == EditorStatus.Ready) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (uiState.isFavorite) R.string.note_favorite_remove else R.string.note_favorite_add,
                        ),
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { viewModel.save() },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.testTag("editor_save"),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.action_save))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("editor_menu")) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (uiState.noteId != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (uiState.isArchived) R.string.note_unarchive else R.string.note_archive))
                                },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.setArchived(!uiState.isArchived)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            },
                            modifier = Modifier.testTag("menu_delete"),
                        )
                    }
                }
            }
        },
    ) {
        when (uiState.status) {
            EditorStatus.Loading -> LoadingState()
            EditorStatus.NotFound -> ErrorState(stringResource(R.string.error_not_found), onRetry = null)
            EditorStatus.LoadFailed -> ErrorState(LoadErrorText.asString(), onRetry = viewModel::retryLoad)
            EditorStatus.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            ) {
                if (uiState.isArchived) {
                    StatusBadge(
                        text = stringResource(R.string.note_archived_banner),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                EditorTitleField(
                    value = draft.title,
                    onValueChange = viewModel::onTitleChange,
                    placeholder = stringResource(R.string.note_title_hint),
                    modifier = Modifier.testTag("editor_title"),
                )
                EditorBodyField(
                    value = draft.content,
                    onValueChange = viewModel::onContentChange,
                    placeholder = stringResource(R.string.note_content_hint),
                    modifier = Modifier.testTag("editor_content"),
                )
                uiState.error?.let {
                    InlineError(it.asString(), modifier = Modifier.padding(horizontal = Spacing.lg))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                TagEditor(
                    tags = draft.tags,
                    allTags = allTags,
                    onAdd = viewModel::addTag,
                    onRemove = viewModel::removeTag,
                    errorMessage = uiState.tagError?.asString(),
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = {
                showUnsavedDialog = false
                viewModel.save(closeAfter = true)
            },
            onDiscard = {
                showUnsavedDialog = false
                onClose()
            },
            onDismiss = { showUnsavedDialog = false },
        )
    }
    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.note_delete_title),
            message = stringResource(R.string.note_delete_body),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
