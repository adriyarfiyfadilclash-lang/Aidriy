package com.aidriy.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidriy.app.R
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.domain.model.SearchResults
import com.aidriy.app.ui.common.formatTimestamp
import com.aidriy.app.ui.components.EditorScaffold
import com.aidriy.app.ui.components.EmptyState
import com.aidriy.app.ui.components.ErrorState
import com.aidriy.app.ui.components.LoadingState
import com.aidriy.app.ui.components.RecordCard
import com.aidriy.app.ui.components.SearchField
import com.aidriy.app.ui.components.SectionHeader
import com.aidriy.app.ui.ideas.labelRes
import com.aidriy.app.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenIdea: (String) -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    EditorScaffold(
        title = stringResource(R.string.search_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = Modifier.testTag("screen_search"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.home_search_hint),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag("search_input"),
                )
            }
            when (val state = uiState) {
                SearchUiState.Idle -> item {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.search_idle_title),
                        body = stringResource(R.string.search_idle_body),
                    )
                }
                SearchUiState.Loading -> item { LoadingState() }
                SearchUiState.Error -> item { ErrorState(stringResource(R.string.error_load), onRetry = null) }
                is SearchUiState.NoResults -> item {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_no_results, state.query),
                        body = stringResource(R.string.no_results_body),
                    )
                }
                is SearchUiState.Results -> results(state.results, onOpenNote, onOpenIdea, onOpenJournal)
            }
        }
    }
}

private fun LazyListScope.results(
    results: SearchResults,
    onOpenNote: (String) -> Unit,
    onOpenIdea: (String) -> Unit,
    onOpenJournal: (LocalDate) -> Unit,
) {
    if (results.notes.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.search_section_notes)) }
        items(results.notes, key = { "n_${it.id}" }) { note ->
            RecordCard(
                title = note.title,
                preview = note.content,
                meta = stringResource(R.string.meta_updated, formatTimestamp(note.updatedAt)),
                badge = if (note.isArchived) stringResource(R.string.notes_scope_archived) else null,
                tags = note.tags,
                onClick = { onOpenNote(note.id) },
                modifier = Modifier.testTag("search_result_note"),
            )
        }
    }
    if (results.ideas.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.search_section_ideas)) }
        items(results.ideas, key = { "i_${it.id}" }) { idea ->
            RecordCard(
                title = idea.title,
                preview = idea.content,
                meta = stringResource(R.string.meta_updated, formatTimestamp(idea.updatedAt)),
                badge = stringResource(idea.status.labelRes()),
                tags = idea.tags,
                onClick = { onOpenIdea(idea.id) },
                modifier = Modifier.testTag("search_result_idea"),
            )
        }
    }
    if (results.journalEntries.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.search_section_journal)) }
        items(results.journalEntries, key = { "j_${it.id}" }) { entry ->
            RecordCard(
                title = IndonesianDateFormatter.formatLong(entry.date),
                preview = listOf(entry.title, entry.content).filter { it.isNotBlank() }.joinToString(" — "),
                meta = stringResource(R.string.meta_updated, formatTimestamp(entry.updatedAt)),
                tags = entry.tags,
                onClick = { onOpenJournal(entry.date) },
                modifier = Modifier.testTag("search_result_journal"),
            )
        }
    }
}
