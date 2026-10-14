package com.aidriy.app.ui.notes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.snapshotFlow
import com.aidriy.app.R
import com.aidriy.app.data.files.AttachmentStore
import com.aidriy.app.data.reminders.NoteReminders
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.TagRepository
import com.aidriy.app.ui.common.DraftSnapshot
import com.aidriy.app.ui.common.EditorDraft
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import com.aidriy.app.ui.navigation.PrefillStore
import com.aidriy.app.ui.navigation.Routes
import com.aidriy.app.ui.notes.templates.NoteTemplates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class NoteEditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    /** null until the note has been saved for the first time. */
    val noteId: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val reminderAt: Long? = null,
    val isSaving: Boolean = false,
    /** Set after a successful automatic save, shown as a quiet status line. */
    val autoSavedAt: Long? = null,
    val error: UiText? = null,
    val tagError: UiText? = null,
)

/**
 * Note editor. The text lives in [draft] (Compose state mirrored to SavedStateHandle), the
 * database stays the authoritative copy, and "saved" is only reported after the repository
 * confirms the write. With autosave on (default), changes are written 1.5 s after typing stops
 * and when leaving the editor.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NoteEditorViewModel(
    private val handle: SavedStateHandle,
    private val notes: NoteRepository,
    tags: TagRepository,
    private val attachments: AttachmentStore? = null,
    private val reminders: NoteReminders? = null,
    settings: Flow<AppSettings> = flowOf(AppSettings(autosave = false)),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val draft = EditorDraft(handle)

    private var noteId: String? = handle.get<String>(KEY_SAVED_ID) ?: handle.get<String>(Routes.ARG_NOTE_ID)
    private val saveMutex = Mutex()

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            noteId = noteId,
            isFavorite = handle.get<Boolean>(KEY_FAVORITE) ?: false,
            isArchived = handle.get<Boolean>(KEY_ARCHIVED) ?: false,
            reminderAt = handle.get<Long>(KEY_REMINDER),
        ),
    )
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> = tags.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autosave: StateFlow<Boolean> = settings.map { it.autosave }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val attachmentList: StateFlow<List<StoredFile>> = _uiState
        .map { it.noteId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null || attachments == null) flowOf(emptyList()) else attachments.observeAttachments(id) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = noteId
        when {
            id == null -> {
                if (!draft.isInitialized) prefillNewNote()
                setStatus(EditorStatus.Ready)
            }
            !InputRules.isValidId(id) -> setStatus(EditorStatus.NotFound)
            // First open, or restored after process death without the full text.
            !draft.isInitialized || draft.contentWasDropped -> load(id)
            // Restored after process death: keep the user's unsaved text.
            else -> setStatus(EditorStatus.Ready)
        }
        // Autosave: 1.5 s after the last edit, while enabled.
        viewModelScope.launch {
            snapshotFlow { draft.snapshot() }
                .drop(1)
                .debounce(AUTOSAVE_DELAY_MS)
                .collect { if (autosave.value && draft.hasUnsavedChanges && canSave()) saveInternal(auto = true) }
        }
    }

    private fun prefillNewNote() {
        val prefill = handle.get<String>(Routes.ARG_PREFILL)?.let(PrefillStore::take)
        val template = handle.get<String>(Routes.ARG_TEMPLATE)?.let { NoteTemplates.byId(it) }
        when {
            prefill != null -> {
                draft.startNew()
                draft.updateTitle(prefill.title)
                draft.updateContent(prefill.content)
            }
            template != null -> {
                draft.startNew()
                draft.updateTitle(template.title)
                draft.updateContent(template.render(today()))
            }
            else -> draft.startNew()
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

    /** Explicit save (check button). */
    fun save(closeAfter: Boolean = false) {
        if (!canSave()) return
        if (!draft.hasUnsavedChanges && noteId != null) {
            if (closeAfter) eventChannel.trySend(EditorEvent.Close)
            return
        }
        viewModelScope.launch {
            val ok = saveInternal(auto = false)
            if (ok) {
                eventChannel.send(if (closeAfter) EditorEvent.Close else EditorEvent.Message(UiText(R.string.saved)))
            }
        }
    }

    /**
     * Back pressed with autosave on: save valid changes, discard an untouched empty new note,
     * and keep the editor open (with the error shown) if saving fails.
     */
    fun closeWithAutosave() {
        viewModelScope.launch {
            val snapshot = draft.snapshot()
            val isEmptyNew = noteId == null && snapshot.title.isBlank() && snapshot.content.isBlank()
            if (!draft.hasUnsavedChanges || isEmptyNew || !canSave()) {
                eventChannel.send(EditorEvent.Close)
                return@launch
            }
            if (saveInternal(auto = false)) eventChannel.send(EditorEvent.Close)
        }
    }

    private fun canSave(): Boolean = _uiState.value.status == EditorStatus.Ready

    /** Writes the current draft. Returns true on success; on failure the text stays and an error shows. */
    private suspend fun saveInternal(auto: Boolean): Boolean = saveMutex.withLock {
        val snapshot: DraftSnapshot = draft.snapshot()
        if (auto && snapshot.title.isBlank() && snapshot.content.isBlank()) return@withLock false
        _uiState.update { it.copy(isSaving = true, error = null) }
        val input = NoteDraft(title = snapshot.title, content = snapshot.content, tagNames = snapshot.tags)
        val id = noteId
        val result = if (id == null) notes.create(input, isFavorite = _uiState.value.isFavorite) else notes.update(id, input)
        when (result) {
            is DataResult.Success -> {
                rememberSavedId(result.value.id)
                draft.markSaved(snapshot)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        noteId = result.value.id,
                        autoSavedAt = if (auto) System.currentTimeMillis() else it.autoSavedAt,
                    )
                }
                true
            }
            is DataResult.Failure -> {
                // Automatic saves stay quiet about validation (e.g. still typing); real failures show.
                _uiState.update { it.copy(isSaving = false, error = if (auto && result.error is DataError.EmptyNote) null else result.error.toUiText()) }
                false
            }
        }
    }

    /** Ensures the note exists in the database (needed before attaching files or reminders). */
    private suspend fun ensureSaved(): String? {
        noteId?.let { if (!draft.hasUnsavedChanges) return it }
        if (!saveInternal(auto = false)) return null
        return noteId
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
        viewModelScope.launch {
            val id = ensureSaved() ?: return@launch
            when (val result = notes.setArchived(id, archived)) {
                is DataResult.Success -> {
                    handle[KEY_ARCHIVED] = archived
                    _uiState.update { it.copy(isArchived = archived) }
                    eventChannel.send(
                        EditorEvent.Message(UiText(if (archived) R.string.note_archived_msg else R.string.note_restored_msg)),
                    )
                }
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Sets (or clears with null) a reminder. The note is saved first if needed. */
    fun setReminder(atMillis: Long?) {
        viewModelScope.launch {
            if (atMillis != null && atMillis <= System.currentTimeMillis()) {
                eventChannel.send(EditorEvent.Message(UiText(R.string.reminder_past)))
                return@launch
            }
            val id = ensureSaved() ?: return@launch
            when (val result = notes.setReminder(id, atMillis)) {
                is DataResult.Success -> {
                    if (atMillis == null) {
                        reminders?.cancelNote(id)
                    } else {
                        reminders?.scheduleNote(id, draft.title.ifBlank { draft.content.take(60) }, atMillis)
                    }
                    handle[KEY_REMINDER] = atMillis
                    _uiState.update { it.copy(reminderAt = atMillis) }
                    eventChannel.send(
                        EditorEvent.Message(UiText(if (atMillis == null) R.string.reminder_removed else R.string.reminder_set)),
                    )
                }
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    /** Copies a picked file into Berkas and links it to this note. */
    fun attach(uri: Uri) {
        val store = attachments ?: return
        viewModelScope.launch {
            val id = ensureSaved()
            if (id == null) {
                eventChannel.send(EditorEvent.Message(UiText(R.string.attach_needs_content)))
                return@launch
            }
            when (val result = store.import(uri, null, id)) {
                is DataResult.Success -> eventChannel.send(EditorEvent.Message(UiText(R.string.attach_done)))
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
                is DataResult.Success -> {
                    reminders?.cancelNote(id)
                    draft.markSaved()
                    eventChannel.send(EditorEvent.Close)
                }
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
            handle[KEY_REMINDER] = note.reminderAt
            _uiState.update {
                it.copy(
                    status = EditorStatus.Ready,
                    isFavorite = note.isFavorite,
                    isArchived = note.isArchived,
                    reminderAt = note.reminderAt,
                )
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
        private const val KEY_REMINDER = "reminder_at"
        private const val AUTOSAVE_DELAY_MS = 1_500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                NoteEditorViewModel(
                    handle = createSavedStateHandle(),
                    notes = container.noteRepository,
                    tags = container.tagRepository,
                    attachments = container.fileRepository,
                    reminders = container.reminderScheduler,
                    settings = container.settingsRepository.settings,
                )
            }
        }
    }
}
