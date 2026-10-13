package com.aidriy.app.ui.home

import com.aidriy.app.core.time.Greeting
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.RecentItems
import com.aidriy.app.domain.repository.RecentItemsSource
import com.aidriy.app.testing.FakeTimeProvider
import com.aidriy.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jakarta = ZoneId.of("Asia/Jakarta")
    private val noRecent = RecentItemsSource { flowOf(RecentItems.EMPTY) }

    @Test
    fun initial_state_uses_local_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 7, 15, 0, 0, jakarta))

        val state = HomeViewModel(clock, noRecent).uiState.value

        assertEquals(Greeting.PAGI, state.greeting)
        assertEquals("Rabu, 14 Oktober 2026", state.todayLabel)
        assertEquals(LocalDate.of(2026, 10, 14), state.today)
    }

    @Test
    fun refresh_picks_up_new_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 23, 50, 0, 0, jakarta))
        val viewModel = HomeViewModel(clock, noRecent)
        assertEquals(Greeting.MALAM, viewModel.uiState.value.greeting)

        clock.current = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, jakarta)
        viewModel.refresh()

        assertEquals(Greeting.PAGI, viewModel.uiState.value.greeting)
        assertEquals("Kamis, 15 Oktober 2026", viewModel.uiState.value.todayLabel)
    }

    @Test
    fun content_reflects_records_from_the_source() = runTest {
        val note = Note(id = "n1", title = "Rapat", content = "", createdAt = 1, updatedAt = 1)
        val source = MutableStateFlow(RecentItems.EMPTY)
        val viewModel = HomeViewModel(
            FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 9, 0, 0, 0, jakarta)),
        ) { source }

        // Eager collector so the WhileSubscribed state starts immediately.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect { } }
        assertEquals(HomeContentState.Loaded(RecentItems.EMPTY), viewModel.content.value)

        source.value = RecentItems(notes = listOf(note), ideas = emptyList(), journalEntries = emptyList())
        assertEquals(listOf(note), (viewModel.content.value as HomeContentState.Loaded).items.notes)
    }

    @Test
    fun content_shows_error_when_source_fails() = runTest {
        val failing = RecentItemsSource { flow { throw IllegalStateException("disk error") } }
        val viewModel = HomeViewModel(
            FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 9, 0, 0, 0, jakarta)),
            failing,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect { } }
        assertEquals(HomeContentState.Error, viewModel.content.first { it !is HomeContentState.Loading })
    }
}
