package com.aidriy.app.data

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.NoteDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SearchAndTransactionTest : DatabaseTestBase() {

    @Test
    fun global_search_covers_notes_ideas_journal_and_archived_notes() = runTest {
        val archived = data.noteRepository.create(NoteDraft("Arsip kopi", "")).let {
            (it as com.aidriy.app.domain.DataResult.Success).value
        }
        data.noteRepository.setArchived(archived.id, true)
        data.noteRepository.create(NoteDraft("Kopi pagi", ""))
        data.ideaRepository.create(IdeaDraft("Kedai kopi", ""))
        data.journalRepository.save(LocalDate.of(2026, 10, 14), JournalDraft("", "minum kopi"), null)
        data.noteRepository.create(NoteDraft("Teh", ""))

        val results = data.searchRepository.search("KOPI").first()

        assertEquals(2, results.notes.size)
        assertTrue(results.notes.any { it.isArchived })
        assertEquals(1, results.ideas.size)
        assertEquals(1, results.journalEntries.size)
    }

    @Test
    fun blank_global_search_returns_nothing() = runTest {
        data.noteRepository.create(NoteDraft("Apa saja", ""))
        assertTrue(data.searchRepository.search("   ").first().isEmpty)
    }

    @Test
    fun failed_transaction_rolls_back_every_write() = runTest {
        val note = NoteEntity("rollback-1", "t", "c", 0, 0, isFavorite = false, isArchived = false)
        try {
            db.withTransaction {
                db.noteDao().insert(note)
                throw IllegalStateException("simulated failure after the first write")
            }
            @Suppress("UNREACHABLE_CODE")
            fail("transaction should have thrown")
        } catch (expected: IllegalStateException) {
            // ok
        }
        assertNull(db.noteDao().getById("rollback-1"))
    }
}
