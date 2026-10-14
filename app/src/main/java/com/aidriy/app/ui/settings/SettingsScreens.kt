package com.aidriy.app.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.AppHeaderBar
import com.aidriy.app.ui.components.ProfileAvatar
import com.aidriy.app.ui.components.SettingsRow
import com.aidriy.app.ui.theme.Spacing

data class SettingsActions(
    val openProfile: () -> Unit,
    val openAi: () -> Unit,
    val openAppearance: () -> Unit,
    val openNotes: () -> Unit,
    val openFiles: () -> Unit,
    val openSecurity: () -> Unit,
    val openNotifications: () -> Unit,
    val openAbout: () -> Unit,
    val openSearch: () -> Unit,
)

/** Pengaturan tab: profile card and one row per settings area (as in the reference design). */
@Composable
fun SettingsHomeScreen(
    actions: SettingsActions,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_pengaturan"),
        contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AppHeaderBar(
                title = stringResource(R.string.nav_settings),
                actions = {
                    IconButton(onClick = actions.openSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_title))
                    }
                },
            )
        }
        item {
            AidriyCard(onClick = actions.openProfile, modifier = Modifier.fillMaxWidth().testTag("settings_profile")) {
                Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(name = settings.profileName, photoPath = settings.profilePhotoPath, size = 56.dp)
                    Spacer(Modifier.size(Spacing.lg))
                    Column {
                        Text(
                            settings.profileName.ifBlank { stringResource(R.string.profile_no_name) },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            AidriyCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(Icons.Outlined.ManageAccounts, stringResource(R.string.set_profile), stringResource(R.string.set_profile_sub), actions.openProfile)
                    Divider()
                    SettingsRow(Icons.Outlined.AutoAwesome, stringResource(R.string.set_ai), stringResource(R.string.set_ai_sub), actions.openAi, Modifier.testTag("settings_ai"))
                    Divider()
                    SettingsRow(Icons.Outlined.Palette, stringResource(R.string.set_appearance), stringResource(R.string.set_appearance_sub), actions.openAppearance)
                    Divider()
                    SettingsRow(Icons.Outlined.Description, stringResource(R.string.set_notes), stringResource(R.string.set_notes_sub), actions.openNotes)
                    Divider()
                    SettingsRow(Icons.Outlined.Folder, stringResource(R.string.set_files), stringResource(R.string.set_files_sub), actions.openFiles)
                }
            }
        }
        item {
            AidriyCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(Icons.Outlined.Shield, stringResource(R.string.set_security), stringResource(R.string.set_security_sub), actions.openSecurity)
                    Divider()
                    SettingsRow(Icons.Outlined.Notifications, stringResource(R.string.set_notifications), stringResource(R.string.set_notifications_sub), actions.openNotifications)
                    Divider()
                    SettingsRow(Icons.Outlined.Info, stringResource(R.string.set_about), stringResource(R.string.set_about_sub), actions.openAbout)
                }
            }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))

/** Shared layout for settings sub-screens: back arrow, title, snackbar for confirmations. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    testTag: String,
    content: LazyListScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(testTag),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                AppHeaderBar(
                    title = title,
                    navigation = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("header_back")) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            }
            content()
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Label + description + switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(Spacing.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Single-choice list inside a card. */
@Composable
fun <T> RadioGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    AidriyCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .semantics { role = Role.RadioButton },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == selected, onClick = null)
                    Spacer(Modifier.size(Spacing.md))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
    )
}
