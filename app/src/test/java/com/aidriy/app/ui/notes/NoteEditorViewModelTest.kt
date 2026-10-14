package com.aidriy.app.ui.notes

import androidx.lifecycle.SavedStateHandle
import com.aidriy.app.R
import com.aidriy.app.data.ai.FormatOutcome
import com.aidriy.app.domain.markdown.FormatGuard
import com.aidriy.app.domain.markdown.FormatOptions
import com.aidriy.app.data.reminders.NoteReminders
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

    private class RecordingReminders : NoteReminders {
        val scheduled = mutableMapOf<String, Long>()
        override fun scheduleNote(noteId: String, title: String, atMillis: Long) {
            scheduled[noteId] = atMillis
        }
        override fun cancelNote(noteId: String) {
            scheduled.remove(noteId)
        }
    }

    @Test
    fun reminder_saves_the_note_and_schedules_it() = runTest {
        val reminders = RecordingReminders()
        val vm = NoteEditorViewModel(SavedStateHandle(), notes, FakeTagRepository(), reminders = reminders)
        vm.onTitleChange("Bayar listrik")
        val at = System.currentTimeMillis() + 3_600_000

        vm.setReminder(at)

        assertEquals(EditorEvent.Message(UiText(R.string.reminder_set)), vm.events.first())
        val id = vm.uiState.value.noteId!!
        assertEquals(at, notes.stored(id)?.reminderAt)
        assertEquals(at, reminders.scheduled[id])

        vm.setReminder(null)
        assertNull(notes.stored(id)?.reminderAt)
        assertTrue(reminders.scheduled.isEmpty())
    }

    @Test
    fun reminder_in_the_past_is_rejected() = runTest {
        val reminders = RecordingReminders()
        val vm = NoteEditorViewModel(SavedStateHandle(), notes, FakeTagRepository(), reminders = reminders)
        vm.onTitleChange("Lewat")

        vm.setReminder(System.currentTimeMillis() - 60_000)

        assertEquals(EditorEvent.Message(UiText(R.string.reminder_past)), vm.events.first())
        assertTrue(reminders.scheduled.isEmpty())
    }

    // ------------------------------------------------------------------ reader, undo, AI format

    private fun existing(content: String): String =
        runBlockingCreate(NoteDraft("Materi", content))

    private fun runBlockingCreate(draft: NoteDraft): String =
        kotlinx.coroutines.runBlocking { (notes.create(draft) as DataResult.Success).value.id }

    @Test
    fun existing_note_opens_in_reader_and_new_note_in_editor() = runTest {
        val id = existing("isi")
        assertEquals(NoteMode.READ, newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to id))).uiState.value.mode)
        assertEquals(NoteMode.EDIT, newEditor().uiState.value.mode)
    }

    @Test
    fun finishing_an_edit_saves_and_returns_to_the_reader() = runTest {
        val id = existing("isi")
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to id)))
        vm.startEditing()
        assertEquals(NoteMode.EDIT, vm.uiState.value.mode)
        vm.onContentChange("isi baru")
        vm.finishEditing()
        assertEquals(NoteMode.READ, vm.uiState.value.mode)
        assertEquals("isi baru", notes.stored(id)?.content)
    }

    @Test
    fun ticking_a_task_in_the_reader_is_stored() = runTest {
        val id = existing("- [ ] beli kopi\n- [ ] kirim laporan")
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to id)))
        vm.toggleTask(1)
        assertEquals("- [ ] beli kopi\n- [x] kirim laporan", notes.stored(id)?.content)
    }

    @Test
    fun undo_and_redo_restore_text() = runTest {
        val vm = newEditor()
        vm.onContentChange("a")
        vm.onContentChange("ab")
        assertTrue(vm.canUndo)
        vm.undo()
        assertEquals("", vm.draft.content)
        assertTrue(vm.canRedo)
        vm.redo()
        assertEquals("ab", vm.draft.content)
    }

    @Test
    fun moving_to_a_folder_is_stored() = runTest {
        val id = existing("isi")
        val vm = newEditor(SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to id)))
        vm.setFolder("folder-1")
        assertEquals("folder-1", notes.stored(id)?.folderId)
        assertEquals(EditorEvent.Message(UiText(R.string.note_moved)), vm.events.first())
    }

    private fun formattingEditor(id: String, answer: (String) -> String) = NoteEditorViewModel(
        handle = SavedStateHandle(mapOf(Routes.ARG_NOTE_ID to id)),
        notes = notes,
        tags = FakeTagRepository(),
        formatter = { _, markdown, _ ->
            val protected = FormatGuard.protect(markdown)
            FormatOutcome.Checked(FormatGuard.check(markdown, protected, answer(protected.text)))
        },
    )

    @Test
    fun ai_format_shows_a_preview_and_applies_only_after_confirming() = runTest {
        val id = existing("1. pengertian\nisi catatan")
        val vm = formattingEditor(id) { "## 1. Pengertian\n\nIsi catatan." }

        vm.requestFormat(FormatOptions())
        val preview = vm.format.value as FormatState.Preview
        assertNull(preview.changedWords)
        assertEquals("1. pengertian\nisi catatan", notes.stored(id)?.content) // nothing applied yet

        vm.applyFormat()
        assertEquals("## 1. Pengertian\n\nIsi catatan.", notes.stored(id)?.content)
        assertEquals(FormatState.Idle, vm.format.value)

        vm.undo()
        assertEquals("1. pengertian\nisi catatan", vm.draft.content)
    }

    @Test
    fun ai_format_that_changes_words_is_flagged() = runTest {
        val id = existing("isi catatan")
        val vm = formattingEditor(id) { "Isi catatan yang lebih baik." }
        vm.requestFormat(FormatOptions())
        val preview = vm.format.value as FormatState.Preview
        assertEquals(listOf("yang", "lebih", "baik"), preview.changedWords?.added)
    }

    @Test
    fun ai_format_that_drops_an_image_is_refused() = runTest {
        val id = existing("teks\n![Gambar 1](berkas://img)")
        val vm = formattingEditor(id) { "Teks" }
        vm.requestFormat(FormatOptions())
        assertEquals(FormatState.Failed(UiText(R.string.format_lost_blocks)), vm.format.value)
        assertEquals("teks\n![Gambar 1](berkas://img)", notes.stored(id)?.content)
    }
}
