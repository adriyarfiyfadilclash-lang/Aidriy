package com.aidriy.app.ui.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.aidriy.app.R
import com.aidriy.app.data.security.AppLockManager
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Full-screen PIN pad shown over the app while it is locked. */
@Composable
fun LockScreen(lock: AppLockManager, biometricEnabled: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    val canUseBiometric = remember(biometricEnabled) {
        biometricEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val promptBiometric: () -> Unit = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        lock.unlockWithBiometric()
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.lock_biometric_title))
                    .setNegativeButtonText(context.getString(R.string.lock_use_pin))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build(),
            )
        }
    }
    LaunchedEffect(canUseBiometric) { if (canUseBiometric) promptBiometric() }

    fun press(digit: Char) {
        if (checking || pin.length >= 6) return
        error = false
        pin += digit
        if (pin.length >= 4) {
            val attempt = pin
            scope.launch {
                checking = true
                // Try at 4, 5 and 6 digits: PINs may be 4–6 long.
                if (lock.tryPin(attempt)) {
                    pin = ""
                } else if (attempt.length == 6) {
                    error = true
                    pin = ""
                }
                checking = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .testTag("screen_lock"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.lock_enter_pin), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(6) { i ->
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }
            Text(
                if (error) stringResource(R.string.lock_wrong_pin) else " ",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    row.forEach { d -> Key(d.toString()) { press(d) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    if (canUseBiometric) {
                        IconButton(onClick = promptBiometric) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = stringResource(R.string.lock_biometric), modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Key("0") { press('0') }
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                        Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.lock_delete_digit))
                    }
                }
            }
            Spacer(Modifier.padding(Spacing.sm))
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .size(72.dp)
            .testTag("pin_key_$label"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
