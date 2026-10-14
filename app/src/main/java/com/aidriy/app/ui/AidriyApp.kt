@file:OptIn(ExperimentalLayoutApi::class)

package com.aidriy.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aidriy.app.R
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.ui.navigation.AidriyNavHost
import com.aidriy.app.ui.navigation.Routes
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.navigation.navigateToTopLevel
import com.aidriy.app.ui.navigation.selectedIcon
import com.aidriy.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Something to open when the app is launched from a notification. */
sealed interface DeepLink {
    data class Note(val id: String) : DeepLink
    data object JournalToday : DeepLink
}

@Composable
fun AidriyApp(
    deepLink: DeepLink? = null,
    onDeepLinkHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Editors and secondary screens hide the bar: more room for writing and the keyboard.
    val showBottomBar = currentRoute == null || TopLevelDestination.entries.any { it.route == currentRoute }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(deepLink) {
        when (deepLink) {
            is DeepLink.Note -> navController.navigate(Routes.noteEditor(deepLink.id))
            DeepLink.JournalToday -> navController.navigate(Routes.journalEditor(LocalDate.now()))
            null -> return@LaunchedEffect
        }
        onDeepLinkHandled()
    }

    // Back closes the drawer first instead of leaving the screen.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    fun go(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.testTag("drawer")) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = Spacing.md),
                )
                DrawerItem(Icons.Outlined.Description, R.string.drawer_all_notes, "drawer_notes") {
                    scope.launch { drawerState.close() }
                    navController.navigateToTopLevel(TopLevelDestination.NOTES)
                }
                DrawerItem(Icons.Outlined.PushPin, R.string.drawer_favorites, "drawer_favorites") { go(Routes.notesList(NoteScope.FAVORITES)) }
                DrawerItem(Icons.Outlined.Archive, R.string.drawer_archive, "drawer_archive") { go(Routes.notesList(NoteScope.ARCHIVED)) }
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm, horizontal = 28.dp))
                DrawerItem(Icons.Outlined.Lightbulb, R.string.nav_ideas, "drawer_ideas") { go(Routes.IDEAS) }
                DrawerItem(Icons.Outlined.Book, R.string.nav_journal, "drawer_journal") { go(Routes.JOURNAL) }
                DrawerItem(Icons.Outlined.Dashboard, R.string.templates_title, "drawer_templates") { go(Routes.TEMPLATES) }
                DrawerItem(Icons.Outlined.Search, R.string.search_title, "drawer_search") { go(Routes.SEARCH) }
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm, horizontal = 28.dp))
                DrawerItem(Icons.Outlined.Forum, R.string.ai_history, "drawer_ai_history") { go(Routes.AI_HISTORY) }
                DrawerItem(Icons.Outlined.Settings, R.string.nav_settings, "drawer_settings") {
                    scope.launch { drawerState.close() }
                    navController.navigateToTopLevel(TopLevelDestination.SETTINGS)
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Only the status bar is applied here; the bottom bar and editors handle the bottom edge.
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (showBottomBar) {
                    AidriyBottomBar(
                        currentDestination = backStackEntry?.destination,
                        onSelect = navController::navigateToTopLevel,
                    )
                }
            },
        ) { innerPadding ->
            AidriyNavHost(
                navController = navController,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            )
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, labelRes: Int, tag: String? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(labelRes)) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .let { if (tag != null) it.testTag(tag) else it },
    )
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
                        indicatorColor = Color.Transparent,
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
