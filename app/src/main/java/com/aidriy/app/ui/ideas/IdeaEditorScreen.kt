package com.aidriy.app.ui.ideas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EditorBodyField
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.EditorTitleField
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.SectionHeader
import com.aidriy.app.ui.components.TagEditor
import com.aidriy.app.ui.components.UnsavedChangesDialog
import com.aidriy.app.ui.theme.Spacing

@Composable
fun IdeaEditorRoute(
    onClose: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: IdeaEditorViewModel = viewModel(factory = IdeaEditorViewModel.Factory),
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
                is EditorEvent.OpenNote -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.idea_converted),
                        actionLabel = context.getString(R.string.action_open),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenNote(event.noteId)
                }
            }
        }
    }

    val canEdit = uiState.status == EditorStatus.Ready
    val requestClose: () -> Unit = {
        if (canEdit && draft.hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = canEdit && draft.hasUnsavedChanges) { showUnsavedDialog = true }

    EditorScaffold(
        title = stringResource(if (uiState.ideaId == null) R.string.idea_editor_new else R.string.idea_editor_edit),
        onBack = requestClose,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_idea_editor"),
        actions = {
            if (canEdit) {
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.idea_convert)) },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            enabled = !uiState.isConverting,
                            onClick = {
                                menuExpanded = false
                                viewModel.convertToNote()
                            },
                            modifier = Modifier.testTag("menu_convert"),
                        )
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
                EditorTitleField(
                    value = draft.title,
                    onValueChange = viewModel::onTitleChange,
                    placeholder = stringResource(R.string.idea_title_hint),
                    modifier = Modifier.testTag("editor_title"),
                )
                EditorBodyField(
                    value = draft.content,
                    onValueChange = viewModel::onContentChange,
                    placeholder = stringResource(R.string.idea_content_hint),
                    minLines = 5,
                    modifier = Modifier.testTag("editor_content"),
                )
                uiState.error?.let {
                    InlineError(it.asString(), modifier = Modifier.padding(horizontal = Spacing.lg))
                }
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    SectionHeader(title = stringResource(R.string.idea_status_label))
                    ChoiceChipRow(
                        options = IdeaStatus.entries.map { status ->
                            ChipOption(status, stringResource(status.labelRes()), "editor_status_${status.storageKey}")
                        },
                        selected = viewModel.selectedStatus,
                        onSelect = viewModel::onStatusChange,
                    )
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
            title = stringResource(R.string.idea_delete_title),
            message = stringResource(R.string.idea_delete_body),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}
