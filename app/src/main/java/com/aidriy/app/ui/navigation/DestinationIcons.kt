package com.aidriy.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Filled icon for the selected tab. */
val TopLevelDestination.selectedIcon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Filled.Home
        TopLevelDestination.NOTES -> Icons.Filled.Description
        TopLevelDestination.AI -> Icons.Filled.AutoAwesome
        TopLevelDestination.FILES -> Icons.Filled.Folder
        TopLevelDestination.SETTINGS -> Icons.Filled.Settings
    }

/** Outlined icon for unselected tabs. */
val TopLevelDestination.icon: ImageVector
    get() = when (this) {
        TopLevelDestination.HOME -> Icons.Outlined.Home
        TopLevelDestination.NOTES -> Icons.Outlined.Description
        TopLevelDestination.AI -> Icons.Outlined.AutoAwesome
        TopLevelDestination.FILES -> Icons.Outlined.Folder
        TopLevelDestination.SETTINGS -> Icons.Outlined.Settings
    }
