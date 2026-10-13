package com.aidriy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InputRulesTest {

    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun note_needs_title_or_content() {
        assertEquals(DataError.EmptyNote, InputRules.validateNote("  ", "\n"))
        assertNull(InputRules.validateNote("Judul saja", ""))
        assertNull(InputRules.validateNote("", "Isi saja"))
    }

    @Test
    fun note_title_length_is_limited() {
        val tooLong = "a".repeat(InputRules.TITLE_MAX + 1)
        assertEquals(DataError.TitleTooLong(InputRules.TITLE_MAX), InputRules.validateNote(tooLong, "isi"))
        // Surrounding whitespace is trimmed before measuring.
        assertNull(InputRules.validateNote("  " + "a".repeat(InputRules.TITLE_MAX) + "  ", ""))
    }

    @Test
    fun note_content_length_is_limited() {
        val tooLong = "x".repeat(InputRules.CONTENT_MAX + 1)
        assertEquals(DataError.ContentTooLong(InputRules.CONTENT_MAX), InputRules.validateNote("t", tooLong))
    }

    @Test
    fun idea_requires_a_title() {
        assertEquals(DataError.EmptyIdeaTitle, InputRules.validateIdea(" ", "detail tanpa judul"))
        assertNull(InputRules.validateIdea("Ide", ""))
    }

    @Test
    fun journal_rejects_future_dates_but_accepts_today_and_past() {
        assertEquals(
            DataError.FutureJournalDate,
            InputRules.validateJournal(today.plusDays(1), today, "", "isi"),
        )
        assertNull(InputRules.validateJournal(today, today, "", "isi"))
        assertNull(InputRules.validateJournal(today.minusYears(3), today, "judul", ""))
    }

    @Test
    fun journal_must_not_be_empty() {
        assertEquals(DataError.EmptyJournal, InputRules.validateJournal(today, today, " ", " "))
    }

    @Test
    fun id_validation() {
        assertTrue(InputRules.isValidId("3f2a7c0e-0000-4000-8000-000000000000"))
        assertFalse(InputRules.isValidId(null))
        assertFalse(InputRules.isValidId(""))
        assertFalse(InputRules.isValidId("x".repeat(InputRules.ID_MAX + 1)))
    }
}
