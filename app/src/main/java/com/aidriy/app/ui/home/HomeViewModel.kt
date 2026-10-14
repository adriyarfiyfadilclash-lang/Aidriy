package com.aidriy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Header state for the Catatan home screen. */
data class HomeUiState(
    val greeting: Greeting,
    val todayLabel: String,
    val today: LocalDate,
)

/** Recent notes from Room. Never sample data. */
sealed interface HomeContentState {
    data object Loading : HomeContentState
    data class Loaded(val notes: List<Note>) : HomeContentState
    data object Error : HomeContentState
}

data class Profile(val name: String, val photoPath: String?)

/**
 * ViewModel convention used across AIDRIY:
 * - Dependencies arrive through the constructor (testable without Android).
 * - State is exposed as read-only [StateFlow]s of immutable data.
 * - Composables only render state and forward events; logic stays here.
 */
class HomeViewModel(
    private val timeProvider: TimeProvider,
    private val notes: NoteRepository,
    settings: Flow<AppSettings> = flowOf(AppSettings()),
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val content: StateFlow<HomeContentState> = notes.observeRecent(RECENT_LIMIT)
        .map<List<Note>, HomeContentState> { HomeContentState.Loaded(it) }
        .catch { emit(HomeContentState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeContentState.Loading)

    val profile: StateFlow<Profile> = settings
        .map { Profile(it.profileName, it.profilePhotoPath) }
        .catch { emit(Profile("", null)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile("", null))

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    /** Called when the screen resumes so the greeting and date stay current. */
    fun refresh() {
        _uiState.value = buildState()
    }

    fun toggleFavorite(note: Note) = report { notes.setFavorite(note.id, !note.isFavorite) }

    fun archive(note: Note) = report { notes.setArchived(note.id, true) }

    fun delete(note: Note) = report { notes.delete(note.id) }

    private fun report(action: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            val result = action()
            if (result is DataResult.Failure) messageChannel.send(result.error.toUiText())
        }
    }

    private fun buildState(): HomeUiState {
        val now = timeProvider.now()
        return HomeUiState(
            greeting = Greeting.forTime(now.toLocalTime()),
            todayLabel = IndonesianDateFormatter.formatLong(now.toLocalDate()),
            today = now.toLocalDate(),
        )
    }

    companion object {
        const val RECENT_LIMIT = 6

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.timeProvider, container.noteRepository, container.settingsRepository.settings)
            }
        }
    }
}
