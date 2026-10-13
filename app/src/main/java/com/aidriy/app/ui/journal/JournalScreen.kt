package com.aidriy.app.ui.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.AidriyTheme
import com.aidriy.app.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun JournalRoute(
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    JournalScreen(uiState = uiState)
}

/** Journal. Phase 2 adds writing today's entry, a date picker, history, and search. */
@Composable
fun JournalScreen(
    uiState: JournalUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .testTag("screen_journal"),
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_journal),
            subtitle = stringResource(R.string.journal_subtitle),
        )

        // Informational only: shows which date today's entry will belong to.
        AidriyCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(Spacing.lg)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = stringResource(R.string.journal_today_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = uiState.todayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        EmptyState(
            icon = TopLevelDestination.JOURNAL.icon,
            title = stringResource(R.string.journal_empty_title),
            body = stringResource(R.string.journal_empty_body),
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8FA, widthDp = 360)
@Composable
private fun JournalScreenPreview() {
    AidriyTheme {
        JournalScreen(
            uiState = JournalUiState(
                today = LocalDate.of(2026, 10, 14),
                todayLabel = "Rabu, 14 Oktober 2026",
            ),
        )
    }
}
