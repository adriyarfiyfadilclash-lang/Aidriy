@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.data.reminders.Notifications
import com.aidriy.app.data.settings.TextSize
import com.aidriy.app.data.settings.ThemeMode
import com.aidriy.app.domain.model.AiLanguage
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.AnswerStyle
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.formatBytes
import com.aidriy.app.ui.common.rememberImagePicker
import com.aidriy.app.ui.common.suppressLock
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.ConfirmDeleteDialog
import com.aidriy.app.ui.components.InlineError
import com.aidriy.app.ui.components.ProfileAvatar
import com.aidriy.app.ui.theme.Spacing
import java.time.LocalDate
import java.util.Locale

// ------------------------------------------------------------------------------------------ Profil

@Composable
fun ProfileSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val used by viewModel.storageUsed.collectAsStateWithLifecycle()
    var name by rememberSaveable(settings.profileName) { mutableStateOf(settings.profileName) }
    val pickPhoto = rememberImagePicker { viewModel.setPhoto(it) }

    SettingsPage(stringResource(R.string.set_profile), onBack, viewModel, "screen_settings_profile") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(settings.profileName, settings.profilePhotoPath, size = 72.dp)
                        Spacer(Modifier.size(Spacing.lg))
                        Column {
                            TextButton(onClick = pickPhoto) { Text(stringResource(R.string.profile_change_photo)) }
                            if (settings.profilePhotoPath != null) {
                                TextButton(onClick = viewModel::removePhoto) { Text(stringResource(R.string.profile_remove_photo)) }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name"),
                    )
                    Button(
                        onClick = { viewModel.setName(name) },
                        enabled = name.trim() != settings.profileName,
                        modifier = Modifier.testTag("profile_save"),
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
        item { SettingsSectionLabel(stringResource(R.string.profile_device)) }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.profile_local_only), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.profile_storage, formatBytes(used)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------- Preferensi AI

@Composable
internal fun <T> Dropdown(value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

internal fun AnswerStyle.labelRes() = when (this) {
    AnswerStyle.BALANCED -> R.string.ai_style_balanced
    AnswerStyle.CONCISE -> R.string.ai_style_concise
    AnswerStyle.DETAILED -> R.string.ai_style_detailed
}

internal fun AiLanguage.labelRes() = when (this) {
    AiLanguage.INDONESIAN -> R.string.lang_id
    AiLanguage.ENGLISH -> R.string.lang_en
}

// ------------------------------------------------------------------------------------------ Tampilan

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.set_appearance), onBack, viewModel, "screen_settings_appearance") {
        item { SettingsSectionLabel(stringResource(R.string.appearance_theme)) }
        item {
            RadioGroup(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark),
                ),
                selected = settings.themeMode,
                onSelect = viewModel::setTheme,
            )
        }
        item { SettingsSectionLabel(stringResource(R.string.appearance_text_size)) }
        item {
            RadioGroup(
                options = listOf(
                    TextSize.SMALL to stringResource(R.string.text_small),
                    TextSize.NORMAL to stringResource(R.string.text_normal),
                    TextSize.LARGE to stringResource(R.string.text_large),
                ),
                selected = settings.textSize,
                onSelect = viewModel::setTextSize,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------- Catatan

@Composable
fun NoteSettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsPage(stringResource(R.string.set_notes), onBack, viewModel, "screen_settings_notes") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                SwitchRow(
                    stringResource(R.string.notes_autosave), stringResource(R.string.notes_autosave_sub),
                    settings.autosave, { viewModel.setAutosave(it) },
                )
            }
        }
        item { SettingsSectionLabel(stringResource(R.string.notes_default_sort)) }
        item {
            RadioGroup(
                options = listOf(
                    SortOrder.UPDATED to stringResource(R.string.sort_updated),
                    SortOrder.CREATED to stringResource(R.string.sort_created),
                ),
                selected = settings.noteSort,
                onSelect = viewModel::setNoteSort,
            )
        }
        item {
            OutlinedButton(onClick = onOpenTemplates) { Text(stringResource(R.string.templates_title)) }
        }
        item {
            Text(
                stringResource(R.string.notes_format_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------- Berkas & backup

@Composable
fun FileSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val used by viewModel.storageUsed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var warnExport by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readBackup(uri)
    }

    SettingsPage(stringResource(R.string.set_files), onBack, viewModel, "screen_settings_files") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.files_storage_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.profile_storage, formatBytes(used)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.files_storage_private),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SettingsSectionLabel(stringResource(R.string.backup_title)) }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
                    if (viewModel.backupBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { warnExport = true }, enabled = !viewModel.backupBusy) {
                            Text(stringResource(R.string.backup_export))
                        }
                        OutlinedButton(
                            onClick = {
                                context.suppressLock()
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            enabled = !viewModel.backupBusy,
                        ) { Text(stringResource(R.string.backup_import)) }
                    }
                    Text(
                        stringResource(R.string.backup_not_included),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (warnExport) {
        AlertDialog(
            onDismissRequest = { warnExport = false },
            title = { Text(stringResource(R.string.backup_warn_title)) },
            text = { Text(stringResource(R.string.backup_warn_body)) },
            confirmButton = {
                TextButton(onClick = {
                    warnExport = false
                    context.suppressLock()
                    exportLauncher.launch("aidriy-backup-${LocalDate.now()}.zip")
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = { TextButton(onClick = { warnExport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    viewModel.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_body,
                        pending.summary.notes, pending.summary.ideas, pending.summary.journalEntries, pending.summary.files,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.backup_restore_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ----------------------------------------------------------------------------- Privasi & Keamanan

@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    val biometricAvailable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    SettingsPage(stringResource(R.string.set_security), onBack, viewModel, "screen_settings_security") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow(
                        stringResource(R.string.lock_title), stringResource(R.string.lock_sub),
                        settings.appLockEnabled,
                        { on -> if (on) showPinDialog = true else viewModel.disableLock() },
                        modifier = Modifier.testTag("lock_toggle"),
                    )
                    if (settings.appLockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(onClick = { showPinDialog = true }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                            Text(stringResource(R.string.lock_change_pin))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchRow(
                            stringResource(R.string.lock_biometric),
                            stringResource(if (biometricAvailable) R.string.lock_biometric_sub else R.string.lock_biometric_unavailable),
                            settings.biometricEnabled && biometricAvailable,
                            { viewModel.setBiometric(it) },
                            enabled = biometricAvailable,
                        )
                    }
                }
            }
        }
        item { SettingsSectionLabel(stringResource(R.string.privacy_title)) }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.privacy_point_local), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.privacy_point_keys), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.privacy_point_ai), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.privacy_point_backup), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (showPinDialog) {
        PinSetupDialog(
            onSubmit = { pin, confirm -> viewModel.setPin(pin, confirm).also { if (it == null) showPinDialog = false } },
            onDismiss = { showPinDialog = false },
        )
    }
}

@Composable
private fun PinSetupDialog(onSubmit: (String, String) -> UiText?, onDismiss: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lock_set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.lock_pin_rules), style = MaterialTheme.typography.bodyMedium)
                PinField(pin, { pin = it }, stringResource(R.string.lock_pin), "pin_new")
                PinField(confirm, { confirm = it }, stringResource(R.string.lock_pin_confirm), "pin_confirm")
                error?.let { InlineError(it.asString()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSubmit(pin, confirm) }, modifier = Modifier.testTag("pin_save")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

// ------------------------------------------------------------------------------------------ Notifikasi

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionTick by remember { mutableStateOf(0) }
    val allowed = remember(permissionTick) { Notifications.canPost(context) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    SettingsPage(stringResource(R.string.set_notifications), onBack, viewModel, "screen_settings_notifications") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(if (allowed) R.string.notif_allowed else R.string.notif_blocked),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    if (!allowed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(onClick = { requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text(stringResource(R.string.notif_allow))
                                }
                            }
                            OutlinedButton(onClick = {
                                context.suppressLock()
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text(stringResource(R.string.notif_open_settings)) }
                        }
                    }
                }
            }
        }
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column {
                    SwitchRow(
                        stringResource(R.string.notif_daily),
                        stringResource(
                            R.string.notif_daily_sub,
                            String.format(Locale.ROOT, "%02d:%02d", settings.dailyReminderHour, settings.dailyReminderMinute),
                        ),
                        settings.dailyReminderEnabled,
                        { on ->
                            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setDailyReminder(on, settings.dailyReminderHour, settings.dailyReminderMinute)
                        },
                        modifier = Modifier.testTag("daily_toggle"),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = { showTime = true }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                        Text(stringResource(R.string.notif_change_time))
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.notif_note_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showTime) {
        val state = rememberTimePickerState(settings.dailyReminderHour, settings.dailyReminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.notif_change_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    viewModel.setDailyReminder(settings.dailyReminderEnabled, state.hour, state.minute)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// --------------------------------------------------------------------------------------------- Tentang

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        } catch (e: Exception) {
            "-"
        }
    }
    SettingsPage(stringResource(R.string.set_about), onBack, viewModel, "screen_settings_about") {
        item {
            AidriyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.app_full_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item { SettingsSectionLabel(stringResource(R.string.privacy_title)) }
        item { Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium) }
        item { SettingsSectionLabel(stringResource(R.string.about_licenses)) }
        item { Text(stringResource(R.string.about_licenses_body), style = MaterialTheme.typography.bodyMedium) }
    }
}
