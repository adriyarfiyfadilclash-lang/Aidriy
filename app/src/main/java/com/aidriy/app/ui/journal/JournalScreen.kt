@file:OptIn(ExperimentalMaterial3Api::class)

package com.aidriy.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.core.time.DatePickerDates
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.ui.common.ListUiState
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.components.AidriyCard
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.ScreenHeader
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.components.SectionHeader
import com.aidriy.app.ui.navigation.TopLevelDestination
import com.aidriy.app.ui.navigation.icon
import com.aidriy.app.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun JournalRoute(
    onOpenDate: (LocalDate) -> Unit,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val todayEntry by viewModel.todayEntry.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    JournalScreen(
        uiState = uiState,
        todayEntry = todayEntry,
        history = history,
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onOpenDate = onOpenDate,
    )
}

@Composable
fun JournalScreen(
    uiState: JournalUiState,
    todayEntry: JournalEntry?,
    history: ListUiState<JournalEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_journal"),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.nav_journal),
                subtitle = stringResource(R.string.journal_subtitle),
            )
        }
        item {
            TodayCard(
                todayLabel = uiState.todayLabel,
                entry = todayEntry,
                onClick = { onOpenDate(uiState.today) },
            )
        }
        item {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("journal_pick_date"),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Text(stringResource(R.string.journal_pick_date), modifier = Modifier.padding(start = Spacing.sm))
            }
        }
        item { SectionHeader(title = stringResource(R.string.journal_history)) }
        item {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.journal_search_hint),
                modifier = Modifier.testTag("journal_search"),
            )
        }
        when (history) {
            ListUiState.Loading -> item { LoadingState() }
            is ListUiState.Error -> item { ErrorState(history.message.asString(), onRetry) }
            is ListUiState.Empty -> item {
                if (history.isFiltered) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.no_results_title),
                        body = stringResource(R.string.no_results_body),
                    )
                } else {
                    EmptyState(
                        icon = TopLevelDestination.JOURNAL.icon,
                        title = stringResource(R.string.journal_empty_title),
                        body = stringResource(R.string.journal_empty_body),
                    )
                }
            }
            is ListUiState.Success -> items(history.items, key = { it.id }) { entry ->
                RecordCard(
                    title = IndonesianDateFormatter.formatLong(entry.date),
                    preview = listOf(entry.title, entry.content).filter { it.isNotBlank() }.joinToString(" — "),
                    meta = stringResource(R.string.meta_updated, formatTimestamp(entry.updatedAt)),
                    tags = entry.tags,
                    onClick = { onOpenDate(entry.date) },
                    modifier = Modifier.testTag("journal_item"),
                )
            }
        }
    }

    if (showDatePicker) {
        JournalDatePicker(
            today = uiState.today,
            onConfirm = { date ->
                showDatePicker = false
                onOpenDate(date)
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun TodayCard(todayLabel: String, entry: JournalEntry?, onClick: () -> Unit) {
    AidriyCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("journal_today"),
        onClick = onClick,
    ) {
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
                text = todayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            val preview = entry?.let { listOf(it.title, it.content).firstOrNull { s -> s.isNotBlank() } }
            if (preview != null) {
                Text(
                    text = preview.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Text(
                text = stringResource(if (entry == null) R.string.journal_today_write else R.string.journal_today_open),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/**
 * Material DatePicker works in UTC day millis; DatePickerDates converts without shifting the day.
 * Future dates are not selectable because a journal entry describes a day that has happened.
 */
@Composable
private fun JournalDatePicker(
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !DatePickerDates.fromPickerMillis(utcTimeMillis).isAfter(today)

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = DatePickerDates.toPickerMillis(today),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(DatePickerDates.fromPickerMillis(it)) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
