package com.aidriy.app.ui.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.repository.IdeaRepository
import com.aidriy.app.domain.repository.TagRepository
import com.aidriy.app.ui.common.EditorDraft
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeaEditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    val ideaId: String? = null,
    val isSaving: Boolean = false,
    val isConverting: Boolean = false,
    val error: UiText? = null,
    val tagError: UiText? = null,
)

/** Same save conventions as the note editor; the idea's status is stored in [EditorDraft.extra]. */
class IdeaEditorViewModel(
    private val handle: SavedStateHandle,
    private val ideas: IdeaRepository,
    tags: TagRepository,
) : ViewModel() {

    val draft = EditorDraft(handle)

    private var ideaId: String? = handle.get<String>(KEY_SAVED_ID) ?: handle.get<String>(Routes.ARG_IDEA_ID)

    private val _uiState = MutableStateFlow(IdeaEditorUiState(ideaId = ideaId))
    val uiState: StateFlow<IdeaEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> = tags.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Current status; falls back to Inbox for display if saved state was somehow invalid. */
    val selectedStatus: IdeaStatus get() = IdeaStatus.fromStorageKey(draft.extra)

    init {
        val id = ideaId
        when {
            id == null -> {
                draft.startNew(extra = IdeaStatus.INBOX.storageKey)
                setStatus(EditorStatus.Ready)
            }
            !InputRules.isValidId(id) -> setStatus(EditorStatus.NotFound)
            !draft.isInitialized || draft.contentWasDropped -> load(id)
            else -> setStatus(EditorStatus.Ready)
        }
    }

    fun retryLoad() {
        ideaId?.let(::load)
    }

    fun onTitleChange(value: String) = draft.updateTitle(value)
    fun onContentChange(value: String) = draft.updateContent(value)
    fun onStatusChange(status: IdeaStatus) = draft.updateExtra(status.storageKey)

    fun addTag(name: String): Boolean = when (val result = draft.addTag(name)) {
        is DataResult.Success -> {
            _uiState.update { it.copy(tagError = null) }
            true
        }
        is DataResult.Failure -> {
            _uiState.update { it.copy(tagError = result.error.toUiText()) }
            false
        }
    }

    fun removeTag(name: String) {
        draft.removeTag(name)
        _uiState.update { it.copy(tagError = null) }
    }

    fun save(closeAfter: Boolean = false) {
        val state = _uiState.value
        if (state.isSaving || state.status != EditorStatus.Ready) return
        if (!draft.hasUnsavedChanges && ideaId != null) {
            if (closeAfter) eventChannel.trySend(EditorEvent.Close)
            return
        }
        val snapshot = draft.snapshot()
        // Strict validation: never persist an unknown status value.
        val status = IdeaStatus.fromStorageKeyOrNull(snapshot.extra)
        if (status == null) {
            _uiState.update { it.copy(error = DataError.InvalidIdeaStatus.toUiText()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val input = IdeaDraft(
                title = snapshot.title,
                content = snapshot.content,
                status = status,
                tagNames = snapshot.tags,
            )
            val id = ideaId
            val result = if (id == null) ideas.create(input) else ideas.update(id, input)
            when (result) {
                is DataResult.Success -> {
                    ideaId = result.value.id
                    handle[KEY_SAVED_ID] = result.value.id
                    draft.markSaved(snapshot)
                    _uiState.update { it.copy(isSaving = false, ideaId = result.value.id) }
                    eventChannel.send(
                        if (closeAfter) EditorEvent.Close else EditorEvent.Message(UiText(R.string.saved)),
                    )
                }
                is DataResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = result.error.toUiText())
                }
            }
        }
    }

    /**
     * Creates a note from the stored idea. Requires the idea to be saved without pending edits,
     * so the note matches what the user sees. The idea is kept.
     */
    fun convertToNote() {
        val id = ideaId
        val state = _uiState.value
        if (state.isConverting) return
        if (id == null || draft.hasUnsavedChanges) {
            eventChannel.trySend(EditorEvent.Message(UiText(R.string.idea_convert_needs_save)))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true) }
            when (val result = ideas.convertToNote(id)) {
                is DataResult.Success -> eventChannel.send(EditorEvent.OpenNote(result.value.id))
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
            _uiState.update { it.copy(isConverting = false) }
        }
    }

    fun delete() {
        val id = ideaId
        if (id == null) {
            eventChannel.trySend(EditorEvent.Close)
            return
        }
        viewModelScope.launch {
            when (val result = ideas.delete(id)) {
                is DataResult.Success -> eventChannel.send(EditorEvent.Close)
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    private fun load(id: String) {
        setStatus(EditorStatus.Loading)
        viewModelScope.launch {
            val idea = try {
                ideas.getById(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(EditorStatus.LoadFailed)
                return@launch
            }
            if (idea == null) {
                setStatus(EditorStatus.NotFound)
                return@launch
            }
            draft.load(
                title = idea.title,
                content = idea.content,
                tags = idea.tags.map { it.name },
                extra = idea.status.storageKey,
            )
            setStatus(EditorStatus.Ready)
        }
    }

    private fun setStatus(status: EditorStatus) = _uiState.update { it.copy(status = status) }

    companion object {
        private const val KEY_SAVED_ID = "saved_idea_id"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                IdeaEditorViewModel(createSavedStateHandle(), container.ideaRepository, container.tagRepository)
            }
        }
    }
}
