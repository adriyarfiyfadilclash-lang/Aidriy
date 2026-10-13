package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.JournalWithTags
import kotlinx.coroutines.flow.Flow

/** All `date` parameters are ISO local dates (yyyy-MM-dd), which compare correctly as text. */
@Dao
interface JournalDao {

    /** Throws SQLiteConstraintException if an entry already exists for the same date. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: JournalEntryEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(entry: JournalEntryEntity): Int

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE date = :date")
    suspend fun getByDate(date: String): JournalEntryEntity?

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getWithTags(id: String): JournalWithTags?

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE date = :date")
    fun observeByDate(date: String): Flow<JournalWithTags?>

    /** Newest date first. */
    @Transaction
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE (title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!')
          AND (:tagId IS NULL OR id IN (SELECT journal_id FROM journal_tags WHERE tag_id = :tagId))
        ORDER BY date DESC
        """,
    )
    fun observeFiltered(pattern: String, tagId: String?): Flow<List<JournalWithTags>>

    /** Inclusive range, oldest first (chronological). */
    @Transaction
    @Query("SELECT * FROM journal_entries WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeRange(start: String, end: String): Flow<List<JournalWithTags>>

    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<JournalWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!'
        ORDER BY date DESC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, limit: Int): Flow<List<JournalWithTags>>
}
