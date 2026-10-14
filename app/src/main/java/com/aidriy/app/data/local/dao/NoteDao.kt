package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.NoteWithTags
import kotlinx.coroutines.flow.Flow

/*
 * One parameterized query covers the active / favorites / archived views, search,
 * tag filter and sort. `pattern` comes from SearchQuery.toLikePattern (escape char '!').
 */
private const val FILTERED_NOTES = """
    SELECT * FROM notes
    WHERE is_archived = :archived
      AND (:favoritesOnly = 0 OR is_favorite = 1)
      AND (title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!')
      AND (:tagId IS NULL OR id IN (SELECT note_id FROM note_tags WHERE tag_id = :tagId))
    ORDER BY CASE WHEN :sortByCreated = 1 THEN created_at ELSE updated_at END DESC, id ASC
"""

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    /** Returns the number of updated rows (0 when the note no longer exists). */
    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getWithTags(id: String): NoteWithTags?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeWithTags(id: String): Flow<NoteWithTags?>

    /** Flags are metadata: they do not change updated_at, so lists don't reorder on a star tap. */
    @Query("UPDATE notes SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean): Int

    @Query("UPDATE notes SET is_archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean): Int

    @Transaction
    @Query(FILTERED_NOTES)
    fun observeFiltered(
        archived: Boolean,
        favoritesOnly: Boolean,
        pattern: String,
        tagId: String?,
        sortByCreated: Boolean,
    ): Flow<List<NoteWithTags>>

    @Query("UPDATE notes SET reminder_at = :reminderAt WHERE id = :id")
    suspend fun setReminder(id: String, reminderAt: Long?): Int

    @Query("SELECT * FROM notes WHERE reminder_at IS NOT NULL")
    suspend fun getWithReminders(): List<NoteEntity>

    @Query("SELECT COUNT(*) AS count, MAX(updated_at) AS latest FROM notes WHERE is_archived = :archived")
    fun observeStats(archived: Boolean): Flow<FileGroupStats>

    @Transaction
    @Query("SELECT * FROM notes WHERE is_archived = 0 ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteWithTags>>

    /** Global search: includes archived notes so nothing stored is unfindable. */
    @Transaction
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!'
        ORDER BY is_archived ASC, updated_at DESC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, limit: Int): Flow<List<NoteWithTags>>
}
