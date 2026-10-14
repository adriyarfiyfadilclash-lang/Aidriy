package com.aidriy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.aidriy.app.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = Neutral0,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue900,
    secondary = Neutral500,
    onSecondary = Neutral0,
    secondaryContainer = Neutral50,
    onSecondaryContainer = Neutral900,
    background = Neutral25,
    onBackground = Neutral900,
    surface = Neutral0,
    onSurface = Neutral900,
    surfaceVariant = Neutral50,
    onSurfaceVariant = Neutral500,
    surfaceTint = Blue600,
    surfaceContainerLowest = Neutral0,
    surfaceContainerLow = Neutral25,
    surfaceContainer = Neutral50,
    surfaceContainerHigh = Neutral100,
    surfaceContainerHighest = Neutral100,
    outline = Neutral200,
    outlineVariant = Neutral100,
    error = Red600,
    onError = Neutral0,
    errorContainer = Red50,
    onErrorContainer = Red600,
)

private val DarkColors = darkColorScheme(
    primary = Blue400,
    onPrimary = Dark0,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkMuted,
    onSecondary = Dark0,
    secondaryContainer = Dark100,
    onSecondaryContainer = DarkText,
    background = Dark0,
    onBackground = DarkText,
    surface = Dark50,
    onSurface = DarkText,
    surfaceVariant = Dark100,
    onSurfaceVariant = DarkMuted,
    surfaceTint = Blue400,
    surfaceContainerLowest = Dark0,
    surfaceContainerLow = Dark50,
    surfaceContainer = Dark100,
    surfaceContainerHigh = Dark200,
    surfaceContainerHighest = Dark200,
    outline = Dark300,
    outlineVariant = Dark200,
    error = RedDark,
    onError = Dark0,
    errorContainer = DarkErrorContainer,
    onErrorContainer = RedDark,
)

private val LocalIsDark = staticCompositionLocalOf { false }

object AidriyColors {
    /** Accent tile colors for the current theme. */
    val accents: List<Accent>
        @Composable @ReadOnlyComposable get() = if (LocalIsDark.current) Accents.dark else Accents.light

    @Composable
    @ReadOnlyComposable
    fun accent(key: AccentKey): Accent = accents[key.ordinal]

    /** Stable color per record id, so a note keeps its icon color. */
    @Composable
    @ReadOnlyComposable
    fun accentFor(seed: String): Accent = accents[Math.floorMod(seed.hashCode(), accents.size)]

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

/**
 * AIDRIY theme. Light by default; users can pick Gelap or follow the system in Pengaturan → Tampilan.
 * [fontScale] multiplies the system font scale (Pengaturan → Ukuran teks).
 */
@Composable
fun AidriyTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalDensity provides Density(density.density, density.fontScale * fontScale),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AidriyTypography,
            shapes = AidriyShapes,
            content = content,
        )
    }
}
