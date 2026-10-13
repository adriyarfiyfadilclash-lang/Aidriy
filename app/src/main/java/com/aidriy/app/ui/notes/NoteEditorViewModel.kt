package com.aidriy.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.repository.NoteRepository
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

data class NoteEditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    /** null until the note has been saved for the first time. */
    val noteId: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null,
    val tagError: UiText? = null,
)

/**
 * Explicit-save editor. The text lives in [draft] (Compose state mirrored to SavedStateHandle),
 * the database stays the authoritative copy, and "saved" is only reported after the
 * repository confirms the write.
 */
class NoteEditorViewModel(
    private val handle: SavedStateHandle,
    private val notes: NoteRepository,
    tags: TagRepository,
) : ViewModel() {

    val draft = EditorDraft(handle)

    private var noteId: String? = handle.get<String>(KEY_SAVED_ID) ?: handle.get<String>(Routes.ARG_NOTE_ID)

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            noteId = noteId,
            isFavorite = handle.get<Boolean>(KEY_FAVORITE) ?: false,
            isArchived = handle.get<Boolean>(KEY_ARCHIVED) ?: false,
        ),
    )
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> = tags.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = noteId
        when {
            id == null -> {
                draft.startNew()
                setStatus(EditorStatus.Ready)
            }
            !InputRules.isValidId(id) -> setStatus(EditorStatus.NotFound)
            // First open, or restored after process death without the full text.
            !draft.isInitialized || draft.contentWasDropped -> load(id)
            // Restored after process death: keep the user's unsaved text.
            else -> setStatus(EditorStatus.Ready)
        }
    }

    fun retryLoad() {
        noteId?.let(::load)
    }

    fun onTitleChange(value: String) = draft.updateTitle(value)

    fun onContentChange(value: String) = draft.updateContent(value)

    /** Returns true when the tag was accepted. */
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
        if (!draft.hasUnsavedChanges && noteId != null) {
            if (closeAfter) eventChannel.trySend(EditorEvent.Close)
            return
        }
        val snapshot = draft.snapshot()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val input = NoteDraft(title = snapshot.title, content = snapshot.content, tagNames = snapshot.tags)
            val id = noteId
            val result = if (id == null) notes.create(input, isFavorite = state.isFavorite) else notes.update(id, input)
            when (result) {
                is DataResult.Success -> {
                    rememberSavedId(result.value.id)
                    draft.markSaved(snapshot)
                    _uiState.update { it.copy(isSaving = false, noteId = result.value.id) }
                    eventChannel.send(
                        if (closeAfter) EditorEvent.Close else EditorEvent.Message(UiText(R.string.saved)),
                    )
                }
                // The text stays in the editor so nothing typed is lost.
                is DataResult.Failure -> _uiState.update {
                    it.copy(isSaving = false, error = result.error.toUiText())
                }
            }
        }
    }

    fun toggleFavorite() {
        val target = !_uiState.value.isFavorite
        val id = noteId
        if (id == null) {
            // Not stored yet: applied when the note is first saved.
            setFavoriteState(target)
            return
        }
        viewModelScope.launch {
            when (val result = notes.setFavorite(id, target)) {
                is DataResult.Success -> setFavoriteState(target)
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    fun setArchived(archived: Boolean) {
        val id = noteId ?: return
        viewModelScope.launch {
            when (val result = notes.setArchived(id, archived)) {
                is DataResult.Success -> {
                    handle[KEY_ARCHIVED] = archived
                    _uiState.update { it.copy(isArchived = archived) }
                    eventChannel.send(
                        EditorEvent.Message(
                            UiText(if (archived) R.string.note_archived_msg else R.string.note_restored_msg),
                        ),
                    )
                }
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Deletes the stored note. A never-saved note is simply discarded. */
    fun delete() {
        val id = noteId
        if (id == null) {
            eventChannel.trySend(EditorEvent.Close)
            return
        }
        viewModelScope.launch {
            when (val result = notes.delete(id)) {
                is DataResult.Success -> eventChannel.send(EditorEvent.Close)
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    private fun load(id: String) {
        setStatus(EditorStatus.Loading)
        viewModelScope.launch {
            val note = try {
                notes.getById(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(EditorStatus.LoadFailed)
                return@launch
            }
            if (note == null) {
                setStatus(EditorStatus.NotFound)
                return@launch
            }
            draft.load(title = note.title, content = note.content, tags = note.tags.map { it.name })
            handle[KEY_FAVORITE] = note.isFavorite
            handle[KEY_ARCHIVED] = note.isArchived
            _uiState.update {
                it.copy(status = EditorStatus.Ready, isFavorite = note.isFavorite, isArchived = note.isArchived)
            }
        }
    }

    private fun rememberSavedId(id: String) {
        noteId = id
        handle[KEY_SAVED_ID] = id
    }

    private fun setFavoriteState(value: Boolean) {
        handle[KEY_FAVORITE] = value
        _uiState.update { it.copy(isFavorite = value) }
    }

    private fun setStatus(status: EditorStatus) = _uiState.update { it.copy(status = status) }

    companion object {
        private const val KEY_SAVED_ID = "saved_note_id"
        private const val KEY_FAVORITE = "is_favorite"
        private const val KEY_ARCHIVED = "is_archived"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                NoteEditorViewModel(createSavedStateHandle(), container.noteRepository, container.tagRepository)
            }
        }
    }
}
