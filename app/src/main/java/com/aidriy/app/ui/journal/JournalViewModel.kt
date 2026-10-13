package com.aidriy.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.di.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Phase 2 adds entry history, the selected date, and today's entry (if any). */
data class JournalUiState(
    val today: LocalDate,
    val todayLabel: String,
)

class JournalViewModel(
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildState())
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    /** Keeps "today" correct if the app stays open across midnight. */
    fun refresh() {
        val next = buildState()
        if (next != _uiState.value) _uiState.value = next
    }

    private fun buildState(): JournalUiState {
        val today = timeProvider.now().toLocalDate()
        return JournalUiState(
            today = today,
            todayLabel = IndonesianDateFormatter.formatLong(today),
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { JournalViewModel(appContainer().timeProvider) }
        }
    }
}
