@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aidriy.app.ui.notes

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aidriy.app.R
import com.aidriy.app.core.time.DatePickerDates
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.domain.markdown.CalloutKind
import com.aidriy.app.domain.markdown.Markdown
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.common.rememberCameraCapture
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
import com.aidriy.app.ui.markdown.MarkdownView
import com.aidriy.app.ui.markdown.ProvideBerkasLookup
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NoteEditorRoute(
    onClose: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenAiSettings: () -> Unit = {},
    viewModel: NoteEditorViewModel = viewModel(factory = NoteEditorViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val autosave by viewModel.autosave.collectAsStateWithLifecycle()
    val attachments by viewModel.attachmentList.collectAsStateWithLifecycle()
    val berkasImages by viewModel.berkasImages.collectAsStateWithLifecycle()
    val folders by viewModel.folderList.collectAsStateWithLifecycle()
    val formatInfo by viewModel.formatterReady.collectAsStateWithLifecycle()
    val formatState by viewModel.format.collectAsStateWithLifecycle()
    val draft = viewModel.draft
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blocks = remember { BlockEditorState() }

    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var showImageSheet by rememberSaveable { mutableStateOf(false) }
    var showBerkasPicker by rememberSaveable { mutableStateOf(false) }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var showTextSheet by rememberSaveable { mutableStateOf(false) }
    var showTableDialog by rememberSaveable { mutableStateOf(false) }
    var showCalloutDialog by rememberSaveable { mutableStateOf(false) }
    var showEmojiSheet by rememberSaveable { mutableStateOf(false) }
    var showFormatSheet by rememberSaveable { mutableStateOf(false) }
    var showFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showReminderDate by rememberSaveable { mutableStateOf(false) }
    var pickedReminderDate by rememberSaveable { mutableStateOf<String?>(null) }
    var captionFor by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Blocks follow the stored text (load, undo/redo, AI format); typing flows back via onChanged.
    LaunchedEffect(draft.content) { blocks.syncFrom(draft.content) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Close -> onClose()
                is EditorEvent.Message -> snackbarHostState.showSnackbar(event.text.resolve(context))
                is EditorEvent.OpenNote -> Unit
            }
        }
    }

    fun applyEdit(transform: (TextFieldValue) -> TextFieldValue) {
        if (blocks.applyToActive(transform)) viewModel.onContentChange(blocks.serialize())
    }
    fun insertImage(file: StoredFile, caption: String? = null) {
        val label = caption ?: context.getString(R.string.image_default_caption, blocks.imageCount() + 1)
        blocks.insertImage(Markdown.berkasTarget(file.id), label)
        viewModel.onContentChange(blocks.serialize())
    }
    fun withBusy(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val dictate = rememberSpeechToText(
        onResult = { text -> applyEdit { MarkdownLite.insertBlock(it, text) } },
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.speech_unavailable)) } },
    )
    val pickImage = rememberImagePicker { uri -> withBusy { viewModel.importToBerkas(uri)?.let { insertImage(it) } } }
    val takePhoto = rememberCameraCapture(
        onCaptured = { uri -> withBusy { viewModel.importToBerkas(uri)?.let { insertImage(it) } } },
        onUnavailable = { viewModel.message(UiText(R.string.scan_camera_unavailable)) },
    )
    val scanDocument = rememberCameraCapture(
        onCaptured = { uri ->
            withBusy {
                val (file, text) = viewModel.scanToBerkas(uri) ?: return@withBusy
                insertImage(file)
                if (text.isNotBlank()) applyEdit { MarkdownLite.insertBlock(it, text.trim()) }
            }
        },
        onUnavailable = { viewModel.message(UiText(R.string.scan_camera_unavailable)) },
    )
    val pickFile = rememberDocumentPicker { uri ->
        withBusy {
            val file = viewModel.importToBerkas(uri) ?: return@withBusy
            applyEdit { MarkdownLite.insertBlock(it, "[📎 ${file.name.replace("]", ")")}](${Markdown.berkasTarget(file.id)})") }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        showReminderDate = true
    }
    val askReminder: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showReminderDate = true
        }
    }

    val ready = uiState.status == EditorStatus.Ready
    val editing = uiState.mode == NoteMode.EDIT
    val requestBack: () -> Unit = {
        when {
            !ready -> onClose()
            !editing -> onClose()
            // Existing note: back from editing returns to the reader.
            !viewModel.startedNew && (autosave || !draft.hasUnsavedChanges) -> viewModel.finishEditing()
            autosave -> viewModel.closeWithAutosave()
            draft.hasUnsavedChanges -> showUnsavedDialog = true
            else -> onClose()
        }
    }
    BackHandler(enabled = ready && editing) { requestBack() }

    ProvideBerkasLookup(viewModel::lookupFile) {
        EditorScaffold(
            title = if (uiState.noteId == null) stringResource(R.string.note_editor_new) else "",
            onBack = requestBack,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.testTag("screen_note_editor"),
            actions = {
                if (ready) {
                    if (editing) {
                        IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo, modifier = Modifier.testTag("editor_undo")) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.action_undo))
                        }
                        IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo, modifier = Modifier.testTag("editor_redo")) {
                            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.action_redo))
                        }
                        IconButton(onClick = { showFormatSheet = true }, modifier = Modifier.testTag("editor_ai")) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.format_title), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (uiState.noteId != null) {
                            IconButton(onClick = viewModel::finishEditing, modifier = Modifier.testTag("editor_done")) {
                                Icon(Icons.Outlined.Done, contentDescription = stringResource(R.string.note_done), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        IconButton(onClick = viewModel::toggleFavorite, modifier = Modifier.testTag("editor_pin")) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(if (uiState.isFavorite) R.string.note_favorite_remove else R.string.note_favorite_add),
                                tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = viewModel::startEditing, modifier = Modifier.testTag("editor_edit")) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.note_edit), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    NoteMenu(
                        uiState = uiState,
                        editing = editing,
                        canUndo = viewModel.canUndo,
                        onSave = viewModel::save,
                        onFormat = { showFormatSheet = true },
                        onUndo = viewModel::undo,
                        onReminder = askReminder,
                        onTags = { showTagSheet = true },
                        onFolder = { showFolderDialog = true },
                        onShare = {
                            shareText(
                                context,
                                draft.title.ifBlank { context.getString(R.string.app_name) },
                                listOf(draft.title, draft.content).filter { it.isNotBlank() }.joinToString("\n\n"),
                            )
                        },
                        onArchive = { viewModel.setArchived(!uiState.isArchived) },
                        onDelete = { showDeleteDialog = true },
                    )
                }
            },
        ) {
            when (uiState.status) {
                EditorStatus.Loading -> LoadingState()
                EditorStatus.NotFound -> ErrorState(stringResource(R.string.error_not_found), onRetry = null)
                EditorStatus.LoadFailed -> ErrorState(LoadErrorText.asString(), onRetry = viewModel::retryLoad)
                EditorStatus.Ready -> if (editing) {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = Spacing.sm),
                        ) {
                            NoteMetaLine(uiState, draft.tags, folders.firstOrNull { it.id == uiState.folderId }?.name, onReminder = askReminder, onClearReminder = { viewModel.setReminder(null) })
                            TitleField(draft.title, viewModel::onTitleChange)
                            BlockEditor(
                                state = blocks,
                                placeholder = stringResource(R.string.note_content_hint),
                                onChanged = viewModel::onContentChange,
                                onEditCaption = { key, caption -> captionFor = key to caption },
                            )
                            uiState.error?.let { InlineError(it.asString(), modifier = Modifier.padding(horizontal = Spacing.lg)) }
                            SaveStatus(uiState, autosave, draft.hasUnsavedChanges, busy)
                            Spacer(Modifier.height(Spacing.lg))
                        }
                        EditorToolbar(
                            onBold = { applyEdit { MarkdownLite.toggleWrap(it, "**") } },
                            onItalic = { applyEdit { MarkdownLite.toggleWrap(it, "*") } },
                            onUnderline = { applyEdit { MarkdownLite.toggleWrap(it, "__") } },
                            onHeading = { applyEdit { MarkdownLite.prefixLines(it, "# ") } },
                            onBullet = { applyEdit { MarkdownLite.prefixLines(it, "- ") } },
                            onNumbered = { applyEdit { MarkdownLite.prefixLines(it, "", numbered = true) } },
                            onLink = { showLinkDialog = true },
                            onText = { showTextSheet = true },
                            onImage = { showImageSheet = true },
                            onChecklist = { applyEdit { MarkdownLite.prefixLines(it, "- [ ] ") } },
                            onFile = pickFile,
                            onMore = { showMoreSheet = true },
                        )
                    }
                } else {
                    NoteReader(
                        uiState = uiState,
                        title = draft.title,
                        content = draft.content,
                        tags = draft.tags,
                        folderName = folders.firstOrNull { it.id == uiState.folderId }?.name,
                        attachments = attachments.filter { !draft.content.contains(Markdown.berkasTarget(it.id)) },
                        onToggleTask = viewModel::toggleTask,
                        onOpenFile = onOpenFile,
                        onReminder = askReminder,
                        onClearReminder = { viewModel.setReminder(null) },
                        onEdit = viewModel::startEditing,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ sheets and dialogs

    if (showImageSheet) {
        InsertImageSheet(
            onGallery = { showImageSheet = false; pickImage() },
            onCamera = { showImageSheet = false; takePhoto() },
            onScan = { showImageSheet = false; scanDocument() },
            onBerkas = { showImageSheet = false; showBerkasPicker = true },
            onDismiss = { showImageSheet = false },
        )
    }
    if (showBerkasPicker) {
        BerkasImagePickerSheet(
            images = berkasImages,
            onPick = { file ->
                showBerkasPicker = false
                insertImage(file)
            },
            onDismiss = { showBerkasPicker = false },
        )
    }
    if (showTextSheet) {
        TextStyleSheet(
            onPick = { choice ->
                showTextSheet = false
                when (choice) {
                    TextStyleChoice.H1 -> applyEdit { MarkdownLite.prefixLines(it, "# ") }
                    TextStyleChoice.H2 -> applyEdit { MarkdownLite.prefixLines(it, "## ") }
                    TextStyleChoice.H3 -> applyEdit { MarkdownLite.prefixLines(it, "### ") }
                    TextStyleChoice.PARAGRAPH -> applyEdit { MarkdownLite.prefixLines(it, "") }
                    TextStyleChoice.BOLD -> applyEdit { MarkdownLite.toggleWrap(it, "**") }
                    TextStyleChoice.ITALIC -> applyEdit { MarkdownLite.toggleWrap(it, "*") }
                    TextStyleChoice.UNDERLINE -> applyEdit { MarkdownLite.toggleWrap(it, "__") }
                    TextStyleChoice.STRIKE -> applyEdit { MarkdownLite.toggleWrap(it, "~~") }
                    TextStyleChoice.HIGHLIGHT -> applyEdit { MarkdownLite.toggleWrap(it, "==") }
                    TextStyleChoice.INLINE_CODE -> applyEdit { MarkdownLite.toggleWrap(it, "`") }
                }
            },
            onDismiss = { showTextSheet = false },
        )
    }
    if (showMoreSheet) {
        MoreElementsSheet(
            onPick = { element ->
                showMoreSheet = false
                when (element) {
                    MoreElement.HEADING -> applyEdit { MarkdownLite.prefixLines(it, "## ") }
                    MoreElement.NUMBERED -> applyEdit { MarkdownLite.prefixLines(it, "", numbered = true) }
                    MoreElement.BULLET -> applyEdit { MarkdownLite.prefixLines(it, "- ") }
                    MoreElement.CHECKLIST -> applyEdit { MarkdownLite.prefixLines(it, "- [ ] ") }
                    MoreElement.TABLE -> showTableDialog = true
                    MoreElement.QUOTE -> applyEdit { MarkdownLite.prefixLines(it, "> ") }
                    MoreElement.DIVIDER -> applyEdit { MarkdownLite.insertBlock(it, "\n---\n") }
                    MoreElement.LINK -> showLinkDialog = true
                    MoreElement.CODE -> applyEdit { MarkdownLite.insertBlock(it, "```\n\n```") }
                    MoreElement.CALLOUT -> showCalloutDialog = true
                    MoreElement.DATETIME -> applyEdit { MarkdownLite.insert(it, nowLabel()) }
                    MoreElement.EMOJI -> showEmojiSheet = true
                    MoreElement.DICTATE -> dictate()
                    MoreElement.TAGS -> showTagSheet = true
                }
            },
            onDismiss = { showMoreSheet = false },
        )
    }
    if (showTableDialog) {
        val header = stringResource(R.string.table_column)
        TableDialog(
            onInsert = { rows, cols ->
                showTableDialog = false
                applyEdit { MarkdownLite.insertBlock(it, tableMarkdown(rows, cols, header)) }
            },
            onDismiss = { showTableDialog = false },
        )
    }
    if (showCalloutDialog) {
        CalloutDialog(
            onPick = { kind ->
                showCalloutDialog = false
                val key = when (kind) {
                    CalloutKind.INFO -> "INFO"
                    CalloutKind.TIP -> "TIP"
                    CalloutKind.IMPORTANT -> "PENTING"
                    CalloutKind.WARNING -> "PERINGATAN"
                }
                applyEdit { MarkdownLite.insertBlock(it, "> [!$key]\n> ") }
            },
            onDismiss = { showCalloutDialog = false },
        )
    }
    if (showEmojiSheet) {
        EmojiSheet(
            onPick = { emoji ->
                showEmojiSheet = false
                applyEdit { MarkdownLite.insert(it, emoji) }
            },
            onDismiss = { showEmojiSheet = false },
        )
    }
    captionFor?.let { (key, caption) ->
        CaptionDialog(
            initial = caption,
            onConfirm = { text ->
                captionFor = null
                blocks.updateImage(key, caption = text)
                viewModel.onContentChange(blocks.serialize())
            },
            onDismiss = { captionFor = null },
        )
    }
    if (showFormatSheet && formatState !is FormatState.Preview) {
        FormatSheet(
            info = formatInfo,
            state = formatState,
            onPreview = viewModel::requestFormat,
            onOpenAiSettings = {
                showFormatSheet = false
                onOpenAiSettings()
            },
            onCancelWork = viewModel::cancelFormat,
            onDismiss = {
                showFormatSheet = false
                viewModel.cancelFormat()
            },
        )
    }
    (formatState as? FormatState.Preview)?.let { preview ->
        FormatPreviewDialog(
            preview = preview,
            onApply = {
                showFormatSheet = false
                viewModel.applyFormat()
            },
            onDismiss = {
                showFormatSheet = false
                viewModel.cancelFormat()
            },
        )
    }
    if (showFolderDialog) {
        MoveToFolderDialog(
            folders = folders,
            current = uiState.folderId,
            canCreate = false,
            onPick = {
                showFolderDialog = false
                viewModel.setFolder(it)
            },
            onCreate = {},
            onDismiss = { showFolderDialog = false },
        )
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
        val selected = blocks.activeText()?.value?.let { it.text.substring(it.selection.min, it.selection.max) }.orEmpty()
        LinkDialog(
            initialText = selected,
            onConfirm = { label, url ->
                showLinkDialog = false
                val clean = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                applyEdit { MarkdownLite.insert(it, "[${label.ifBlank { clean }}]($clean)") }
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
                if (!viewModel.startedNew) viewModel.finishEditing() else viewModel.save(closeAfter = true)
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

private fun nowLabel(): String {
    val now = LocalDateTime.now()
    return IndonesianDateFormatter.formatLong(now.toLocalDate()) + ", " + now.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun tableMarkdown(rows: Int, cols: Int, header: String): String = buildString {
    appendLine((1..cols).joinToString(" | ", "| ", " |") { "$header $it" })
    appendLine((1..cols).joinToString(" | ", "| ", " |") { "---" })
    repeat(rows) { r ->
        append((1..cols).joinToString(" | ", "| ", " |") { " " })
        if (r < rows - 1) append('\n')
    }
}

// ------------------------------------------------------------------ reader

@Composable
private fun NoteReader(
    uiState: NoteEditorUiState,
    title: String,
    content: String,
    tags: List<String>,
    folderName: String?,
    attachments: List<StoredFile>,
    onToggleTask: (Int) -> Unit,
    onOpenFile: (String) -> Unit,
    onReminder: () -> Unit,
    onClearReminder: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .testTag("note_reader"),
    ) {
        if (uiState.isArchived) StatusBadge(text = stringResource(R.string.note_archived_banner))
        Text(
            title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = Spacing.xs).testTag("reader_title"),
        )
        if (tags.isNotEmpty() || folderName != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
                folderName?.let { SmallChip(Icons.Outlined.Folder, it) }
                tags.forEach { SmallChip(null, it) }
            }
        }
        uiState.updatedAt?.let {
            Text(
                formatTimestamp(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        uiState.reminderAt?.let { at -> ReminderChip(at, onReminder, onClearReminder) }
        HorizontalDivider(Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
        if (content.isBlank()) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.note_reader_empty)) }
        } else {
            MarkdownView(
                markdown = content,
                onToggleTask = onToggleTask,
                onOpenBerkas = onOpenFile,
            )
        }
        if (attachments.isNotEmpty()) AttachmentStrip(attachments, onOpenFile)
        Spacer(Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun SmallChip(icon: ImageVector?, label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderChip(at: Long, onReminder: () -> Unit, onClear: () -> Unit) {
    AssistChip(
        onClick = onReminder,
        label = { Text(stringResource(R.string.reminder_chip, formatRelative(at))) },
        leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.reminder_remove), modifier = Modifier.size(16.dp))
            }
        },
        colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
    )
}

// ------------------------------------------------------------------ editor parts

@Composable
private fun NoteMetaLine(
    uiState: NoteEditorUiState,
    tags: List<String>,
    folderName: String?,
    onReminder: () -> Unit,
    onClearReminder: () -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.lg)) {
        if (uiState.isArchived) StatusBadge(text = stringResource(R.string.note_archived_banner))
        if (tags.isNotEmpty() || folderName != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                folderName?.let { SmallChip(Icons.Outlined.Folder, it) }
                tags.forEach { SmallChip(null, it) }
            }
        }
        uiState.reminderAt?.let { ReminderChip(it, onReminder, onClearReminder) }
    }
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit) {
    val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_title"),
        textStyle = style,
        placeholder = { Text(stringResource(R.string.note_title_hint), style = style) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = 3,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SaveStatus(uiState: NoteEditorUiState, autosave: Boolean, unsaved: Boolean, busy: Boolean) {
    val status = when {
        busy -> stringResource(R.string.editor_processing)
        uiState.isSaving -> stringResource(R.string.saving)
        autosave && uiState.autoSavedAt != null && !unsaved -> stringResource(R.string.autosaved)
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
}

/** Floating format row plus the Teks · Gambar · Checklist · Berkas · Lainnya tabs. */
@Composable
private fun EditorToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    onLink: () -> Unit,
    onText: () -> Unit,
    onImage: () -> Unit,
    onChecklist: () -> Unit,
    onFile: () -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("editor_toolbar")) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(Icons.Outlined.FormatBold, R.string.fmt_bold, onBold)
                ToolIcon(Icons.Outlined.FormatItalic, R.string.fmt_italic, onItalic)
                ToolIcon(Icons.Outlined.FormatUnderlined, R.string.fmt_underline, onUnderline)
                IconButton(onClick = onHeading) {
                    Text("H1", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.fmt_bullets, onBullet)
                ToolIcon(Icons.Outlined.FormatListNumbered, R.string.fmt_numbers, onNumbered)
                ToolIcon(Icons.Outlined.Link, R.string.fmt_link, onLink)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ToolTab(Icons.Outlined.TextFields, R.string.tool_text, "tool_text", onText)
            ToolTab(Icons.Outlined.Image, R.string.tool_image, "tool_image", onImage)
            ToolTab(Icons.Outlined.CheckBox, R.string.fmt_checklist, "tool_checklist", onChecklist)
            ToolTab(Icons.Outlined.Description, R.string.nav_files, "tool_file", onFile)
            ToolTab(Icons.Outlined.MoreHoriz, R.string.more_title, "tool_more", onMore)
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToolTab(icon: ImageVector, labelRes: Int, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NoteMenu(
    uiState: NoteEditorUiState,
    editing: Boolean,
    canUndo: Boolean,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onUndo: () -> Unit,
    onReminder: () -> Unit,
    onTags: () -> Unit,
    onFolder: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("editor_menu")) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entries = buildList {
                if (editing) add(MenuEntry(R.string.action_save, Icons.Outlined.Save, "editor_save", onSave))
                if (!editing) add(MenuEntry(R.string.format_title, Icons.Outlined.AutoAwesome, "menu_format", onFormat))
                if (!editing && canUndo) add(MenuEntry(R.string.action_undo, Icons.AutoMirrored.Outlined.Undo, null, onUndo))
                add(MenuEntry(R.string.reminder_set_action, Icons.Outlined.NotificationsNone, null, onReminder))
                add(MenuEntry(R.string.tags_label, Icons.Outlined.Sell, null, onTags))
                add(MenuEntry(R.string.note_move_folder, Icons.Outlined.Folder, "menu_folder", onFolder))
                add(MenuEntry(R.string.action_share, Icons.Outlined.Share, null, onShare))
                add(
                    MenuEntry(
                        if (uiState.isArchived) R.string.note_unarchive else R.string.note_archive,
                        if (uiState.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        null,
                        onArchive,
                    ),
                )
                add(MenuEntry(R.string.action_delete, Icons.Outlined.Delete, "menu_delete", onDelete))
            }
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        entry.action()
                    },
                    modifier = entry.tag?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
    }
}

private data class MenuEntry(val label: Int, val icon: ImageVector, val tag: String?, val action: () -> Unit)

@Composable
private fun AttachmentStrip(files: List<StoredFile>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = Spacing.md)) {
        Text(
            stringResource(R.string.attachments_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
