package com.aidriy.app.domain.repository

import com.aidriy.app.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Repository convention used across AIDRIY:
 * - Reads are exposed as [Flow] so screens update reactively when local data changes.
 * - Writes are `suspend` functions and run off the main thread inside the implementation.
 * - The local database is the single source of truth; no method requires network access.
 *
 * Implemented with Room in Phase 2.
 */
interface NoteRepository {
    fun observeNotes(includeArchived: Boolean = false): Flow<List<Note>>
    fun observeRecent(limit: Int): Flow<List<Note>>
    suspend fun getById(id: String): Note?
    suspend fun upsert(note: Note)
    suspend fun delete(id: String)
}
