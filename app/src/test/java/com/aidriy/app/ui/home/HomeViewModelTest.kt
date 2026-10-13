package com.aidriy.app.ui.home

import com.aidriy.app.core.time.Greeting
import com.aidriy.app.core.time.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeViewModelTest {

    private val jakarta = ZoneId.of("Asia/Jakarta")

    /** Mutable clock so tests can move time forward. */
    private class FakeTimeProvider(var current: ZonedDateTime) : TimeProvider {
        override fun now(): ZonedDateTime = current
    }

    @Test
    fun initial_state_uses_local_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 7, 15, 0, 0, jakarta))

        val state = HomeViewModel(clock).uiState.value

        assertEquals(Greeting.PAGI, state.greeting)
        assertEquals("Rabu, 14 Oktober 2026", state.todayLabel)
    }

    @Test
    fun refresh_picks_up_new_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 23, 50, 0, 0, jakarta))
        val viewModel = HomeViewModel(clock)
        assertEquals(Greeting.MALAM, viewModel.uiState.value.greeting)

        clock.current = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, jakarta)
        viewModel.refresh()

        assertEquals(Greeting.PAGI, viewModel.uiState.value.greeting)
        assertEquals("Kamis, 15 Oktober 2026", viewModel.uiState.value.todayLabel)
    }
}
