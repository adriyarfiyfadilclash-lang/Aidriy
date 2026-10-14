@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.aidriy.app.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.data.ai.AiErrorCodec
import com.aidriy.app.domain.model.AiError
import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import com.aidriy.app.domain.model.Conversation
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.ui.common.formatBytes
import com.aidriy.app.ui.common.rememberDocumentPicker
import com.aidriy.app.ui.common.rememberImagePicker
import com.aidriy.app.ui.common.rememberSpeechToText
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.files.NameDialog
import com.aidriy.app.ui.markdown.MarkdownView
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Navigation needed by the AI tab. */
data class AiActions(
    val openTools: () -> Unit,
    val openHistory: () -> Unit,
    val openAiSettings: () -> Unit,
    val openSource: (SourceRef) -> Unit,
    val saveAsNote: (title: String, content: String) -> Unit,
)

@Composable
fun AiChatRoute(
    actions: AiActions,
    viewModel: AiChatViewModel = viewModel(factory = AiChatViewModel.Factory),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val conversation by viewModel.currentConversation.collectAsStateWithLifecycle()
    val title by viewModel.currentTitle.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val focusManager = LocalFocusManager.current
    var picker by rememberSaveable { mutableStateOf<SuggestionInput?>(null) }
    var showAttachMenu by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    val provider = settings.defaultProvider

    // Requests from other screens (history, document viewer, AI tools).
    val request by AiInbox.request.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        AiInbox.consume()
        r.conversationId?.let { viewModel.openConversation(it) }
        r.suggestionId?.let { id ->
            AiSuggestions.byId(id)?.let { s ->
                viewModel.applySuggestion(s)
                if (s.input != SuggestionInput.NONE && r.source == null) picker = s.input
            }
        }
        r.source?.let { viewModel.attach(it) }
        if (r.openHistory) drawerState.open()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AiEvent.Message -> scope.launch { snackbarHostState.showSnackbar(event.text.resolve(context)) }
                AiEvent.OpenSettings -> actions.openAiSettings()
            }
        }
    }
    val dictate = rememberSpeechToText(
        onResult = { viewModel.input = (viewModel.input + " " + it).trim() },
        onUnavailable = { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.speech_unavailable)) } },
    )
    val pickDocument = rememberDocumentPicker { viewModel.attachFromDevice(it) }
    val pickImage = rememberImagePicker { viewModel.attachFromDevice(it) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HistorySidebar(
                conversations = conversations,
                current = conversation,
                query = viewModel.historyQuery,
                onQuery = { viewModel.historyQuery = it },
                onNewChat = {
                    focusManager.clearFocus()
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onOpen = {
                    // Close the keyboard left open by the history search.
                    focusManager.clearFocus()
                    viewModel.openConversation(it.id)
                    scope.launch { drawerState.close() }
                },
                onDelete = { deleting = it },
                onRename = { renaming = it },
                onClose = { scope.launch { drawerState.close() } },
                onSettings = {
                    scope.launch { drawerState.close() }
                    actions.openAiSettings()
                },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("screen_ai"),
        ) {
            Column(Modifier.fillMaxSize()) {
                ChatTopBar(
                    title = title,
                    hasConversation = conversation != null,
                    onMenu = { scope.launch { drawerState.open() } },
                    onNewChat = viewModel::newConversation,
                    onRename = { conversations.firstOrNull { it.id == conversation }?.let { renaming = it } },
                    onDelete = { conversations.firstOrNull { it.id == conversation }?.let { deleting = it } },
                    onSettings = actions.openAiSettings,
                )
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size, viewModel.isSending, streaming?.length) {
                    val last = messages.size + (if (viewModel.isSending) 1 else 0) - 1
                    if (last >= 0) listState.scrollToItem(last)
                }
                if (messages.isEmpty() && !viewModel.isSending) {
                    EmptyChat(
                        name = settings.profileName,
                        needsKey = provider !in configured,
                        providerName = provider.displayName,
                        onSetup = actions.openAiSettings,
                        onWrite = { viewModel.applyPrompt(context.getString(R.string.ai_prompt_write)) },
                        onLearn = { viewModel.applyPrompt(context.getString(R.string.ai_prompt_learn)) },
                        onAskNotes = {
                            viewModel.applyPrompt(context.getString(R.string.ai_prompt_notes))
                            picker = SuggestionInput.NOTE
                        },
                        onAnalyze = {
                            viewModel.applyPrompt(context.getString(R.string.ai_prompt_file))
                            showAttachMenu = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("ai_messages"),
                        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        val lastAnswerId = messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }?.id
                        items(messages, key = { it.id }) { message ->
                            val index = messages.indexOf(message)
                            when {
                                message.role == ChatRole.USER -> UserMessage(
                                    message = message,
                                    failed = messages.getOrNull(index + 1)?.isError == true,
                                    onOpen = actions.openSource,
                                )
                                message.isError -> ErrorCard(
                                    error = AiErrorCodec.decode(message.content),
                                    onRetry = { viewModel.retry(message) },
                                    onSettings = actions.openAiSettings,
                                )
                                else -> AssistantMessage(
                                    message = message,
                                    isLast = message.id == lastAnswerId && !viewModel.isSending,
                                    onOpenSource = actions.openSource,
                                    onCopy = {
                                        context.getSystemService(ClipboardManager::class.java)
                                            ?.setPrimaryClip(ClipData.newPlainText("AIDRIY", message.content))
                                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copied)) }
                                    },
                                    onRegenerate = { viewModel.regenerate(message) },
                                    onRate = { viewModel.rate(message, it) },
                                    onSaveNote = { actions.saveAsNote(title ?: context.getString(R.string.ai_note_title), message.content) },
                                )
                            }
                        }
                        if (viewModel.isSending) {
                            item(key = "streaming") { StreamingMessage(streaming) }
                        }
                    }
                }
                AttachmentRow(
                    attachments = viewModel.attachments,
                    busy = viewModel.isAttaching,
                    onRemove = viewModel::removeAttachment,
                )
                InputBar(
                    value = viewModel.input,
                    onValueChange = { viewModel.input = it },
                    isSending = viewModel.isSending,
                    onSend = viewModel::send,
                    onCancel = viewModel::cancel,
                    onAttach = { showAttachMenu = true },
                    onImage = pickImage,
                    onMic = dictate,
                )
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }

    viewModel.pending?.let { p ->
        ContextPermissionDialog(
            pending = p,
            onAllow = { viewModel.confirmPending(allow = true) },
            onDeny = { viewModel.confirmPending(allow = false) },
            onDismiss = viewModel::dismissPending,
        )
    }
    if (showAttachMenu) {
        AttachSheet(
            onDevice = {
                showAttachMenu = false
                pickDocument()
            },
            onBerkas = {
                showAttachMenu = false
                picker = SuggestionInput.DOCUMENT
            },
            onNote = {
                showAttachMenu = false
                picker = SuggestionInput.NOTE
            },
            onDismiss = { showAttachMenu = false },
        )
    }
    picker?.let { kind ->
        SourcePickerSheet(
            kind = kind,
            onPicked = { ref ->
                picker = null
                viewModel.attach(ref)
            },
            onDismiss = { picker = null },
        )
    }
    deleting?.let { c ->
        DeleteChatDialog(
            onConfirm = {
                deleting = null
                viewModel.deleteConversation(c.id)
            },
            onDismiss = { deleting = null },
        )
    }
    renaming?.let { c ->
        NameDialog(
            title = stringResource(R.string.ai_rename_chat),
            initial = c.title,
            onConfirm = { name ->
                renaming = null
                viewModel.renameConversation(c.id, name)
            },
            onDismiss = { renaming = null },
        )
    }
}

// ------------------------------------------------------------------ top bar

@Composable
private fun ChatTopBar(
    title: String?,
    hasConversation: Boolean,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.testTag("ai_menu")) {
            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.ai_history))
        }
        Text(
            title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Surface(
            onClick = onNewChat,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.testTag("ai_new_chat"),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AddComment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ai_new_chat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("ai_more")) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (hasConversation) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_rename_chat)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ai_delete_one_title).removeSuffix("?")) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_model_settings)) },
                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    onClick = { menu = false; onSettings() },
                    modifier = Modifier.testTag("menu_ai_settings"),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ empty state

@Composable
private fun EmptyChat(
    name: String,
    needsKey: Boolean,
    providerName: String,
    onSetup: () -> Unit,
    onWrite: () -> Unit,
    onLearn: () -> Unit,
    onAskNotes: () -> Unit,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("ai_empty"),
        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { AiRobot(size = 150.dp) }
        item {
            Text(
                if (name.isBlank()) stringResource(R.string.ai_hello_assistant) else stringResource(R.string.ai_hello_assistant_name, name),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                stringResource(R.string.ai_hello_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (needsKey) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("ai_setup"),
                ) {
                    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.ai_setup_title, providerName), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.ai_setup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onSetup) { Text(stringResource(R.string.ai_setup_action)) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StarterCard(Icons.Outlined.EditNote, AccentKey.BLUE, R.string.ai_card_write, R.string.ai_card_write_sub, "ai_card_write", onWrite, Modifier.weight(1f))
                    StarterCard(Icons.Outlined.Lightbulb, AccentKey.PURPLE, R.string.ai_card_learn, R.string.ai_card_learn_sub, "ai_card_learn", onLearn, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StarterCard(Icons.Outlined.StickyNote2, AccentKey.GREEN, R.string.ai_card_notes, R.string.ai_card_notes_sub, "ai_card_notes", onAskNotes, Modifier.weight(1f))
                    StarterCard(Icons.Outlined.Description, AccentKey.ORANGE, R.string.ai_card_files, R.string.ai_card_files_sub, "ai_card_files", onAnalyze, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StarterCard(icon: ImageVector, accent: AccentKey, title: Int, sub: Int, tag: String, onClick: () -> Unit, modifier: Modifier) {
    val a = AidriyColors.accent(accent)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 110.dp).testTag(tag),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Box(Modifier.size(40.dp).background(a.container, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = a.content)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

// ------------------------------------------------------------------ messages

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun clock(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable
private fun UserMessage(message: ChatMessage, failed: Boolean, onOpen: (SourceRef) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("ai_user_message"), horizontalAlignment = Alignment.End) {
        message.sources.forEach { ref ->
            AttachmentCard(ref = ref, sizeBytes = null, onClick = { onOpen(ref) }, onRemove = null, modifier = Modifier.padding(bottom = Spacing.xs))
        }
        Surface(
            color = if (AidriyColors.isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFE6EFFE),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (failed) {
                Text(
                    stringResource(R.string.ai_send_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
            } else {
                Text(clock(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = stringResource(R.string.ai_sent),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        Modifier
            .size(32.dp)
            .background(Color(0xFFDCE8FD), CircleShape),
        contentAlignment = Alignment.Center,
    ) { AiRobot(size = 28.dp, showSparkles = false) }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isLast: Boolean,
    onOpenSource: (SourceRef) -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onRate: (Int) -> Unit,
    onSaveNote: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().testTag("ai_answer")) {
        AssistantAvatar()
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_assistant_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)) {
                    MarkdownView(message.content, onOpenBerkas = { onOpenSource(SourceRef(SourceType.FILE, it, "")) })
                    if (message.sources.isNotEmpty()) {
                        Text(
                            stringResource(R.string.ai_sources),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.md, bottom = 4.dp),
                        )
                        message.sources.forEachIndexed { i, ref ->
                            Text(
                                "[${i + 1}] ${ref.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .combinedClickable(onClick = { onOpenSource(ref) }),
                            )
                        }
                    }
                    if (message.noLocalMatch) {
                        Text(
                            stringResource(R.string.ai_no_local_match),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    Text(
                        clock(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallAction(Icons.Outlined.ContentCopy, R.string.action_copy, "ai_copy", onClick = onCopy)
                if (isLast) SmallAction(Icons.Outlined.Refresh, R.string.ai_regenerate, "ai_regenerate", onClick = onRegenerate)
                SmallAction(
                    if (message.rating == Rating.UP) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    R.string.ai_rate_up, "ai_rate_up", active = message.rating == Rating.UP,
                ) { onRate(Rating.UP) }
                SmallAction(
                    if (message.rating == Rating.DOWN) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    R.string.ai_rate_down, "ai_rate_down", active = message.rating == Rating.DOWN,
                ) { onRate(Rating.DOWN) }
                SmallAction(Icons.Outlined.NoteAdd, R.string.ai_save_note, "ai_save_note", onClick = onSaveNote)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: Int, tag: String, active: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).testTag(tag)) {
        Icon(
            icon,
            contentDescription = stringResource(label),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun StreamingMessage(text: String?) {
    Row(Modifier.fillMaxWidth().testTag("ai_streaming_message")) {
        AssistantAvatar()
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_assistant_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Column(Modifier.padding(Spacing.md)) {
                    if (!text.isNullOrBlank()) {
                        MarkdownView(text)
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(error: AiError, onRetry: () -> Unit, onSettings: () -> Unit) {
    val offline = error == AiError.Offline || error == AiError.Timeout
    val keyProblem = error == AiError.MissingKey || error == AiError.InvalidKey || error is AiError.ModelNotFound
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("ai_error"),
    ) {
        Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (offline) Icons.Outlined.WifiOff else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (error == AiError.Offline) stringResource(R.string.ai_offline_title) else error.toUiText().asString(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (error == AiError.Offline) {
                        Text(stringResource(R.string.ai_offline_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("ai_retry")) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retry))
                }
                if (keyProblem) TextButton(onClick = onSettings) { Text(stringResource(R.string.ai_open_settings)) }
            }
        }
    }
}

// ------------------------------------------------------------------ attachments and input

@Composable
private fun AttachmentCard(ref: SourceRef, sizeBytes: Long?, onClick: (() -> Unit)?, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val isPdf = ref.title.endsWith(".pdf", ignoreCase = true)
    val accent = AidriyColors.accent(
        when {
            ref.type == SourceType.NOTE -> AccentKey.BLUE
            isPdf -> AccentKey.RED
            else -> AccentKey.TEAL
        },
    )
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .widthIn(max = 280.dp)
            .let { if (onClick != null) it.combinedClickable(onClick = onClick) else it }
            .testTag("ai_attachment"),
    ) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(accent.container, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                if (isPdf) {
                    Text("PDF", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent.content)
                } else {
                    Icon(if (ref.type == SourceType.NOTE) Icons.Outlined.StickyNote2 else Icons.Outlined.Description, contentDescription = null, tint = accent.content, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f, fill = false)) {
                Text(ref.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        stringResource(if (ref.type == SourceType.NOTE) R.string.nav_notes else R.string.nav_files),
                        sizeBytes?.let { formatBytes(it) },
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.ai_detach), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachments: List<ChatAttachment>, busy: Boolean, onRemove: (ChatAttachment) -> Unit) {
    if (attachments.isEmpty() && !busy) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        attachments.forEach { a ->
            AttachmentCard(ref = a.ref, sizeBytes = a.sizeBytes, onClick = null, onRemove = { onRemove(a) })
        }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.ai_attaching), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onAttach: () -> Unit,
    onImage: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = { Text(stringResource(R.string.ai_input_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_input"),
                )
                if (value.isEmpty()) {
                    IconButton(onClick = onMic) {
                        Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.fmt_dictate), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onAttach, modifier = Modifier.testTag("ai_attach")) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.ai_attach), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onImage, modifier = Modifier.testTag("ai_image")) {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.ai_attach_image), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        FilledIconButton(
            onClick = if (isSending) onCancel else onSend,
            enabled = isSending || value.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .size(52.dp)
                .testTag("ai_send"),
        ) {
            Icon(
                if (isSending) Icons.Outlined.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(if (isSending) R.string.ai_stop else R.string.ai_send),
            )
        }
    }
}

@Composable
private fun AttachSheet(onDevice: () -> Unit, onBerkas: () -> Unit, onNote: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = Spacing.xl).testTag("ai_attach_sheet")) {
            Text(
                stringResource(R.string.ai_attach),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            AttachOption(Icons.Outlined.PhoneAndroid, AccentKey.BLUE, R.string.ai_attach_device, R.string.ai_attach_device_sub, onDevice)
            AttachOption(Icons.Outlined.FolderOpen, AccentKey.ORANGE, R.string.ai_attach_berkas, R.string.ai_attach_berkas_sub, onBerkas)
            AttachOption(Icons.Outlined.StickyNote2, AccentKey.GREEN, R.string.ai_attach_note, R.string.ai_attach_note_sub, onNote)
        }
    }
}

@Composable
private fun AttachOption(icon: ImageVector, accent: AccentKey, title: Int, sub: Int, onClick: () -> Unit) {
    val a = AidriyColors.accent(accent)
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(a.container, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = a.content)
        }
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ sidebar

@Composable
private fun HistorySidebar(
    conversations: List<Conversation>,
    current: String?,
    query: String,
    onQuery: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpen: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.testTag("ai_sidebar")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_feather), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close)) }
            }
            SearchField(
                query = query,
                onQueryChange = onQuery,
                placeholder = stringResource(R.string.ai_history_search),
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("ai_history_search"),
            )
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("sidebar_new_chat"),
            ) {
                Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.ai_new_chat))
            }
            LazyColumn(Modifier.weight(1f).padding(top = Spacing.sm)) {
                if (conversations.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (query.isBlank()) R.string.ai_history_empty_title else R.string.no_results_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                }
                groupConversations(conversations).forEach { (group, items) ->
                    item(key = "h_$group") {
                        Text(
                            stringResource(
                                when (group) {
                                    HistoryGroup.TODAY -> R.string.ai_group_today
                                    HistoryGroup.YESTERDAY -> R.string.ai_group_yesterday
                                    HistoryGroup.THIS_WEEK -> R.string.ai_group_week
                                    HistoryGroup.OLDER -> R.string.ai_group_older
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
                        )
                    }
                    items(items, key = { it.id }) { c ->
                        HistoryItem(
                            conversation = c,
                            selected = c.id == current,
                            showTime = group == HistoryGroup.TODAY || group == HistoryGroup.YESTERDAY,
                            onClick = { onOpen(c) },
                            onLongClick = { onDelete(c) },
                            onRename = { onRename(c) },
                            onDelete = { onDelete(c) },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onSettings)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                    .testTag("ai_sidebar_settings"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_model_settings), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ai_model_settings_sub), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", Locale("id", "ID"))

@Composable
private fun HistoryItem(
    conversation: Conversation,
    selected: Boolean,
    showTime: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(conversation.updatedAt).atZone(ZoneId.systemDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Spacing.sm, top = 2.dp, bottom = 2.dp)
            .testTag("ai_history_item"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(conversation.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            if (showTime) time.format(timeFormat) else time.toLocalDate().format(dayFormat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more), modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.ai_rename_chat)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun DeleteChatDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.errorContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        },
        title = { Text(stringResource(R.string.ai_delete_one_title)) },
        text = { Text(stringResource(R.string.ai_delete_one_body_local)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("ai_delete_confirm"),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        modifier = Modifier.testTag("ai_delete_dialog"),
    )
}

/** "Izinkan penggunaan konteks?" — nothing private leaves the device without this. */
@Composable
private fun ContextPermissionDialog(pending: PendingSend, onAllow: () -> Unit, onDeny: () -> Unit, onDismiss: () -> Unit) {
    val items: List<SourceRef> = (pending.attachments.map { it.ref } + pending.context.map { it.ref }).distinctBy { it.type to it.id }
    val hasImages = pending.attachments.any { it.isImage }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(if (pending.retrievalRan) R.string.ai_consent_found else R.string.ai_consent_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    items.take(6).forEach { ref ->
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Icon(sourceIcon(ref.type), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.sm))
                                Column {
                                    Text(ref.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(sourceLabel(ref.type)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.ai_consent_data), style = MaterialTheme.typography.titleSmall)
                val bullets = buildList {
                    if (pending.context.isNotEmpty()) add(stringResource(R.string.ai_consent_text, pending.context.sumOf { it.text.length }))
                    if (hasImages) add(stringResource(R.string.ai_consent_images))
                    add(stringResource(R.string.ai_consent_provider, pending.provider.displayName))
                    add(stringResource(R.string.ai_consent_scope))
                }
                bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.ai_consent_deny_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onAllow, modifier = Modifier.testTag("ai_confirm_send")) { Text(stringResource(R.string.ai_consent_allow)) } },
        dismissButton = { OutlinedButton(onClick = onDeny, modifier = Modifier.testTag("ai_confirm_deny")) { Text(stringResource(R.string.ai_consent_deny)) } },
    )
}

private fun sourceIcon(type: SourceType): ImageVector = when (type) {
    SourceType.NOTE -> Icons.Outlined.StickyNote2
    SourceType.IDEA -> Icons.Outlined.Lightbulb
    SourceType.JOURNAL -> Icons.AutoMirrored.Outlined.MenuBook
    SourceType.FILE -> Icons.Outlined.Description
}

private fun sourceLabel(type: SourceType): Int = when (type) {
    SourceType.NOTE -> R.string.nav_notes
    SourceType.IDEA -> R.string.nav_ideas
    SourceType.JOURNAL -> R.string.nav_journal
    SourceType.FILE -> R.string.nav_files
}
