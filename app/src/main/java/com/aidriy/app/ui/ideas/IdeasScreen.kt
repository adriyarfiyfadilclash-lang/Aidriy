package com.aidriy.app.ui.ideas

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
 * Ide. Phase 2 adds quick capture, status filters (Inbox, Dikembangkan, Selesai),
 * tags, search, and convert-to-note.
 */
@Composable
fun IdeasScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .testTag("screen_ideas"),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_ideas),
            subtitle = stringResource(R.string.ideas_subtitle),
        )
        EmptyState(
            icon = TopLevelDestination.IDEAS.icon,
            title = stringResource(R.string.ideas_empty_title),
            body = stringResource(R.string.ideas_empty_body),
            modifier = Modifier.padding(top = Spacing.xl),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8FA, widthDp = 360)
@Composable
private fun IdeasScreenPreview() {
    AidriyTheme { IdeasScreen() }
}
