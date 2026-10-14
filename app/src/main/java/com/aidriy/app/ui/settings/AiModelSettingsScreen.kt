@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.domain.model.AiLanguage
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.AnswerStyle
import com.aidriy.app.domain.model.ResponseLength
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.theme.Spacing

/** Pengaturan Model AI: provider, model, API key, answer preferences, connection test. */
@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val provider = settings.defaultProvider
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.ai_model_settings), onBack, viewModel, "screen_settings_ai") {
        // ------------------------------------------------------------ provider
        item { SettingsSectionLabel(stringResource(R.string.ai_provider)) }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = Spacing.xs)) {
                    AiProviderId.entries.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDefaultProvider(p) }
                                .padding(horizontal = Spacing.sm)
                                .testTag("provider_${p.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = p == provider, onClick = { viewModel.setDefaultProvider(p) })
                            Column(Modifier.weight(1f)) {
                                Text(p.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(if (p in configured) R.string.ai_key_stored else R.string.ai_not_configured),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (p in configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (provider == AiProviderId.OTHER) {
            item { SettingsSectionLabel(stringResource(R.string.ai_base_url)) }
            item {
                BaseUrlField(settings.otherBaseUrl, onSave = viewModel::setOtherBaseUrl)
            }
        }

        // ------------------------------------------------------------ model
        item { SettingsSectionLabel(stringResource(R.string.ai_default_model_id)) }
        item {
            ModelPicker(
                provider = provider,
                model = settings.modelFor(provider),
                onSave = { viewModel.setModel(provider, it) },
            )
        }

        // ------------------------------------------------------------ key
        item { SettingsSectionLabel(stringResource(R.string.ai_api_key)) }
        item {
            ApiKeyField(
                provider = provider,
                hasKey = provider in configured,
                onSave = { viewModel.saveKey(provider, it) },
                onRemove = { viewModel.removeKey(provider) },
            )
        }

        // ------------------------------------------------------------ answers
        item { SettingsSectionLabel(stringResource(R.string.ai_preferences)) }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.ai_language), style = MaterialTheme.typography.labelLarge)
                    Dropdown(
                        value = stringResource(settings.aiLanguage.labelRes()),
                        options = AiLanguage.entries.map { it to stringResource(it.labelRes()) },
                        onSelect = viewModel::setLanguage,
                    )
                    Text(stringResource(R.string.ai_style), style = MaterialTheme.typography.labelLarge)
                    Dropdown(
                        value = stringResource(settings.answerStyle.labelRes()),
                        options = AnswerStyle.entries.map { it to stringResource(it.labelRes()) },
                        onSelect = viewModel::setStyle,
                    )
                    Text(stringResource(R.string.ai_length), style = MaterialTheme.typography.labelLarge)
                    Dropdown(
                        value = stringResource(settings.responseLength.labelRes()),
                        options = ResponseLength.entries.map { it to stringResource(it.labelRes()) },
                        onSelect = viewModel::setResponseLength,
                    )
                }
            }
        }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow(
                        stringResource(R.string.ai_streaming), stringResource(R.string.ai_streaming_sub),
                        settings.streaming, { viewModel.setStreaming(it) },
                        modifier = Modifier.testTag("ai_streaming"),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        stringResource(R.string.ai_use_notes), stringResource(R.string.ai_use_notes_sub),
                        settings.useNotesData, { viewModel.setUseNotes(it) },
                        modifier = Modifier.testTag("ai_use_notes"),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        stringResource(R.string.ai_save_history), stringResource(R.string.ai_save_history_sub),
                        settings.saveChatHistory, { viewModel.setSaveHistory(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchRow(
                        stringResource(R.string.ai_show_suggestions), stringResource(R.string.ai_show_suggestions_sub),
                        settings.showSuggestions, { viewModel.setShowSuggestions(it) },
                    )
                }
            }
        }

        // ------------------------------------------------------------ test
        item {
            Button(
                onClick = { viewModel.testConnection(provider) },
                enabled = provider in configured && viewModel.testingProvider == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_test"),
            ) {
                if (viewModel.testingProvider != null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(stringResource(R.string.ai_test))
            }
        }
        val ok = viewModel.testOk
        val message = viewModel.testMessage
        if (ok != null && message != null) {
            item {
                Surface(
                    color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                stringResource(if (ok) R.string.ai_test_success_title else R.string.ai_test_failed_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(message.asString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ data
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { confirmClearHistory = true }) { Text(stringResource(R.string.ai_clear_history)) }
                OutlinedButton(onClick = { confirmRemoveAll = true }, enabled = configured.isNotEmpty()) {
                    Text(stringResource(R.string.ai_remove_all_keys))
                }
            }
        }
    }
    if (confirmRemoveAll) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.ai_remove_all_keys),
            message = stringResource(R.string.ai_remove_all_keys_body),
            onConfirm = {
                confirmRemoveAll = false
                viewModel.removeAllKeys()
            },
            onDismiss = { confirmRemoveAll = false },
        )
    }
    if (confirmClearHistory) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.ai_delete_all_title),
            message = stringResource(R.string.ai_delete_all_body),
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearChatHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

/** Suggested models in a dropdown, plus a free text field for any other model ID. */
@Composable
private fun ModelPicker(provider: AiProviderId, model: String, onSave: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var custom by rememberSaveable(provider, model) { mutableStateOf(model) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.trim() },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .testTag("ai_model"),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                provider.suggestedModels.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = {
                            expanded = false
                            custom = m
                            onSave(m)
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ai_model_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(custom) }, enabled = custom.isNotBlank() && custom != model) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun ApiKeyField(provider: AiProviderId, hasKey: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit) {
    var key by rememberSaveable(provider) { mutableStateOf("") }
    var show by rememberSaveable(provider) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            placeholder = { Text(if (hasKey) "••••••••••••••••" else stringResource(R.string.ai_key_enter)) },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (show) R.string.ai_key_hide else R.string.ai_key_show),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_${provider.key}"),
        )
        Text(
            stringResource(R.string.ai_keys_explain),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(
                onClick = {
                    onSave(key)
                    key = ""
                },
                enabled = key.isNotBlank(),
                modifier = Modifier.testTag("api_save_${provider.key}"),
            ) { Text(stringResource(if (hasKey) R.string.ai_key_replace else R.string.action_save)) }
            if (hasKey) {
                OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.ai_key_remove)) }
            }
        }
    }
}

@Composable
private fun BaseUrlField(current: String, onSave: (String) -> Unit) {
    var url by rememberSaveable(current) { mutableStateOf(current) }
    Column {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it.trim() },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ai_base_url_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onSave(url) }, enabled = url != current) { Text(stringResource(R.string.action_save)) }
        }
    }
}

internal fun ResponseLength.labelRes() = when (this) {
    ResponseLength.SHORT -> R.string.ai_length_short
    ResponseLength.MEDIUM -> R.string.ai_length_medium
    ResponseLength.LONG -> R.string.ai_length_long
}
