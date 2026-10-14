package com.aidriy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.aidriy.app.data.reminders.Notifications
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.data.settings.ThemeMode
import com.aidriy.app.ui.AidriyApp
import com.aidriy.app.ui.DeepLink
import com.aidriy.app.ui.SplashScreen
import com.aidriy.app.ui.lock.LockScreen
import com.aidriy.app.ui.theme.AidriyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import android.graphics.Color as AndroidColor

/** FragmentActivity (a ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {

    private val deepLinks = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLinks.value = intent.toDeepLink()
        val container = (application as AidriyApplication).container

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(initial = null)
            val locked by container.appLock.locked.collectAsState()
            val lockReady by container.appLock.ready.collectAsState()
            val deepLink by deepLinks.collectAsState()
            var splash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MILLIS)
                splash = false
            }

            val s = settings ?: AppSettings()
            val dark = when (s.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AidriyTheme(themeMode = s.themeMode, fontScale = s.textSize.scale) {
                val stage = when {
                    splash || settings == null || !lockReady -> Stage.SPLASH
                    locked -> Stage.LOCKED
                    else -> Stage.APP
                }
                Crossfade(targetState = stage, label = "root") { current ->
                    when (current) {
                        Stage.SPLASH -> SplashScreen()
                        Stage.LOCKED -> LockScreen(container.appLock, s.biometricEnabled)
                        Stage.APP -> AidriyApp(
                            deepLink = deepLink,
                            onDeepLinkHandled = { deepLinks.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toDeepLink()?.let { deepLinks.value = it }
    }

    private fun Intent?.toDeepLink(): DeepLink? = when {
        this == null -> null
        getStringExtra(Notifications.EXTRA_OPEN_NOTE) != null -> DeepLink.Note(getStringExtra(Notifications.EXTRA_OPEN_NOTE)!!)
        getBooleanExtra(Notifications.EXTRA_OPEN_JOURNAL_TODAY, false) -> DeepLink.JournalToday
        else -> null
    }

    private enum class Stage { SPLASH, LOCKED, APP }

    private companion object {
        const val SPLASH_MILLIS = 900L
    }
}
