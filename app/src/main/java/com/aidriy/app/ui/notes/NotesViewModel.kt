package com.aidriy.app.ui.notes

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
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteFilter
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.model.TaggableKind
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.TagRepository
import com.aidriy.app.ui.common.ListUiState
import com.aidriy.app.ui.common.LoadErrorText
import com.aidriy.app.ui.common.SEARCH_DEBOUNCE_MS
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toListState
import com.aidriy.app.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteListOptions(
    val scope: NoteScope = NoteScope.ACTIVE,
    val sort: SortOrder = SortOrder.UPDATED,
    val tagId: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val notes: NoteRepository,
    tags: TagRepository,
) : ViewModel() {

    /** Text field state lives in Compose state so typing never lags or jumps the cursor. */
    var query by mutableStateOf("")
        private set

    private val options = MutableStateFlow(NoteListOptions())
    val listOptions: StateFlow<NoteListOptions> = options.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    private val searchTerms: Flow<String> = snapshotFlow { query }
        .map(SearchQuery::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val uiState: StateFlow<ListUiState<Note>> =
        combine(searchTerms, options, retryTrigger) { term, opts, _ ->
            NoteFilter(scope = opts.scope, query = term, tagId = opts.tagId, sort = opts.sort)
        }
            .flatMapLatest { filter ->
                val filtered = filter.query.isNotEmpty() || filter.tagId != null || filter.scope != NoteScope.ACTIVE
                notes.observeNotes(filter)
                    .map<List<Note>, ListUiState<Note>> { it.toListState(isFiltered = filtered) }
                    .catch { emit(ListUiState.Error(LoadErrorText)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState.Loading)

    val availableTags: StateFlow<List<Tag>> = tags.observeUsedBy(TaggableKind.NOTE)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query = value
    }

    fun setScope(scope: NoteScope) = options.update { it.copy(scope = scope) }
    fun setSort(sort: SortOrder) = options.update { it.copy(sort = sort) }
    fun setTag(tagId: String?) = options.update { it.copy(tagId = tagId) }
    fun retry() = retryTrigger.update { it + 1 }

    fun toggleFavorite(note: Note) {
        viewModelScope.launch {
            val result = notes.setFavorite(note.id, !note.isFavorite)
            if (result is DataResult.Failure) messageChannel.send(result.error.toUiText())
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                NotesViewModel(container.noteRepository, container.tagRepository)
            }
        }
    }
}
