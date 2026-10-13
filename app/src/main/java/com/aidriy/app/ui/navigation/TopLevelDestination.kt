package com.aidriy.app.ui.navigation

import androidx.annotation.StringRes
import com.aidriy.app.R

/**
 * The five primary destinations, in bottom-navigation order.
 * [route] values are stable identifiers; do not rename them once deep links exist.
 * Icons are mapped in the UI layer (see DestinationIcons.kt) so this stays plain Kotlin.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
) {
    HOME(route = "home", labelRes = R.string.nav_home),
    NOTES(route = "notes", labelRes = R.string.nav_notes),
    IDEAS(route = "ideas", labelRes = R.string.nav_ideas),
    JOURNAL(route = "journal", labelRes = R.string.nav_journal),
    INSIGHT(route = "insight", labelRes = R.string.nav_insight),
}
