package com.aidriy.app.domain.repository

import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.domain.model.JournalFilter
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** See [NoteRepository] for the shared repository conventions. All dates are local calendar dates. */
interface JournalRepository {
    /** Newest date first. */
    fun observeEntries(filter: JournalFilter): Flow<List<JournalEntry>>
    fun observeRecent(limit: Int): Flow<List<JournalEntry>>
    fun observeByDate(date: LocalDate): Flow<JournalEntry?>
    /** Inclusive range, oldest first. */
    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>>
    suspend fun getByDate(date: LocalDate): JournalEntry?

    /**
     * Creates or updates the single entry for [date].
     * [expectedId] is the id the editor loaded (null when the editor started empty).
     * If an entry appeared for that date meanwhile, returns DuplicateJournalDate instead of
     * overwriting it, so the user can decide.
     */
    suspend fun save(date: LocalDate, draft: JournalDraft, expectedId: String?): DataResult<JournalEntry>
    suspend fun delete(id: String): DataResult<Unit>
}
