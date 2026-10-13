package com.aidriy.app.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.today
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.domain.model.JournalFilter
import com.aidriy.app.domain.repository.JournalRepository
import com.aidriy.app.ui.common.ListUiState
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.common.SEARCH_DEBOUNCE_MS
import com.aidriy.app.ui.common.toListState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Header state: today's local calendar date. */
data class JournalUiState(
    val today: LocalDate,
    val todayLabel: String,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val timeProvider: TimeProvider,
    private val journal: JournalRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    var query by mutableStateOf("")
        private set

    private val retryTrigger = MutableStateFlow(0)

    /** Today's entry, or null if none has been written yet. Follows the date across midnight. */
    val todayEntry: StateFlow<JournalEntry?> = _uiState
        .map { it.today }
        .distinctUntilChanged()
        .flatMapLatest { journal.observeByDate(it).catch { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val searchTerms: Flow<String> = snapshotFlow { query }
        .map(SearchQuery::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    /** History, newest date first. */
    val history: StateFlow<ListUiState<JournalEntry>> =
        combine(searchTerms, retryTrigger) { term, _ -> JournalFilter(query = term) }
            .flatMapLatest { filter ->
                journal.observeEntries(filter)
                    .map<List<JournalEntry>, ListUiState<JournalEntry>> {
                        it.toListState(isFiltered = filter.query.isNotEmpty())
                    }
                    .catch { emit(ListUiState.Error(LoadErrorText)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState.Loading)

    fun onQueryChange(value: String) {
        query = value
    }

    fun retry() = retryTrigger.update { it + 1 }

    /** Keeps "today" correct if the app stays open across midnight or the time zone changes. */
    fun refresh() {
        val next = buildState()
        if (next != _uiState.value) _uiState.value = next
    }

    private fun buildState(): JournalUiState {
        val today = timeProvider.today()
        return JournalUiState(today = today, todayLabel = IndonesianDateFormatter.formatLong(today))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                JournalViewModel(container.timeProvider, container.journalRepository)
            }
        }
    }
}
