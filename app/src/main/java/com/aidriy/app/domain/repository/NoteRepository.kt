package com.aidriy.app.domain.repository

import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.NoteFilter
import kotlinx.coroutines.flow.Flow

/**
 * Repository convention used across AIDRIY:
 * - Reads are exposed as [Flow] so screens update reactively when local data changes.
 * - Writes are `suspend`, validate their input, and return [DataResult]; a Success is only
 *   returned after the database committed the change.
 * - The local database is the single source of truth; no method requires network access.
 */
interface NoteRepository {
    fun observeNotes(filter: NoteFilter): Flow<List<Note>>
    fun observeRecent(limit: Int): Flow<List<Note>>
    /** Emits null if the note does not exist or is deleted while observed. */
    fun observeNote(id: String): Flow<Note?>
    suspend fun getById(id: String): Note?

    suspend fun create(draft: NoteDraft, isFavorite: Boolean = false): DataResult<Note>
    /** Replaces title, content and tags. Favorite and archive state are kept. */
    suspend fun update(id: String, draft: NoteDraft): DataResult<Note>
    suspend fun setFavorite(id: String, favorite: Boolean): DataResult<Unit>
    suspend fun setArchived(id: String, archived: Boolean): DataResult<Unit>
    suspend fun delete(id: String): DataResult<Unit>
}
