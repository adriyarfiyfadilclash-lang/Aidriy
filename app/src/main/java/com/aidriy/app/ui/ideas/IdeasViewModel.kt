package com.aidriy.app.ui.ideas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaFilter
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.model.TaggableKind
import com.aidriy.app.domain.repository.IdeaRepository
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

data class IdeaListOptions(
    /** null = all statuses. */
    val status: IdeaStatus? = null,
    val sort: SortOrder = SortOrder.UPDATED,
    val tagId: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class IdeasViewModel(
    private val ideas: IdeaRepository,
    tags: TagRepository,
) : ViewModel() {

    var query by mutableStateOf("")
        private set

    /** Quick-capture input. Kept after a failed save so nothing typed is lost. */
    var captureText by mutableStateOf("")
        private set
    var captureError by mutableStateOf<UiText?>(null)
        private set
    var isCapturing by mutableStateOf(false)
        private set

    private val options = MutableStateFlow(IdeaListOptions())
    val listOptions: StateFlow<IdeaListOptions> = options.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    private val searchTerms: Flow<String> = snapshotFlow { query }
        .map(SearchQuery::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val uiState: StateFlow<ListUiState<Idea>> =
        combine(searchTerms, options, retryTrigger) { term, opts, _ ->
            IdeaFilter(status = opts.status, query = term, tagId = opts.tagId, sort = opts.sort)
        }
            .flatMapLatest { filter ->
                val filtered = filter.query.isNotEmpty() || filter.tagId != null || filter.status != null
                ideas.observeIdeas(filter)
                    .map<List<Idea>, ListUiState<Idea>> { it.toListState(isFiltered = filtered) }
                    .catch { emit(ListUiState.Error(LoadErrorText)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState.Loading)

    val availableTags: StateFlow<List<Tag>> = tags.observeUsedBy(TaggableKind.IDEA)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query = value
    }

    fun onCaptureTextChange(value: String) {
        captureText = value
        captureError = null
    }

    /** One-step capture: the idea is stored in Inbox; status and details can be edited later. */
    fun capture() {
        if (isCapturing) return
        val text = captureText
        viewModelScope.launch {
            isCapturing = true
            when (val result = ideas.quickCapture(text)) {
                is DataResult.Success -> {
                    // Only clear if the user didn't keep typing while saving.
                    if (captureText == text) captureText = ""
                    captureError = null
                    messageChannel.send(UiText(R.string.ideas_captured))
                }
                is DataResult.Failure -> captureError = result.error.toUiText()
            }
            isCapturing = false
        }
    }

    fun setStatus(status: IdeaStatus?) = options.update { it.copy(status = status) }
    fun setSort(sort: SortOrder) = options.update { it.copy(sort = sort) }
    fun setTag(tagId: String?) = options.update { it.copy(tagId = tagId) }
    fun retry() = retryTrigger.update { it + 1 }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                IdeasViewModel(container.ideaRepository, container.tagRepository)
            }
        }
    }
}
