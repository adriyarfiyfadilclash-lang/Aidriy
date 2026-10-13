package com.aidriy.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.SearchResults
import com.aidriy.app.domain.repository.SearchRepository
import com.aidriy.app.ui.common.SEARCH_DEBOUNCE_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface SearchUiState {
    /** No query typed yet. */
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val query: String, val results: SearchResults) : SearchUiState
    data class NoResults(val query: String) : SearchUiState
    data object Error : SearchUiState
}

/** Global offline search across notes, ideas and journal entries. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: SearchRepository,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    val uiState: StateFlow<SearchUiState> = snapshotFlow { query }
        .map(SearchQuery::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { term ->
            if (term.isEmpty()) {
                flowOf(SearchUiState.Idle)
            } else {
                search.search(term)
                    .map<SearchResults, SearchUiState> { results ->
                        if (results.isEmpty) SearchUiState.NoResults(term) else SearchUiState.Results(term, results)
                    }
                    .onStart { emit(SearchUiState.Loading) }
                    .catch { emit(SearchUiState.Error) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    fun onQueryChange(value: String) {
        query = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(appContainer().searchRepository) }
        }
    }
}
