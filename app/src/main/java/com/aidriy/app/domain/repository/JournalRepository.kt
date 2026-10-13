package com.aidriy.app.domain.repository

import com.aidriy.app.domain.model.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** See [NoteRepository] for the shared repository conventions. Implemented in Phase 2. */
interface JournalRepository {
    fun observeEntries(): Flow<List<JournalEntry>>
    fun observeRecent(limit: Int): Flow<List<JournalEntry>>
    /** At most one entry exists per date; the database enforces this with a unique index. */
    suspend fun getByDate(date: LocalDate): JournalEntry?
    suspend fun upsert(entry: JournalEntry)
    suspend fun delete(id: String)
}
