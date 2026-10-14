@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.notes

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aidriy.app.R
import com.aidriy.app.core.time.DatePickerDates
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.common.rememberDocumentPicker
import com.aidriy.app.ui.common.rememberImagePicker
import com.aidriy.app.ui.common.rememberSpeechToText
import com.aidriy.app.ui.common.shareText
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.MarkdownLite
import com.aidriy.app.ui.components.StatusBadge
import com.aidriy.app.ui.components.TagEditor
import com.aidriy.app.ui.components.UnsavedChangesDialog
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun NoteEditorRoute(
    onClose: () -> Unit,
    onOpenFile: (String) -> Unit,
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val autosave by viewModel.autosave.collectAsStateWithLifecycle()
    val attachments by viewModel.attachmentList.collectAsStateWithLifecycle()
    val draft = viewModel.draft
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var showReminderDate by rememberSaveable { mutableStateOf(false) }
    var pickedReminderDate by rememberSaveable { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Content keeps its cursor/selection in a TextFieldValue; the text itself lives in the draft.
    var content by remember { mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length))) }
    if (content.text != draft.content) {
        content = TextFieldValue(draft.content, TextRange(draft.content.length))
    }
    fun updateContent(value: TextFieldValue) {
        content = value
        viewModel.onContentChange(value.text)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onClose()
                is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
                is EditorEvent.OpenNote -> Unit
            }
        }
    }

    val dictate = rememberSpeechToText(
        onResult = { text -> updateContent(MarkdownLite.insertBlock(content, text)) },
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.speech_unavailable)) } },
    )
    val pickImage = rememberImagePicker { viewModel.attach(it) }
    val pickFile = rememberDocumentPicker { viewModel.attach(it) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showReminderDate = true
    }

    val ready = uiState.status == EditorStatus.Ready
    val requestClose: () -> Unit = {
        when {
            !ready -> onClose()
            autosave -> viewModel.closeWithAutosave()
            draft.hasUnsavedChanges -> showUnsavedDialog = true
            else -> onClose()
        }
    }
    BackHandler(enabled = ready && (autosave || draft.hasUnsavedChanges)) { requestClose() }

    EditorScaffold(
        title = stringResource(if (uiState.noteId == null) R.string.note_editor_new else R.string.note_editor_edit),
        onBack = requestClose,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_note_editor"),
        actions = {
            if (ready) {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(
                            if (uiState.isFavorite) R.string.note_favorite_remove else R.string.note_favorite_add,
                        ),
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            showReminderDate = true
                        }
                    },
                    modifier = Modifier.testTag("editor_reminder"),
                ) {
                    Icon(
                        imageVector = if (uiState.reminderAt != null) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                        contentDescription = stringResource(R.string.reminder_set_action),
                        tint = if (uiState.reminderAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("editor_menu")) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save)) },
                            onClick = {
                                menuExpanded = false
                                viewModel.save()
                            },
                            modifier = Modifier.testTag("editor_save"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                shareText(context, draft.title.ifBlank { context.getString(R.string.app_name) }, listOf(draft.title, draft.content).filter { it.isNotBlank() }.joinToString("\n\n"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (uiState.isArchived) R.string.note_unarchive else R.string.note_archive)) },
                            leadingIcon = {
                                Icon(if (uiState.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                viewModel.setArchived(!uiState.isArchived)
                            },
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
            EditorStatus.Ready -> Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.sm),
                ) {
                    if (uiState.isArchived) {
                        StatusBadge(
                            text = stringResource(R.string.note_archived_banner),
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                    PlainField(
                        value = draft.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = stringResource(R.string.note_title_hint),
                        large = true,
                        modifier = Modifier.testTag("editor_title"),
                    )
                    uiState.reminderAt?.let { at ->
                        AssistChip(
                            onClick = { showReminderDate = true },
                            label = { Text(stringResource(R.string.reminder_chip, formatRelative(at))) },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setReminder(null) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.reminder_remove), modifier = Modifier.size(16.dp))
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }
                    if (draft.tags.isNotEmpty()) {
                        Text(
                            text = draft.tags.joinToString("  ") { "#$it" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                    ContentField(
                        value = content,
                        onValueChange = ::updateContent,
                        placeholder = stringResource(R.string.note_content_hint),
                        modifier = Modifier.testTag("editor_content"),
                    )
                    if (attachments.isNotEmpty()) {
                        AttachmentStrip(attachments, onOpenFile)
                    }
                    uiState.error?.let {
                        InlineError(it.asString(), modifier = Modifier.padding(horizontal = Spacing.lg))
                    }
                    val status = when {
                        uiState.isSaving -> stringResource(R.string.saving)
                        autosave && uiState.autoSavedAt != null && !draft.hasUnsavedChanges -> stringResource(R.string.autosaved)
                        else -> null
                    }
                    if (status != null) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                    Spacer(Modifier.size(Spacing.lg))
                }
                FormattingToolbar(
                    onBold = { updateContent(MarkdownLite.toggleWrap(content, "**")) },
                    onItalic = { updateContent(MarkdownLite.toggleWrap(content, "*")) },
                    onUnderline = { updateContent(MarkdownLite.toggleWrap(content, "__")) },
                    onBullet = { updateContent(MarkdownLite.prefixLines(content, "- ")) },
                    onNumbered = { updateContent(MarkdownLite.prefixLines(content, "", numbered = true)) },
                    onChecklist = { updateContent(MarkdownLite.prefixLines(content, "- [ ] ")) },
                    onImage = pickImage,
                    onAttach = pickFile,
                    onLink = { showLinkDialog = true },
                    onHeading = { updateContent(MarkdownLite.prefixLines(content, "## ")) },
                    onTags = { showTagSheet = true },
                    onMic = dictate,
                )
            }
        }
    }

    if (showTagSheet) {
        ModalBottomSheet(onDismissRequest = { showTagSheet = false }) {
            TagEditor(
                tags = draft.tags,
                allTags = allTags,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                errorMessage = uiState.tagError?.asString(),
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            )
        }
    }
    if (showLinkDialog) {
        LinkDialog(
            initialText = content.text.substring(content.selection.min, content.selection.max),
            onConfirm = { label, url ->
                showLinkDialog = false
                val clean = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                updateContent(MarkdownLite.insert(content, "[${label.ifBlank { clean }}]($clean)"))
            },
            onDismiss = { showLinkDialog = false },
        )
    }
    if (showReminderDate) {
        ReminderDateDialog(
            onConfirm = { date ->
                showReminderDate = false
                pickedReminderDate = date.toString()
            },
            onDismiss = { showReminderDate = false },
        )
    }
    pickedReminderDate?.let { iso ->
        ReminderTimeDialog(
            onConfirm = { time ->
                pickedReminderDate = null
                val at = LocalDate.parse(iso).atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                viewModel.setReminder(at)
            },
            onDismiss = { pickedReminderDate = null },
        )
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

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    large: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style,
        placeholder = { Text(placeholder, style = style) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = 3,
        colors = transparentFieldColors(),
    )
}

@Composable
private fun ContentField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val linkColor = MaterialTheme.colorScheme.primary
    val transformation = remember(markerColor, linkColor) { MarkdownLite.visualTransformation(markerColor, linkColor) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        visualTransformation = transformation,
        minLines = 10,
        colors = transparentFieldColors(),
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun FormattingToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    onChecklist: () -> Unit,
    onImage: () -> Unit,
    onAttach: () -> Unit,
    onLink: () -> Unit,
    onHeading: () -> Unit,
    onTags: () -> Unit,
    onMic: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton(Icons.Outlined.FormatBold, R.string.fmt_bold, onBold)
                ToolButton(Icons.Outlined.FormatItalic, R.string.fmt_italic, onItalic)
                ToolButton(Icons.Outlined.FormatUnderlined, R.string.fmt_underline, onUnderline)
                ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.fmt_bullets, onBullet)
                ToolButton(Icons.Outlined.FormatListNumbered, R.string.fmt_numbers, onNumbered)
                ToolButton(Icons.Outlined.CheckBox, R.string.fmt_checklist, onChecklist)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton(Icons.Outlined.Image, R.string.fmt_image, onImage)
                ToolButton(Icons.Outlined.AttachFile, R.string.fmt_attach, onAttach)
                ToolButton(Icons.Outlined.Link, R.string.fmt_link, onLink)
                ToolButton(Icons.Outlined.Title, R.string.fmt_heading, onHeading)
                ToolButton(Icons.Outlined.Tag, R.string.tags_label, onTags)
                ToolButton(Icons.Outlined.Mic, R.string.fmt_dictate, onMic)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AttachmentStrip(files: List<StoredFile>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Text(
            stringResource(R.string.attachments_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), contentPadding = PaddingValues(end = Spacing.lg)) {
            items(files, key = { it.id }) { file ->
                Surface(
                    onClick = { onOpen(file.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(width = 120.dp, height = 96.dp),
                ) {
                    if (file.isImage) {
                        AsyncImage(
                            model = File(file.localPath),
                            contentDescription = file.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            AccentTile(Icons.Outlined.Description, AidriyColors.accent(AccentKey.RED), size = 32.dp, iconSize = 18.dp)
                            Text(file.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkDialog(initialText: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(initialText) }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fmt_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.link_text)) }, singleLine = true)
                OutlinedTextField(url, { url = it.trim() }, label = { Text(stringResource(R.string.link_url)) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim(), url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.action_insert))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ReminderDateDialog(onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DatePickerDates.toPickerMillis(today),
        selectableDates = remember {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !DatePickerDates.fromPickerMillis(utcTimeMillis).isBefore(today)
                override fun isSelectableYear(year: Int) = year >= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(DatePickerDates.fromPickerMillis(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun ReminderTimeDialog(onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val now = remember { LocalTime.now().plusMinutes(5) }
    val state = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.reminder_set_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
