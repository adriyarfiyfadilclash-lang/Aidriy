package com.aidriy.app.ui.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.today
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.repository.JournalRepository
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
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class JournalEditorUiState(
    val status: EditorStatus = EditorStatus.Loading,
    val dateLabel: String = "",
    /** null when no entry exists yet for this date. */
    val entryId: String? = null,
    val isSaving: Boolean = false,
    /** Blocking problem with the date itself (invalid or in the future). */
    val dateProblem: UiText? = null,
    val error: UiText? = null,
    /** Non-error information, e.g. that an existing entry was found on save. */
    val notice: UiText? = null,
    val tagError: UiText? = null,
)

/**
 * Editor for the single journal entry of one local calendar date.
 * Opening a date that already has an entry loads it, so duplicates are never created from here.
 */
class JournalEditorViewModel(
    private val handle: SavedStateHandle,
    private val journal: JournalRepository,
    tags: TagRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    val draft = EditorDraft(handle)

    private val date: LocalDate? = handle.get<String>(Routes.ARG_DATE)?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private var entryId: String? = handle.get<String>(KEY_ENTRY_ID)

    private val _uiState = MutableStateFlow(
        JournalEditorUiState(
            dateLabel = date?.let(IndonesianDateFormatter::formatLong).orEmpty(),
            entryId = entryId,
        ),
    )
    val uiState: StateFlow<JournalEditorUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = eventChannel.receiveAsFlow()

    val allTags: StateFlow<List<Tag>> = tags.observeAll()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        when {
            date == null -> _uiState.update {
                it.copy(status = EditorStatus.NotFound, dateProblem = UiText(R.string.journal_invalid_date))
            }
            date.isAfter(timeProvider.today()) -> _uiState.update {
                it.copy(status = EditorStatus.NotFound, dateProblem = DataError.FutureJournalDate.toUiText())
            }
            !draft.isInitialized || draft.contentWasDropped -> load(date)
            else -> setStatus(EditorStatus.Ready)
        }
    }

    fun retryLoad() {
        date?.let(::load)
    }

    fun onTitleChange(value: String) = draft.updateTitle(value)
    fun onContentChange(value: String) = draft.updateContent(value)

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
        val day = date ?: return
        val state = _uiState.value
        if (state.isSaving || state.status != EditorStatus.Ready) return
        if (!draft.hasUnsavedChanges && entryId != null) {
            if (closeAfter) eventChannel.trySend(EditorEvent.Close)
            return
        }
        val snapshot = draft.snapshot()
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, notice = null) }
            val input = JournalDraft(title = snapshot.title, content = snapshot.content, tagNames = snapshot.tags)
            when (val result = journal.save(day, input, expectedId = entryId)) {
                is DataResult.Success -> {
                    setEntryId(result.value.id)
                    draft.markSaved(snapshot)
                    _uiState.update { it.copy(isSaving = false) }
                    eventChannel.send(
                        if (closeAfter) EditorEvent.Close else EditorEvent.Message(UiText(R.string.saved)),
                    )
                }
                is DataResult.Failure -> handleSaveFailure(result.error)
            }
        }
    }

    fun delete() {
        val id = entryId
        if (id == null) {
            eventChannel.trySend(EditorEvent.Close)
            return
        }
        viewModelScope.launch {
            when (val result = journal.delete(id)) {
                is DataResult.Success -> eventChannel.send(EditorEvent.Close)
                is DataResult.Failure -> eventChannel.send(EditorEvent.Message(result.error.toUiText()))
            }
        }
    }

    private fun handleSaveFailure(error: DataError) {
        when (error) {
            // Someone else created this date's entry: point the editor at it, keep the user's text,
            // and explain that saving again will replace that entry's content.
            is DataError.DuplicateJournalDate -> {
                setEntryId(error.existingId)
                _uiState.update { it.copy(isSaving = false, notice = UiText(R.string.journal_duplicate_loaded)) }
            }
            // The entry was deleted meanwhile; the next save creates a new one.
            DataError.NotFound -> {
                setEntryId(null)
                _uiState.update { it.copy(isSaving = false, notice = UiText(R.string.journal_deleted_elsewhere)) }
            }
            else -> _uiState.update { it.copy(isSaving = false, error = error.toUiText()) }
        }
    }

    private fun load(day: LocalDate) {
        setStatus(EditorStatus.Loading)
        viewModelScope.launch {
            val entry = try {
                journal.getByDate(day)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(EditorStatus.LoadFailed)
                return@launch
            }
            if (entry != null) {
                draft.load(title = entry.title, content = entry.content, tags = entry.tags.map { it.name })
                setEntryId(entry.id)
            } else {
                draft.startNew()
                setEntryId(null)
            }
            setStatus(EditorStatus.Ready)
        }
    }

    private fun setEntryId(id: String?) {
        entryId = id
        handle[KEY_ENTRY_ID] = id
        _uiState.update { it.copy(entryId = id) }
    }

    private fun setStatus(status: EditorStatus) = _uiState.update { it.copy(status = status) }

    companion object {
        private const val KEY_ENTRY_ID = "journal_entry_id"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                JournalEditorViewModel(
                    createSavedStateHandle(),
                    container.journalRepository,
                    container.tagRepository,
                    container.timeProvider,
                )
            }
        }
    }
}
