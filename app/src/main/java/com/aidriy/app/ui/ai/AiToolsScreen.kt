package com.aidriy.app.ui.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aidriy.app.AidriyApplication
import com.aidriy.app.R
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.ui.common.formatRelative
import com.aidriy.app.ui.components.AccentTile
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.ChipOption
import com.aidriy.app.ui.components.ChoiceChipRow
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.SectionTitle
import com.aidriy.app.ui.theme.AccentKey
import com.aidriy.app.ui.theme.AidriyColors
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** AI Tools: choose the default model and start from a categorized suggestion. */
@Composable
fun AiToolsScreen(
    onBack: () -> Unit,
    onPickSuggestion: (String) -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AidriyApplication).container
    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    val configured by container.secretStore.configured.collectAsState()
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf(SuggestionCategory.CHAT) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_ai_tools"),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            AppHeaderBar(
                title = stringResource(R.string.ai_tools_title),
                navigation = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        }
        item {
            ChoiceChipRow(
                options = SuggestionCategory.entries.map { ChipOption(it, it.label) },
                selected = category,
                onSelect = { category = it },
            )
        }
        item { SectionTitle(stringResource(R.string.ai_pick_model)) }
        items(AiProviderId.entries.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { p ->
                    val selected = settings.defaultProvider == p
                    Surface(
                        onClick = { scope.launch { container.settingsRepository.setDefaultProvider(p) } },
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 108.dp),
                    ) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(p.company, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                settings.modelFor(p),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(if (p in configured) R.string.ai_connected else R.string.ai_not_configured),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (p in configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = onOpenAiSettings) { Text(stringResource(R.string.ai_manage_keys)) }
        }
        item { SectionTitle(stringResource(R.string.ai_quick_suggestions)) }
        items(AiSuggestions.all.filter { it.category == category }, key = { it.id }) { s ->
            RecordCard(
                title = s.title,
                preview = s.prompt.trim(),
                meta = s.category.label,
                onClick = { onPickSuggestion(s.id) },
                leading = { AccentTile(s.icon, AidriyColors.accent(s.accent), size = 36.dp, iconSize = 18.dp) },
            )
        }
    }
}

/** Saved AI conversations. */
@Composable
fun AiHistoryScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as AidriyApplication).container
    val conversations by remember { container.aiChatRepository.observeConversations() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmAll by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_ai_history"),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            AppHeaderBar(
                title = stringResource(R.string.ai_history),
                navigation = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (conversations.isNotEmpty()) {
                        TextButton(onClick = { confirmAll = true }) { Text(stringResource(R.string.ai_delete_all)) }
                    }
                },
            )
        }
        if (conversations.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Forum,
                    title = stringResource(R.string.ai_history_empty_title),
                    body = stringResource(R.string.ai_history_empty_body),
                )
            }
        }
        items(conversations, key = { it.id }) { c ->
            RecordCard(
                title = c.title,
                preview = "",
                meta = formatRelative(c.updatedAt),
                onClick = { onOpen(c.id) },
                leading = { AccentTile(Icons.Outlined.Forum, AidriyColors.accent(AccentKey.BLUE), size = 36.dp, iconSize = 18.dp) },
                trailing = {
                    IconButton(onClick = { pendingDelete = c.id }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        }
    }

    if (confirmAll) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.ai_delete_all_title),
            message = stringResource(R.string.ai_delete_all_body),
            onConfirm = {
                confirmAll = false
                scope.launch { container.aiChatRepository.deleteAllConversations() }
            },
            onDismiss = { confirmAll = false },
        )
    }
    pendingDelete?.let { id ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.ai_delete_one_title),
            message = stringResource(R.string.ai_delete_one_body),
            onConfirm = {
                pendingDelete = null
                scope.launch { container.aiChatRepository.deleteConversation(id) }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
