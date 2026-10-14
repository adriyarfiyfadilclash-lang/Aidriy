package com.aidriy.app.ui.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.Folder
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
import com.aidriy.app.ui.navigation.Routes
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter chips on the Catatan list: Semua · Disematkan · Folder · Tag · Arsip. */
enum class NoteListMode { ALL, PINNED, FOLDER, TAG, ARCHIVED }

data class NoteListOptions(
    val mode: NoteListMode = NoteListMode.ALL,
    val sort: SortOrder = SortOrder.UPDATED,
    val tagId: String? = null,
    val folderId: String? = null,
) {
    fun toFilter(query: String) = NoteFilter(
        scope = when (mode) {
            NoteListMode.PINNED -> NoteScope.FAVORITES
            NoteListMode.ARCHIVED -> NoteScope.ARCHIVED
            else -> NoteScope.ACTIVE
        },
        query = query,
        tagId = tagId.takeIf { mode == NoteListMode.TAG },
        folderId = folderId.takeIf { mode == NoteListMode.FOLDER },
        sort = sort,
    )
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val notes: NoteRepository,
    tags: TagRepository,
    initialScope: NoteScope = NoteScope.ACTIVE,
    defaultSort: Flow<SortOrder>? = null,
    folders: Flow<List<Folder>> = flowOf(emptyList()),
    private val createFolder: (suspend (String) -> DataResult<Folder>)? = null,
) : ViewModel() {

    /** Text field state lives in Compose state so typing never lags or jumps the cursor. */
    var query by mutableStateOf("")
        private set

    private val options = MutableStateFlow(
        NoteListOptions(
            mode = when (initialScope) {
                NoteScope.FAVORITES -> NoteListMode.PINNED
                NoteScope.ARCHIVED -> NoteListMode.ARCHIVED
                NoteScope.ACTIVE -> NoteListMode.ALL
            },
        ),
    )
    val listOptions: StateFlow<NoteListOptions> = options.asStateFlow()

    private val retryTrigger = MutableStateFlow(0)

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    /** Until the user picks a sort on this screen, follow the default from Pengaturan. */
    private var sortChosenHere = false

    init {
        if (defaultSort != null) {
            viewModelScope.launch {
                defaultSort.catch { }.collect { sort ->
                    if (!sortChosenHere) options.update { it.copy(sort = sort) }
                }
            }
        }
    }

    private val searchTerms: Flow<String> = snapshotFlow { query }
        .map(SearchQuery::normalize)
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    val uiState: StateFlow<ListUiState<Note>> =
        combine(searchTerms, options, retryTrigger) { term, opts, _ -> opts.toFilter(term) }
            .flatMapLatest { filter ->
                val filtered = filter.query.isNotEmpty() || filter.tagId != null || filter.folderId != null ||
                    filter.scope != NoteScope.ACTIVE
                notes.observeNotes(filter)
                    .map<List<Note>, ListUiState<Note>> { it.toListState(isFiltered = filtered) }
                    .catch { emit(ListUiState.Error(LoadErrorText)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState.Loading)

    val availableTags: StateFlow<List<Tag>> = tags.observeUsedBy(TaggableKind.NOTE)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderList: StateFlow<List<Folder>> = folders
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query = value
    }

    fun setMode(mode: NoteListMode) = options.update { current ->
        when (mode) {
            // Opening Folder/Tag picks the first one so the list is never ambiguous.
            NoteListMode.FOLDER -> current.copy(mode = mode, folderId = current.folderId ?: folderList.value.firstOrNull()?.id)
            NoteListMode.TAG -> current.copy(mode = mode, tagId = current.tagId ?: availableTags.value.firstOrNull()?.id)
            else -> current.copy(mode = mode)
        }
    }

    fun setScope(scope: NoteScope) = setMode(
        when (scope) {
            NoteScope.FAVORITES -> NoteListMode.PINNED
            NoteScope.ARCHIVED -> NoteListMode.ARCHIVED
            NoteScope.ACTIVE -> NoteListMode.ALL
        },
    )

    fun setSort(sort: SortOrder) {
        sortChosenHere = true
        options.update { it.copy(sort = sort) }
    }

    fun setTag(tagId: String?) = options.update { it.copy(tagId = tagId) }
    fun setFolder(folderId: String?) = options.update { it.copy(folderId = folderId) }
    fun retry() = retryTrigger.update { it + 1 }

    fun toggleFavorite(note: Note) = report { notes.setFavorite(note.id, !note.isFavorite) }

    fun archive(note: Note) = report { notes.setArchived(note.id, true) }

    fun restore(note: Note) = report { notes.setArchived(note.id, false) }

    fun delete(note: Note) = report { notes.delete(note.id) }

    fun moveToFolder(note: Note, folderId: String?) = report(UiText(R.string.note_moved)) { notes.setFolder(note.id, folderId) }

    /** Creates a folder and, when [note] is given, moves the note into it. */
    fun createFolderAndMove(name: String, note: Note?) {
        val create = createFolder ?: return
        viewModelScope.launch {
            when (val result = create(name)) {
                is DataResult.Success -> {
                    if (note != null) {
                        val moved = notes.setFolder(note.id, result.value.id)
                        if (moved is DataResult.Failure) messageChannel.send(moved.error.toUiText())
                        else messageChannel.send(UiText(R.string.note_moved))
                    } else {
                        options.update { it.copy(mode = NoteListMode.FOLDER, folderId = result.value.id) }
                        messageChannel.send(UiText(R.string.files_folder_created))
                    }
                }
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
            }
        }
    }

    val canCreateFolders: Boolean get() = createFolder != null

    private fun report(success: UiText? = null, action: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            when (val result = action()) {
                is DataResult.Failure -> messageChannel.send(result.error.toUiText())
                is DataResult.Success -> success?.let { messageChannel.send(it) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val scope = createSavedStateHandle().get<String>(Routes.ARG_SCOPE)
                    ?.let { value -> NoteScope.entries.firstOrNull { it.name == value } }
                    ?: NoteScope.ACTIVE
                NotesViewModel(
                    notes = container.noteRepository,
                    tags = container.tagRepository,
                    initialScope = scope,
                    defaultSort = container.settingsRepository.settings.map { it.noteSort }.distinctUntilChanged(),
                    folders = container.fileRepository.observeFolders(),
                    createFolder = container.fileRepository::createFolder,
                )
            }
        }
    }
}
