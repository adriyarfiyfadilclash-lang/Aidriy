package com.aidriy.app.ui.navigation

import androidx.annotation.StringRes
import com.aidriy.app.R

/**
 * The four primary destinations, in bottom-navigation order.
 * [route] values are stable identifiers; do not rename them once deep links exist.
 * Icons are mapped in the UI layer (see DestinationIcons.kt) so this stays plain Kotlin.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    NOTES(route = "catatan", labelRes = R.string.nav_notes),
    AI(route = "ai", labelRes = R.string.nav_ai),
    FILES(route = "berkas", labelRes = R.string.nav_files),
    SETTINGS(route = "pengaturan", labelRes = R.string.nav_settings),
}
