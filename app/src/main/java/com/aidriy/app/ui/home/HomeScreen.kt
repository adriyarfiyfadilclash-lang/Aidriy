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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.IconBadge
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.components.SectionHeader
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.AidriyTheme
import com.aidriy.app.ui.theme.Spacing

/** Stateful entry point: wires the ViewModel to the stateless [HomeScreen]. */
@Composable
fun HomeRoute(
    onOpenDestination: (TopLevelDestination) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    HomeScreen(uiState = uiState, onOpenDestination = onOpenDestination)
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onOpenDestination: (TopLevelDestination) -> Unit,
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

        item { SectionHeader(title = stringResource(R.string.home_quick_access)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                QuickAccessCard(
                    destination = TopLevelDestination.NOTES,
                    labelRes = R.string.home_quick_notes,
                    onClick = onOpenDestination,
                    modifier = Modifier.weight(1f),
                )
                QuickAccessCard(
                    destination = TopLevelDestination.IDEAS,
                    labelRes = R.string.home_quick_ideas,
                    onClick = onOpenDestination,
                    modifier = Modifier.weight(1f),
                )
                QuickAccessCard(
                    destination = TopLevelDestination.JOURNAL,
                    labelRes = R.string.home_quick_journal,
                    onClick = onOpenDestination,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            InsightEntryCard(
                onClick = { onOpenDestination(TopLevelDestination.INSIGHT) },
                modifier = Modifier.padding(top = Spacing.md),
            )
        }

        item {
            EmptyState(
                icon = TopLevelDestination.NOTES.icon,
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    destination: TopLevelDestination,
    @StringRes labelRes: Int,
    onClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    AidriyCard(
        modifier = modifier
            .heightIn(min = 96.dp)
            .testTag("quick_${destination.route}"),
        onClick = { onClick(destination) },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            IconBadge(icon = destination.icon, size = 36.dp, iconSize = 18.dp)
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
private fun HomeScreenPreview() {
    AidriyTheme {
        HomeScreen(
            uiState = HomeUiState(greeting = Greeting.PAGI, todayLabel = "Rabu, 14 Oktober 2026"),
            onOpenDestination = {},
        )
    }
}
