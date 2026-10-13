package com.aidriy.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.domain.model.RecentItems
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.IconBadge
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.components.SectionHeader
import com.aidriy.app.ui.ideas.labelRes
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.AidriyTheme
import com.aidriy.app.ui.theme.Spacing
import java.time.LocalDate

/** Navigation callbacks from Beranda. */
data class HomeActions(
    val openDestination: (TopLevelDestination) -> Unit,
    val openSearch: () -> Unit,
    val createNote: () -> Unit,
    val openNote: (String) -> Unit,
    val createIdea: () -> Unit,
    val openIdea: (String) -> Unit,
    val openJournal: (LocalDate) -> Unit,
)

/** Stateful entry point: wires the ViewModel to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    HomeScreen(uiState = uiState, content = content, actions = actions)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    content: HomeContentState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_home"),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.xl,
        ),
    ) {
        item {
            ScreenHeader(
                title = stringResource(uiState.greeting.labelRes()),
                subtitle = uiState.todayLabel,
            )
        }
        item { SearchEntry(onClick = actions.openSearch) }

        item { SectionHeader(title = stringResource(R.string.home_quick_access)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                QuickActionCard(
                    icon = TopLevelDestination.NOTES.icon,
                    labelRes = R.string.home_quick_notes,
                    testTag = "quick_notes",
                    onClick = actions.createNote,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    icon = TopLevelDestination.IDEAS.icon,
                    labelRes = R.string.home_quick_ideas,
                    testTag = "quick_ideas",
                    onClick = actions.createIdea,
                    modifier = Modifier.weight(1f),
                )
                QuickActionCard(
                    icon = TopLevelDestination.JOURNAL.icon,
                    labelRes = R.string.home_quick_journal,
                    testTag = "quick_journal",
                    onClick = { actions.openJournal(uiState.today) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            InsightEntryCard(
                onClick = { actions.openDestination(TopLevelDestination.INSIGHT) },
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        when (content) {
            HomeContentState.Loading -> item { LoadingState() }
            HomeContentState.Error -> item {
                ErrorState(stringResource(R.string.error_load), onRetry = null)
            }
            is HomeContentState.Loaded -> recentSections(content.items, actions)
        }
    }
}

private fun LazyListScope.recentSections(recent: RecentItems, actions: HomeActions) {
    if (recent.isEmpty) {
        item {
            EmptyState(
                icon = TopLevelDestination.NOTES.icon,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
        return
    }
    if (recent.notes.isNotEmpty()) {
        item {
            SectionWithAction(R.string.home_recent_notes) {
                actions.openDestination(TopLevelDestination.NOTES)
            }
        }
        items(recent.notes, key = { "note_${it.id}" }) { note ->
            RecordCard(
                title = note.title,
                preview = note.content,
                meta = stringResource(R.string.meta_updated, formatTimestamp(note.updatedAt)),
                onClick = { actions.openNote(note.id) },
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
    if (recent.ideas.isNotEmpty()) {
        item {
            SectionWithAction(R.string.home_recent_ideas) {
                actions.openDestination(TopLevelDestination.IDEAS)
            }
        }
        items(recent.ideas, key = { "idea_${it.id}" }) { idea ->
            RecordCard(
                title = idea.title,
                preview = idea.content,
                meta = stringResource(R.string.meta_updated, formatTimestamp(idea.updatedAt)),
                badge = stringResource(idea.status.labelRes()),
                onClick = { actions.openIdea(idea.id) },
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
    if (recent.journalEntries.isNotEmpty()) {
        item {
            SectionWithAction(R.string.home_recent_journal) {
                actions.openDestination(TopLevelDestination.JOURNAL)
            }
        }
        items(recent.journalEntries, key = { "journal_${it.id}" }) { entry ->
            RecordCard(
                title = IndonesianDateFormatter.formatLong(entry.date),
                preview = listOf(entry.title, entry.content).filter { it.isNotBlank() }.joinToString(" — "),
                meta = stringResource(R.string.meta_updated, formatTimestamp(entry.updatedAt)),
                onClick = { actions.openJournal(entry.date) },
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
}

@Composable
private fun SectionWithAction(@StringRes titleRes: Int, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onSeeAll) { Text(stringResource(R.string.home_see_all)) }
    }
}

/** Looks like a search field; opens the dedicated search screen. */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    AidriyCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("home_search"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.home_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    @StringRes labelRes: Int,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AidriyCard(
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag(testTag),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconBadge(icon = icon, size = 36.dp, iconSize = 18.dp)
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InsightEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AidriyCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_insight_entry"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.Outlined.AutoAwesome)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_insight_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_insight_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun Greeting.labelRes(): Int = when (this) {
    Greeting.PAGI -> R.string.greeting_pagi
    Greeting.SIANG -> R.string.greeting_siang
    Greeting.SORE -> R.string.greeting_sore
    Greeting.MALAM -> R.string.greeting_malam
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8FA, widthDp = 360, heightDp = 760)
@Composable
private fun HomeScreenEmptyPreview() {
    AidriyTheme {
        HomeScreen(
            uiState = HomeUiState(
                greeting = Greeting.PAGI,
                todayLabel = "Rabu, 14 Oktober 2026",
                today = LocalDate.of(2026, 10, 14),
            ),
            content = HomeContentState.Loaded(RecentItems.EMPTY),
            actions = HomeActions({}, {}, {}, {}, {}, {}, {}),
        )
    }
}
