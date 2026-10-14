package com.aidriy.app.ui.home

import com.aidriy.app.core.time.Greeting
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.testing.FakeNoteRepository
import com.aidriy.app.testing.FakeTimeProvider
import com.aidriy.app.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private fun clockAt(hour: Int, day: Int = 14) =
        FakeTimeProvider(ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, jakarta))

    @Test
    fun initial_state_uses_local_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 7, 15, 0, 0, jakarta))

        val state = HomeViewModel(clock, FakeNoteRepository()).uiState.value

        assertEquals(Greeting.PAGI, state.greeting)
        assertEquals("Rabu, 14 Oktober 2026", state.todayLabel)
        assertEquals(LocalDate.of(2026, 10, 14), state.today)
    }

    @Test
    fun refresh_picks_up_new_time_and_date() {
        val clock = FakeTimeProvider(ZonedDateTime.of(2026, 10, 14, 23, 50, 0, 0, jakarta))
        val viewModel = HomeViewModel(clock, FakeNoteRepository())
        assertEquals(Greeting.MALAM, viewModel.uiState.value.greeting)

        clock.current = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, jakarta)
        viewModel.refresh()

        assertEquals(Greeting.PAGI, viewModel.uiState.value.greeting)
        assertEquals("Kamis, 15 Oktober 2026", viewModel.uiState.value.todayLabel)
    }

    @Test
    fun content_reflects_notes_from_the_repository() = runTest {
        val repo = FakeNoteRepository()
        val viewModel = HomeViewModel(clockAt(9), repo)

        // Eager collector so the WhileSubscribed state starts immediately.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect { } }
        assertEquals(HomeContentState.Loaded(emptyList()), viewModel.content.value)

        repo.create(NoteDraft(title = "Rapat", content = ""))
        val loaded = viewModel.content.value as HomeContentState.Loaded
        assertEquals(listOf("Rapat"), loaded.notes.map { it.title })
    }

    @Test
    fun content_shows_error_when_repository_fails() = runTest {
        val failing = object : NoteRepository by FakeNoteRepository() {
            override fun observeRecent(limit: Int): Flow<List<Note>> = flow { throw IllegalStateException("disk error") }
        }
        val viewModel = HomeViewModel(clockAt(9), failing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.content.collect { } }
        assertEquals(HomeContentState.Error, viewModel.content.first { it !is HomeContentState.Loading })
    }

    @Test
    fun toggle_favorite_updates_the_note() = runTest {
        val repo = FakeNoteRepository()
        val note = (repo.create(NoteDraft(title = "Ide", content = "")) as com.aidriy.app.domain.DataResult.Success).value
        val viewModel = HomeViewModel(clockAt(9), repo)

        viewModel.toggleFavorite(note)

        assertTrue(repo.stored(note.id)!!.isFavorite)
    }

    @Test
    fun profile_follows_settings() = runTest {
        val settings = MutableStateFlow(AppSettings(profileName = "Adri"))
        val viewModel = HomeViewModel(clockAt(9), FakeNoteRepository(), settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.profile.collect { } }
        assertEquals("Adri", viewModel.profile.value.name)
    }
}
