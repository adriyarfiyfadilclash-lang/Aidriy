package com.aidriy.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-based spacing scale. Use these instead of raw dp values in screens. */
object Spacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Horizontal padding for every top-level screen. */
    val screenHorizontal: Dp = 20.dp

    /** Minimum touch target recommended by Material and Android accessibility guidance. */
    val minTouchTarget: Dp = 48.dp
}
