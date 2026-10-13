package com.aidriy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.model.RecentItems
import com.aidriy.app.domain.repository.RecentItemsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Header state for Beranda. */
data class HomeUiState(
    val greeting: Greeting,
    val todayLabel: String,
    val today: LocalDate,
)

/** Recent records from Room. Never sample data. */
sealed interface HomeContentState {
    data object Loading : HomeContentState
    data class Loaded(val items: RecentItems) : HomeContentState
    data object Error : HomeContentState
}

/**
 * ViewModel convention used across AIDRIY:
 * - Dependencies arrive through the constructor (testable without Android).
 * - State is exposed as read-only [StateFlow]s of immutable data.
 * - Composables only render state and forward events; logic stays here.
 */
class HomeViewModel(
    private val timeProvider: TimeProvider,
    recentItems: RecentItemsSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val content: StateFlow<HomeContentState> = recentItems.observe()
        .map<RecentItems, HomeContentState> { HomeContentState.Loaded(it) }
        .catch { emit(HomeContentState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeContentState.Loading)

    /** Called when the screen resumes so the greeting and date stay current. */
    fun refresh() {
        _uiState.value = buildState()
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.timeProvider, container.recentItemsSource)
            }
        }
    }
}
