package com.aidriy.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.aidriy.app.R
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.testing.FakeNoteRepository
import com.aidriy.app.testing.FakeTagRepository
import com.aidriy.app.testing.MainDispatcherRule
import com.aidriy.app.ui.common.EditorEvent
import com.aidriy.app.ui.common.EditorStatus
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.navigation.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val notes = FakeNoteRepository()

    private fun newEditor(handle: SavedStateHandle = SavedStateHandle()) =
        NoteEditorViewModel(handle, notes, FakeTagRepository())

    @Test
    fun creating_a_note_reports_saved_only_after_the_repository_succeeds() = runTest {
        val vm = newEditor()
        vm.onTitleChange("Belanja")
        vm.onContentChange("Telur, roti")

        vm.save()

        assertEquals(EditorEvent.Message(UiText(R.string.saved)), vm.events.first())
        val id = vm.uiState.value.noteId
        assertNotNull(id)
        assertEquals("Belanja", notes.stored(id!!)?.title)
        assertFalse(vm.draft.hasUnsavedChanges)
    }

    @Test
    fun failed_save_keeps_the_text_and_shows_an_error() = runTest {
        notes.failWritesWith = DataError.Storage(RuntimeException("disk full"))
        val vm = newEditor()
        vm.onContentChange("Jangan sampai hilang")

        vm.save()

        assertEquals(UiText(R.string.error_storage), vm.uiState.value.error)
        assertEquals("Jangan sampai hilang", vm.draft.content)
        assertTrue(vm.draft.hasUnsavedChanges)
        assertNull(vm.uiState.value.noteId)
        assertEquals(0, notes.writeCount)
    }

    @Test
    fun empty_note_is_rejected_with_validation_message() = runTest {
        val vm = newEditor()
        vm.save()
        assertEquals(UiText(R.string.error_empty_note), vm.uiState.value.error)
        assertEquals(0, notes.writeCount)
    }

    @Test
    fun editing_an_existing_note_updates_it_instead_of_creating_a_copy() = runTest {
        val created = (notes.create(NoteDraft("Lama", "isi")) as DataResult.Success).value
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to created.id)))
        assertEquals(EditorStatus.Ready, vm.uiState.value.status)
        assertEquals("Lama", vm.draft.title)

        vm.onTitleChange("Baru")
        vm.save()

        assertEquals("Baru", notes.stored(created.id)?.title)
        assertEquals(2, notes.writeCount) // one create in setup + one update
    }

    @Test
    fun unknown_note_id_shows_not_found_and_blocks_saving() = runTest {
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to "missing")))
        assertEquals(EditorStatus.NotFound, vm.uiState.value.status)
        vm.onContentChange("teks")
        vm.save()
        assertEquals(0, notes.writeCount)
    }

    @Test
    fun favorite_on_a_new_note_is_applied_when_first_saved() = runTest {
        val vm = newEditor()
        vm.toggleFavorite()
        vm.onTitleChange("Penting")
        vm.save()
        assertTrue(notes.stored(vm.uiState.value.noteId!!)!!.isFavorite)
    }

    @Test
    fun deleting_closes_the_editor_after_success() = runTest {
        val created = (notes.create(NoteDraft("Hapus aku", "")) as DataResult.Success).value
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to created.id)))
        vm.delete()
        assertEquals(EditorEvent.Close, vm.events.first())
        assertNull(notes.stored(created.id))
    }
}
