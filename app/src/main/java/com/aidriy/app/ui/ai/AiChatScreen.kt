@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.data.ai.AiErrorCodec
import com.aidriy.app.domain.model.AiError
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.ui.common.rememberSpeechToText
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.MarkdownLite
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch

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
    val chosen by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val conversation by viewModel.currentConversation.collectAsStateWithLifecycle()
    val provider = chosen ?: settings.defaultProvider
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by rememberSaveable { mutableStateOf<SuggestionInput?>(null) }

    // Requests from AI Tools, history, or the document viewer.
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

    Box(
        Modifier
            .fillMaxSize()
            .testTag("screen_ai"),
    ) {
        Column(Modifier.fillMaxSize()) {
            AppHeaderBar(
                title = stringResource(R.string.ai_title),
                actions = {
                    IconButton(onClick = actions.openHistory, modifier = Modifier.testTag("ai_history")) {
                        Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.ai_history))
                    }
                    IconButton(onClick = viewModel::newConversation, enabled = conversation != null) {
                        Icon(Icons.Outlined.AddComment, contentDescription = stringResource(R.string.ai_new_chat))
                    }
                },
            )
            Text(
                stringResource(R.string.ai_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
            )
            ProviderRow(
                selected = provider,
                configured = configured,
                onSelect = viewModel::selectProvider,
                onTools = actions.openTools,
            )
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size, viewModel.isSending) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    if (provider !in configured) {
                        SetupCard(provider, actions.openAiSettings)
                    } else {
                        GreetingBubble(settings.profileName)
                    }
                }
                if (messages.isEmpty() && settings.showSuggestions) {
                    item {
                        SuggestionGrid(onPick = { s ->
                            viewModel.applySuggestion(s)
                            if (s.input != SuggestionInput.NONE) picker = s.input
                        })
                    }
                }
                items(messages, key = { it.id }) { message ->
                    when {
                        message.role == ChatRole.USER -> UserBubble(message.content)
                        message.isError -> ErrorBubble(
                            message = message,
                            onRetry = { viewModel.retry(message) },
                            onSettings = actions.openAiSettings,
                        )
                        else -> AssistantBubble(
                            message = message,
                            onOpenSource = actions.openSource,
                            onCopy = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("AIDRIY", message.content))
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copied)) }
                            },
                            onSaveNote = {
                                actions.saveAsNote(context.getString(R.string.ai_note_title), message.content)
                            },
                        )
                    }
                }
                if (viewModel.isSending) {
                    item { ThinkingBubble() }
                }
            }
            if (settings.useNotesData && viewModel.attached == null) {
                Text(
                    stringResource(R.string.ai_using_notes_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
            viewModel.attached?.let { ref ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(ref.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.attach(null) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.ai_detach), modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.padding(horizontal = Spacing.screenHorizontal),
                )
            }
            InputBar(
                value = viewModel.input,
                onValueChange = { viewModel.input = it },
                isSending = viewModel.isSending,
                onSend = viewModel::send,
                onCancel = viewModel::cancel,
                onMic = dictate,
            )
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    viewModel.pending?.let { p ->
        ConfirmShareDialog(
            pending = p,
            onSend = { viewModel.confirmPending(includeContext = true) },
            onSendWithout = { viewModel.confirmPending(includeContext = false) },
            onDismiss = viewModel::dismissPending,
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
}

@Composable
private fun ProviderRow(
    selected: AiProviderId,
    configured: Set<AiProviderId>,
    onSelect: (AiProviderId) -> Unit,
    onTools: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenHorizontal, end = Spacing.sm, top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AiProviderId.entries.forEach { p ->
                FilterChip(
                    selected = p == selected,
                    onClick = { onSelect(p) },
                    label = { Text(p.displayName) },
                    leadingIcon = if (p in configured) null else ({
                        Icon(Icons.Outlined.Key, contentDescription = stringResource(R.string.ai_not_configured), modifier = Modifier.size(14.dp))
                    }),
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.testTag("provider_${p.key}"),
                )
            }
        }
        IconButton(onClick = onTools, modifier = Modifier.testTag("ai_tools")) {
            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.ai_tools_title))
        }
    }
}

@Composable
private fun GreetingBubble(name: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (AidriyColors.isDark) 0.6f else 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                if (name.isBlank()) stringResource(R.string.ai_hello) else stringResource(R.string.ai_hello_name, name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.ai_hello_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SetupCard(provider: AiProviderId, onSetup: () -> Unit) {
    AidriyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentTile(Icons.Outlined.Key, AidriyColors.accent(com.aidriy.app.ui.theme.AccentKey.BLUE))
                Spacer(Modifier.size(Spacing.md))
                Text(
                    stringResource(R.string.ai_setup_title, provider.displayName),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.ai_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSetup, modifier = Modifier.testTag("ai_setup")) { Text(stringResource(R.string.ai_setup_action)) }
        }
    }
}

@Composable
private fun SuggestionGrid(onPick: (AiSuggestion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AiSuggestions.home.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { s ->
                    AidriyCard(onClick = { onPick(s) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            AccentTile(s.icon, AidriyColors.accent(s.accent), size = 34.dp, iconSize = 18.dp)
                            Spacer(Modifier.size(Spacing.sm))
                            Text(s.title, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }
    }
}

@Composable
private fun AssistantBubble(
    message: ChatMessage,
    onOpenSource: (SourceRef) -> Unit,
    onCopy: () -> Unit,
    onSaveNote: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(Spacing.xs))
            Text(
                stringResource(R.string.ai_label, message.provider?.displayName ?: "AI", message.model.orEmpty()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .padding(top = Spacing.xs)
                .widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                Text(
                    remember(message.content, linkColor) { MarkdownLite.render(message.content, linkColor) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (message.sources.isNotEmpty()) {
                    Text(
                        stringResource(R.string.ai_sources),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                    message.sources.forEachIndexed { i, ref ->
                        AssistChip(
                            onClick = { onOpenSource(ref) },
                            label = { Text("[${i + 1}] ${ref.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(sourceIcon(ref.type), contentDescription = null, modifier = Modifier.size(16.dp)) },
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
            }
        }
        Row {
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.action_copy))
            }
            TextButton(onClick = onSaveNote) {
                Icon(Icons.Outlined.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(Spacing.xs))
                Text(stringResource(R.string.ai_save_note))
            }
        }
    }
}

private fun sourceIcon(type: SourceType) = when (type) {
    SourceType.FILE -> Icons.Outlined.Description
    else -> Icons.Outlined.NoteAdd
}

@Composable
private fun ErrorBubble(message: ChatMessage, onRetry: () -> Unit, onSettings: () -> Unit) {
    val error = AiErrorCodec.decode(message.content)
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(error.toUiText().asString(), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(stringResource(R.string.action_retry))
                }
                if (error is AiError.MissingKey || error is AiError.InvalidKey || error is AiError.ModelNotFound) {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.ai_open_settings)) }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(Spacing.sm))
        Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.ime)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.ai_input_hint)) },
            modifier = Modifier
                .weight(1f)
                .testTag("ai_input"),
            shape = CircleShape,
            maxLines = 5,
            trailingIcon = {
                IconButton(onClick = onMic) { Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.fmt_dictate)) }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.size(Spacing.sm))
        if (isSending) {
            FilledIconButton(onClick = onCancel, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.ai_stop))
            }
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("ai_send"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ai_send))
            }
        }
    }
}

@Composable
private fun ConfirmShareDialog(
    pending: PendingSend,
    onSend: () -> Unit,
    onSendWithout: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.ai_confirm_body, pending.provider.company))
                pending.context.forEachIndexed { i, item ->
                    Text("[${i + 1}] ${item.ref.title}", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    stringResource(R.string.ai_confirm_chars, pending.context.sumOf { it.text.length }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onSend, modifier = Modifier.testTag("ai_confirm_send")) { Text(stringResource(R.string.ai_confirm_send)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                if (pending.retrievalRan) {
                    TextButton(onClick = onSendWithout) { Text(stringResource(R.string.ai_confirm_without)) }
                }
            }
        },
    )
}
