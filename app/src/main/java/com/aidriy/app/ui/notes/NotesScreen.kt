package com.aidriy.app.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aidriy.app.R
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.AidriyTheme
import com.aidriy.app.ui.theme.Spacing

/**
 * Catatan. Phase 1 shows the layout and empty state only; creation, editing, search,
 * favorites, and archive arrive with the Room data layer in Phase 2. No action controls
 * are shown until they work.
 */
@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .testTag("screen_notes"),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_notes),
            subtitle = stringResource(R.string.notes_subtitle),
        )
        EmptyState(
            icon = TopLevelDestination.NOTES.icon,
            title = stringResource(R.string.notes_empty_title),
            body = stringResource(R.string.notes_empty_body),
            modifier = Modifier.padding(top = Spacing.xl),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8FA, widthDp = 360)
@Composable
private fun NotesScreenPreview() {
    AidriyTheme { NotesScreen() }
}
