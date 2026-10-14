package com.aidriy.app.ui.settings

import android.text.format.DateUtils
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.cloud.AuthError
import com.aidriy.app.data.cloud.AuthResult
import com.aidriy.app.data.sync.CloudState
import com.aidriy.app.data.sync.SyncManager
import com.aidriy.app.data.sync.SyncStatus
import com.aidriy.app.di.appContainer
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AccountMode { LOGIN, REGISTER, FORGOT }

class AccountViewModel(private val sync: SyncManager) : ViewModel() {
    val state: StateFlow<CloudState> = sync.state

    var mode by mutableStateOf(AccountMode.LOGIN)
        private set
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    var info by mutableStateOf<UiText?>(null)
        private set

    fun switchMode(m: AccountMode) {
        mode = m
        error = null
        info = null
    }

    fun submit() {
        val mail = email.trim()
        error = null
        info = null
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            error = UiText(R.string.acc_err_email)
            return
        }
        if (mode != AccountMode.FORGOT && password.length < MIN_PASSWORD) {
            error = UiText(R.string.acc_err_short, listOf(MIN_PASSWORD))
            return
        }
        busy = true
        viewModelScope.launch {
            when (mode) {
                AccountMode.LOGIN -> handle(sync.signIn(mail, password))
                AccountMode.REGISTER -> handle(sync.signUp(mail, password))
                AccountMode.FORGOT -> {
                    val e = sync.sendPasswordReset(mail)
                    if (e == null) info = UiText(R.string.acc_reset_sent, listOf(mail)) else error = e.toUiText()
                }
            }
            busy = false
        }
    }

    private fun handle(r: AuthResult) {
        when (r) {
            is AuthResult.SignedIn -> password = ""
            is AuthResult.ConfirmEmail -> {
                info = UiText(R.string.acc_confirm_sent, listOf(r.email))
                mode = AccountMode.LOGIN
                password = ""
            }
            is AuthResult.Failed -> error = r.error.toUiText()
        }
    }

    fun syncNow() = sync.requestSync()

    fun resolveMerge(keepLocal: Boolean) {
        busy = true
        viewModelScope.launch {
            sync.resolveMerge(keepLocal)
            busy = false
        }
    }

    /** Returns the error to show inside the dialog, or null when the password was changed. */
    suspend fun setNewPassword(pw: String): UiText? {
        if (pw.length < MIN_PASSWORD) return UiText(R.string.acc_err_short, listOf(MIN_PASSWORD))
        val e = sync.setNewPassword(pw) ?: return null.also { info = UiText(R.string.acc_password_changed) }
        return e.toUiText()
    }

    fun dismissNewPassword() = sync.dismissNewPassword()

    fun signOut(wipe: Boolean) {
        busy = true
        viewModelScope.launch {
            sync.signOut(wipe)
            busy = false
            info = null
            error = null
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(appContainer().syncManager) }
        }
    }
}

fun AuthError.toUiText(): UiText = when (this) {
    AuthError.InvalidCredentials -> UiText(R.string.acc_err_credentials)
    AuthError.EmailNotConfirmed -> UiText(R.string.acc_err_not_confirmed)
    AuthError.UserExists -> UiText(R.string.acc_err_exists)
    is AuthError.WeakPassword -> UiText(R.string.acc_err_weak)
    AuthError.InvalidEmail -> UiText(R.string.acc_err_email)
    AuthError.RateLimited -> UiText(R.string.acc_err_rate)
    AuthError.Offline -> UiText(R.string.acc_err_offline)
    AuthError.SessionExpired -> UiText(R.string.acc_err_session)
    is AuthError.Other -> UiText(R.string.acc_err_other, listOf(code))
}

/** Pengaturan → Akun & Sinkronisasi: sign in / register / reset, sync status, sign out. */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val cloud by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var changePassword by rememberSaveable { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.acc_title), onBack, settingsViewModel, "screen_settings_account") {
        if (cloud.email == null) {
            item { SignedOutIntro() }
            item { AuthForm(viewModel) }
        } else {
            item { SignedInCard(cloud, viewModel.info, onSync = viewModel::syncNow) }
            item {
                AidriyCard(Modifier.fillMaxWidth()) {
                    Column {
                        TextButton(
                            onClick = { changePassword = true },
                            modifier = Modifier.padding(horizontal = Spacing.sm).testTag("acc_change_password"),
                        ) { Text(stringResource(R.string.acc_change_password)) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(
                            onClick = { confirmSignOut = true },
                            enabled = !viewModel.busy,
                            modifier = Modifier.padding(horizontal = Spacing.sm).testTag("acc_sign_out"),
                        ) { Text(stringResource(R.string.acc_sign_out), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item { PrivacyNote() }
        }
    }

    if (cloud.needsMergeChoice) MergeDialog(busy = viewModel.busy, onChoose = viewModel::resolveMerge)
    if (cloud.needsNewPassword || changePassword) {
        NewPasswordDialog(
            recovery = cloud.needsNewPassword,
            onSave = viewModel::setNewPassword,
            onDone = {
                changePassword = false
                viewModel.dismissNewPassword()
            },
        )
    }
    if (confirmSignOut) {
        SignOutDialog(
            onConfirm = { wipe ->
                confirmSignOut = false
                viewModel.signOut(wipe)
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun SignedOutIntro() {
    AidriyCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(Spacing.md))
            Column {
                Text(stringResource(R.string.acc_intro_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.acc_intro_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthForm(vm: AccountViewModel) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AidriyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                stringResource(
                    when (vm.mode) {
                        AccountMode.LOGIN -> R.string.acc_login
                        AccountMode.REGISTER -> R.string.acc_register
                        AccountMode.FORGOT -> R.string.acc_forgot_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            if (vm.mode == AccountMode.FORGOT) {
                Text(stringResource(R.string.acc_forgot_body), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text(stringResource(R.string.acc_email)) },
                singleLine = true,
                enabled = !vm.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("acc_email"),
            )
            if (vm.mode != AccountMode.FORGOT) {
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text(stringResource(R.string.acc_password)) },
                    singleLine = true,
                    enabled = !vm.busy,
                    supportingText = if (vm.mode == AccountMode.REGISTER) {
                        { Text(stringResource(R.string.acc_password_hint, AccountViewModel.MIN_PASSWORD)) }
                    } else null,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (showPassword) R.string.acc_hide_password else R.string.acc_show_password),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("acc_password"),
                )
            }
            vm.error?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("acc_error"))
            }
            vm.info?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("acc_info"))
            }
            Button(onClick = vm::submit, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("acc_submit")) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.size(Spacing.sm))
                }
                Text(
                    stringResource(
                        when (vm.mode) {
                            AccountMode.LOGIN -> R.string.acc_login_action
                            AccountMode.REGISTER -> R.string.acc_register_action
                            AccountMode.FORGOT -> R.string.acc_forgot_action
                        },
                    ),
                )
            }
            when (vm.mode) {
                AccountMode.LOGIN -> {
                    TextButton(onClick = { vm.switchMode(AccountMode.REGISTER) }, modifier = Modifier.testTag("acc_to_register")) {
                        Text(stringResource(R.string.acc_to_register))
                    }
                    TextButton(onClick = { vm.switchMode(AccountMode.FORGOT) }, modifier = Modifier.testTag("acc_to_forgot")) {
                        Text(stringResource(R.string.acc_to_forgot))
                    }
                }
                else -> TextButton(onClick = { vm.switchMode(AccountMode.LOGIN) }, modifier = Modifier.testTag("acc_to_login")) {
                    Text(stringResource(R.string.acc_to_login))
                }
            }
        }
    }
}

@Composable
private fun SignedInCard(cloud: CloudState, info: UiText?, onSync: () -> Unit) {
    AidriyCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.acc_signed_in_as), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cloud.email.orEmpty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("acc_email_label"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val (icon, label) = when (cloud.status) {
                SyncStatus.RUNNING -> Icons.Outlined.CloudSync to R.string.acc_status_running
                SyncStatus.OK -> Icons.Outlined.CloudDone to R.string.acc_status_ok
                SyncStatus.OFFLINE -> Icons.Outlined.CloudOff to R.string.acc_status_offline
                SyncStatus.ERROR -> Icons.Outlined.ErrorOutline to R.string.acc_status_error
                SyncStatus.SESSION_EXPIRED -> Icons.Outlined.ErrorOutline to R.string.acc_err_session
                SyncStatus.IDLE -> Icons.Outlined.CloudSync to R.string.acc_status_idle
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cloud.status == SyncStatus.RUNNING) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(Spacing.sm))
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("acc_status"))
            }
            val last = if (cloud.lastSyncAt > 0) {
                DateUtils.getRelativeTimeSpanString(cloud.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            } else stringResource(R.string.acc_never)
            Text(stringResource(R.string.acc_last_sync, last), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cloud.pending > 0) {
                Text(stringResource(R.string.acc_pending, cloud.pending), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            info?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(
                onClick = onSync,
                enabled = cloud.status != SyncStatus.RUNNING,
                modifier = Modifier.fillMaxWidth().testTag("acc_sync_now"),
            ) { Text(stringResource(R.string.acc_sync_now)) }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        stringResource(R.string.acc_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
private fun MergeDialog(busy: Boolean, onChoose: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.acc_merge_title)) },
        text = { Text(stringResource(R.string.acc_merge_body)) },
        confirmButton = {
            TextButton(onClick = { onChoose(true) }, enabled = !busy, modifier = Modifier.testTag("acc_merge_keep")) {
                Text(stringResource(R.string.acc_merge_keep))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoose(false) }, enabled = !busy, modifier = Modifier.testTag("acc_merge_replace")) {
                Text(stringResource(R.string.acc_merge_replace), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun NewPasswordDialog(recovery: Boolean, onSave: suspend (String) -> UiText?, onDone: () -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(stringResource(if (recovery) R.string.acc_recovery_title else R.string.acc_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text(stringResource(R.string.acc_new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("acc_new_password"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val e = onSave(pw)
                        busy = false
                        if (e == null) onDone() else error = e.resolve(context)
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDone, enabled = !busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SignOutDialog(onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var wipe by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.acc_sign_out)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.acc_sign_out_body))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = wipe, role = Role.Checkbox, onValueChange = { wipe = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = wipe, onCheckedChange = null)
                    Spacer(Modifier.size(Spacing.sm))
                    Text(stringResource(R.string.acc_sign_out_wipe))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(wipe) }, modifier = Modifier.testTag("acc_sign_out_confirm")) {
                Text(stringResource(R.string.acc_sign_out), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
