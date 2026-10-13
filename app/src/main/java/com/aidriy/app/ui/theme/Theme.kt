package com.aidriy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

/**
 * AIDRIY theme. Light is the default product theme; dynamic color is intentionally
 * not used so the blue brand accent stays consistent across devices.
 * A dark scheme can be added later behind a user preference.
 */
@Composable
fun AidriyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AidriyTypography,
        shapes = AidriyShapes,
        content = content,
    )
}
