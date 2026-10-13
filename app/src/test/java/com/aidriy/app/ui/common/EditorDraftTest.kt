package com.aidriy.app.ui.common

import androidx.lifecycle.SavedStateHandle
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.TagProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorDraftTest {

    @Test
    fun new_editor_has_no_unsaved_changes() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew() }
        assertFalse(draft.hasUnsavedChanges)
    }

    @Test
    fun typing_marks_changes_and_reverting_clears_them() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew() }
        draft.updateTitle("Halo")
        assertTrue(draft.hasUnsavedChanges)
        draft.updateTitle("")
        assertFalse(draft.hasUnsavedChanges)
    }

    @Test
    fun loaded_values_are_the_saved_baseline() {
        val draft = EditorDraft(SavedStateHandle())
        draft.load(title = "Judul", content = "Isi", tags = listOf("kerja"))
        assertFalse(draft.hasUnsavedChanges)
        draft.updateContent("Isi baru")
        assertTrue(draft.hasUnsavedChanges)
    }

    @Test
    fun typing_during_a_save_stays_unsaved() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew() }
        draft.updateContent("versi 1")
        val saving = draft.snapshot()
        draft.updateContent("versi 1 + ketikan baru") // typed while the save was in flight
        draft.markSaved(saving)
        assertTrue(draft.hasUnsavedChanges)
    }

    @Test
    fun text_survives_recreation_from_saved_state() {
        val handle = SavedStateHandle()
        EditorDraft(handle).apply {
            load(title = "A", content = "B", tags = emptyList())
            updateTitle("A diedit")
        }

        val restored = EditorDraft(handle)

        assertTrue(restored.isInitialized)
        assertEquals("A diedit", restored.title)
        assertEquals("B", restored.content)
        assertTrue(restored.hasUnsavedChanges)
        assertFalse(restored.contentWasDropped)
    }

    @Test
    fun very_long_content_is_not_mirrored_and_is_flagged() {
        val handle = SavedStateHandle()
        EditorDraft(handle).apply {
            startNew()
            updateContent("x".repeat(EditorDraft.MAX_SAVED_CONTENT + 1))
        }
        val restored = EditorDraft(handle)
        assertTrue(restored.contentWasDropped)
        assertEquals("", restored.content)
    }

    @Test
    fun tags_are_cleaned_and_deduplicated() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew() }
        assertEquals(DataResult.Success(Unit), draft.addTag(" #Kerja "))
        assertEquals(DataResult.Success(Unit), draft.addTag("kerja"))
        assertEquals(listOf("Kerja"), draft.tags)
        draft.removeTag("Kerja")
        assertEquals(emptyList<String>(), draft.tags)
    }

    @Test
    fun invalid_or_excess_tags_are_rejected() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew() }
        assertEquals(DataResult.Failure(DataError.InvalidTag(TagProblem.EMPTY)), draft.addTag("  "))
        repeat(TagNames.MAX_PER_RECORD) { draft.addTag("t$it") }
        assertEquals(DataResult.Failure(DataError.InvalidTag(TagProblem.TOO_MANY)), draft.addTag("satu lagi"))
    }

    @Test
    fun extra_field_is_part_of_change_detection() {
        val draft = EditorDraft(SavedStateHandle()).apply { startNew(extra = "INBOX") }
        assertFalse(draft.hasUnsavedChanges)
        draft.updateExtra("COMPLETED")
        assertTrue(draft.hasUnsavedChanges)
    }
}
