package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.IdeaWithTags
import kotlinx.coroutines.flow.Flow

private const val FILTERED_IDEAS = """
    SELECT * FROM ideas
    WHERE (:status IS NULL OR status = :status)
      AND (title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!')
      AND (:tagId IS NULL OR id IN (SELECT idea_id FROM idea_tags WHERE tag_id = :tagId))
    ORDER BY CASE WHEN :sortByCreated = 1 THEN created_at ELSE updated_at END DESC, id ASC
"""

@Dao
interface IdeaDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(idea: IdeaEntity)

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(idea: IdeaEntity): Int

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getById(id: String): IdeaEntity?

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getWithTags(id: String): IdeaWithTags?

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :id")
    fun observeWithTags(id: String): Flow<IdeaWithTags?>

    @Query("UPDATE ideas SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, updatedAt: Long): Int

    /** [status] null means every status. */
    @Transaction
    @Query(FILTERED_IDEAS)
    fun observeFiltered(
        status: String?,
        pattern: String,
        tagId: String?,
        sortByCreated: Boolean,
    ): Flow<List<IdeaWithTags>>

    @Transaction
    @Query("SELECT * FROM ideas ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<IdeaWithTags>>

    @Transaction
    @Query(
        """
        SELECT * FROM ideas
        WHERE title LIKE :pattern ESCAPE '!' OR content LIKE :pattern ESCAPE '!'
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun search(pattern: String, limit: Int): Flow<List<IdeaWithTags>>
}
