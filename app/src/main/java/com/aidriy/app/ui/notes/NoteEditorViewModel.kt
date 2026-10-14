package com.aidriy.app.ui.notes

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.ai.FormatOutcome
import com.aidriy.app.data.files.AttachmentStore
import com.aidriy.app.data.reminders.NoteReminders
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.markdown.FormatGuard
import com.aidriy.app.domain.markdown.FormatOptions
import com.aidriy.app.domain.markdown.Markdown
import com.aidriy.app.domain.model.Folder
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
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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

/** Existing notes open in READ (clean reader); new notes and the pencil button use EDIT. */
enum class NoteMode { READ, EDIT }

data class NoteEditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    /** null until the note has been saved for the first time. */
    val noteId: String? = null,
    val mode: NoteMode = NoteMode.EDIT,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val reminderAt: Long? = null,
    val folderId: String? = null,
    val updatedAt: Long? = null,
    val isSaving: Boolean = false,
    /** Set after a successful automatic save, shown as a quiet status line. */
    val autoSavedAt: Long? = null,
    val error: UiText? = null,
    val tagError: UiText? = null,
)

/** Which AI provider "Rapikan Format" will use, shown before anything is sent. */
data class FormatterInfo(val providerName: String, val configured: Boolean)

/** "Rapikan Format" with AI: options → working → preview (before/after) → confirm → applied. */
sealed interface FormatState {
    data object Idle : FormatState
    data object Working : FormatState
    data class Preview(
        val before: String,
        val after: String,
        /** Non-null when the AI changed words; applying then needs a second, explicit choice. */
        val changedWords: FormatGuard.Result.TextChanged? = null,
    ) : FormatState
    data class Failed(val message: UiText) : FormatState
}

/** Runs the AI formatting. Implemented with AiChatRepository; faked in tests. */
fun interface NoteFormatter {
    suspend fun format(title: String, markdown: String, options: FormatOptions): FormatOutcome
}

/**
 * Note reader + editor. The text lives in [draft] (Compose state mirrored to SavedStateHandle),
 * the database stays the authoritative copy, and "saved" is only reported after the repository
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
    folders: Flow<List<Folder>> = flowOf(emptyList()),
    formatterInfo: Flow<FormatterInfo?> = flowOf(null),
    private val formatter: NoteFormatter? = null,
) : ViewModel() {

    val draft = EditorDraft(handle)

    /** Opened with "Catatan Baru" (not an existing note): Back closes the editor after saving. */
    val startedNew: Boolean = handle.get<String>(Routes.ARG_NOTE_ID) == null

    private var noteId: String? = handle.get<String>(KEY_SAVED_ID) ?: handle.get<String>(Routes.ARG_NOTE_ID)
    private val saveMutex = Mutex()

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            noteId = noteId,
            mode = handle.get<String>(KEY_MODE)?.let { NoteMode.valueOf(it) }
                ?: if (noteId == null) NoteMode.EDIT else NoteMode.READ,
            isFavorite = handle.get<Boolean>(KEY_FAVORITE) ?: false,
            isArchived = handle.get<Boolean>(KEY_ARCHIVED) ?: false,
            reminderAt = handle.get<Long>(KEY_REMINDER),
            folderId = handle.get<String>(KEY_FOLDER),
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

    val berkasImages: StateFlow<List<StoredFile>> = (attachments?.observeImages() ?: flowOf(emptyList()))
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderList: StateFlow<List<Folder>> = folders
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val formatterReady: StateFlow<FormatterInfo?> = formatterInfo
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------------ undo / redo

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastHistoryPush = 0L

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    // ------------------------------------------------------------------ AI format

    private val _format = MutableStateFlow<FormatState>(FormatState.Idle)
    val format: StateFlow<FormatState> = _format.asStateFlow()
    private var formatJob: Job? = null

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

    /** Every content edit goes through here so undo/redo can follow it. */
    fun onContentChange(value: String) {
        val old = draft.content
        if (value == old) return
        val now = System.currentTimeMillis()
        // Typing within a short burst is one undo step.
        if (undoStack.isEmpty() || now - lastHistoryPush > HISTORY_GROUP_MS) {
            pushUndo(old)
            lastHistoryPush = now
        }
        redoStack.clear()
        draft.updateContent(value)
        refreshHistoryFlags()
    }

    private fun pushUndo(value: String) {
        undoStack.addLast(value)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(draft.content)
        draft.updateContent(previous)
        lastHistoryPush = 0L
        refreshHistoryFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        pushUndo(draft.content)
        draft.updateContent(next)
        lastHistoryPush = 0L
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    // ------------------------------------------------------------------ modes

    fun startEditing() = setMode(NoteMode.EDIT)

    /** Pencil → check: save valid changes, then show the reader. Stays in EDIT if saving fails. */
    fun finishEditing() {
        viewModelScope.launch {
            val snapshot = draft.snapshot()
            if (noteId == null && snapshot.title.isBlank() && snapshot.content.isBlank()) {
                eventChannel.send(EditorEvent.Close)
                return@launch
            }
            if (draft.hasUnsavedChanges || noteId == null) {
                if (!saveInternal(auto = false)) return@launch
            }
            setMode(NoteMode.READ)
        }
    }

    private fun setMode(mode: NoteMode) {
        handle[KEY_MODE] = mode.name
        _uiState.update { it.copy(mode = mode) }
    }

    /** Ticks a checklist item from the reader and stores it right away. */
    fun toggleTask(line: Int) {
        val updated = Markdown.toggleTask(draft.content, line)
        if (updated == draft.content) return
        onContentChange(updated)
        viewModelScope.launch { if (canSave()) saveInternal(auto = true) }
    }

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

    /** Explicit save (menu). */
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
                // A folder chosen before the first save is applied now.
                if (id == null) _uiState.value.folderId?.let { notes.setFolder(result.value.id, it) }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        noteId = result.value.id,
                        updatedAt = result.value.updatedAt,
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

    /** Ensures the note exists in the database (needed before reminders). */
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

    fun setFolder(folderId: String?) {
        val id = noteId
        if (id == null) {
            handle[KEY_FOLDER] = folderId
            _uiState.update { it.copy(folderId = folderId) }
            return
        }
        viewModelScope.launch {
            when (val result = notes.setFolder(id, folderId)) {
                is DataResult.Success -> {
                    handle[KEY_FOLDER] = folderId
                    _uiState.update { it.copy(folderId = folderId) }
                    eventChannel.send(EditorEvent.Message(UiText(R.string.note_moved)))
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

    // ------------------------------------------------------------------ images and files

    /** Copies a picked image or file into Berkas, linked to this note when it already exists. */
    suspend fun importToBerkas(uri: Uri): StoredFile? {
        val store = attachments ?: return null
        return when (val result = store.import(uri, null, noteId)) {
            is DataResult.Success -> result.value
            is DataResult.Failure -> {
                eventChannel.send(EditorEvent.Message(result.error.toUiText()))
                null
            }
        }
    }

    /** "Scan Dokumen": stores the photo and reads its text on the device. */
    suspend fun scanToBerkas(uri: Uri): Pair<StoredFile, String>? {
        val store = attachments ?: return null
        val file = importToBerkas(uri) ?: return null
        return when (val text = store.runOcr(file.id)) {
            is DataResult.Success -> file to text.value
            is DataResult.Failure -> {
                eventChannel.send(EditorEvent.Message(text.error.toUiText()))
                file to ""
            }
        }
    }

    suspend fun lookupFile(id: String): StoredFile? = attachments?.getFile(id)

    fun message(text: UiText) {
        eventChannel.trySend(EditorEvent.Message(text))
    }

    // ------------------------------------------------------------------ AI format

    fun requestFormat(options: FormatOptions) {
        val run = formatter ?: return
        val before = draft.content
        if (before.isBlank()) {
            _format.value = FormatState.Failed(UiText(R.string.format_empty))
            return
        }
        formatJob?.cancel()
        _format.value = FormatState.Working
        formatJob = viewModelScope.launch {
            _format.value = when (val outcome = run.format(draft.title, before, options)) {
                is FormatOutcome.Failed -> FormatState.Failed(outcome.error.toUiText())
                is FormatOutcome.TooLong -> FormatState.Failed(UiText(R.string.format_too_long, listOf(outcome.maxChars)))
                is FormatOutcome.Checked -> when (val r = outcome.result) {
                    is FormatGuard.Result.Ok -> if (r.text == before) {
                        FormatState.Failed(UiText(R.string.format_no_change))
                    } else {
                        FormatState.Preview(before, r.text)
                    }
                    is FormatGuard.Result.TextChanged -> FormatState.Preview(before, r.restored, changedWords = r)
                    is FormatGuard.Result.LostBlocks -> FormatState.Failed(UiText(R.string.format_lost_blocks))
                }
            }
        }
    }

    fun cancelFormat() {
        formatJob?.cancel()
        _format.value = FormatState.Idle
    }

    /** Applies the previewed format after the user confirmed. Undo restores the previous text. */
    fun applyFormat() {
        val preview = _format.value as? FormatState.Preview ?: return
        _format.value = FormatState.Idle
        if (draft.content != preview.before) {
            // The note changed while the preview was open: never overwrite newer text.
            eventChannel.trySend(EditorEvent.Message(UiText(R.string.format_stale)))
            return
        }
        pushUndo(draft.content)
        redoStack.clear()
        lastHistoryPush = 0L
        draft.updateContent(preview.after)
        refreshHistoryFlags()
        viewModelScope.launch {
            if (canSave()) saveInternal(auto = false)
            eventChannel.send(EditorEvent.Message(UiText(R.string.format_applied)))
        }
    }

    // ------------------------------------------------------------------ delete / load

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
            handle[KEY_FOLDER] = note.folderId
            _uiState.update {
                it.copy(
                    status = EditorStatus.Ready,
                    isFavorite = note.isFavorite,
                    isArchived = note.isArchived,
                    reminderAt = note.reminderAt,
                    folderId = note.folderId,
                    updatedAt = note.updatedAt,
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
        private const val KEY_FOLDER = "folder_id"
        private const val KEY_MODE = "mode"
        private const val AUTOSAVE_DELAY_MS = 1_500L
        private const val HISTORY_GROUP_MS = 1_000L
        private const val MAX_HISTORY = 100

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val settingsFlow = container.settingsRepository.settings
                NoteEditorViewModel(
                    handle = createSavedStateHandle(),
                    notes = container.noteRepository,
                    tags = container.tagRepository,
                    attachments = container.fileRepository,
                    reminders = container.reminderScheduler,
                    settings = settingsFlow,
                    folders = container.fileRepository.observeFolders(),
                    formatterInfo = combine(settingsFlow, container.secretStore.configured) { s, keys ->
                        FormatterInfo(s.defaultProvider.displayName, s.defaultProvider in keys)
                    },
                    formatter = { title, markdown, options ->
                        val s = container.settingsRepository.settings.first()
                        container.aiChatRepository.formatNote(
                            provider = s.defaultProvider,
                            model = s.modelFor(s.defaultProvider),
                            title = title,
                            markdown = markdown,
                            options = options,
                            otherBaseUrl = s.otherBaseUrl,
                        )
                    },
                )
            }
        }
    }
}
