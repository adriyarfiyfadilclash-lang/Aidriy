package com.aidriy.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aidriy.app.ui.navigation.AidriyNavHost
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.navigation.navigateToTopLevel
import com.aidriy.app.ui.navigation.selectedIcon

@Composable
fun AidriyApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AidriyBottomBar(
                currentDestination = backStackEntry?.destination,
                onSelect = navController::navigateToTopLevel,
            )
        },
    ) { innerPadding ->
        AidriyNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun AidriyBottomBar(
    currentDestination: NavDestination?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        NavigationBar(
            containerColor = colors.surface,
            tonalElevation = 0.dp,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination.isOn(destination)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            // The visible label already names the destination.
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    ),
                    modifier = Modifier.testTag("nav_${destination.route}"),
                )
            }
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route == destination.route } == true
