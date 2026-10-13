package com.aidriy.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.ui.graphics.vector.ImageVector

/** Filled icon for the selected tab, also used wherever the destination is referenced. */
val TopLevelDestination.selectedIcon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Filled.Home
        TopLevelDestination.NOTES -> Icons.Filled.Description
        TopLevelDestination.IDEAS -> Icons.Filled.Lightbulb
        TopLevelDestination.JOURNAL -> Icons.Filled.Book
        TopLevelDestination.INSIGHT -> Icons.Filled.AutoAwesome
    }

/** Outlined icon for unselected tabs and in-content references. */
val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Outlined.Home
        TopLevelDestination.NOTES -> Icons.Outlined.Description
        TopLevelDestination.IDEAS -> Icons.Outlined.Lightbulb
        TopLevelDestination.JOURNAL -> Icons.Outlined.Book
        TopLevelDestination.INSIGHT -> Icons.Outlined.AutoAwesome
    }
