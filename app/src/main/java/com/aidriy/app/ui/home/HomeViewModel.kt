package com.aidriy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.core.time.Greeting
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Immutable UI state for Beranda.
 * Phase 2 extends this with recent notes, ideas, and journal entries from Room.
 */
data class HomeUiState(
    val greeting: Greeting,
    val todayLabel: String,
)

/**
 * ViewModel convention used across AIDRIY:
 * - Dependencies arrive through the constructor (testable without Android).
 * - State is exposed as a single read-only [StateFlow] of an immutable data class.
 * - Composables only render state and forward events; logic stays here.
 */
class HomeViewModel(
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Called when the screen resumes so the greeting and date stay current. */
    fun refresh() {
        _uiState.value = buildState()
    }

    private fun buildState(): HomeUiState {
        val now = timeProvider.now()
        return HomeUiState(
            greeting = Greeting.forTime(now.toLocalTime()),
            todayLabel = IndonesianDateFormatter.formatLong(now.toLocalDate()),
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().timeProvider) }
        }
    }
}
