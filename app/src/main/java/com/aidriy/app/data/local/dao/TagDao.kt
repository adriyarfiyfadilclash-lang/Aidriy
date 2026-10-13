package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aidriy.app.data.local.entity.IdeaTagCrossRef
import com.aidriy.app.data.local.entity.JournalTagCrossRef
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    /** Throws SQLiteConstraintException on a duplicate normalized name. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalized_name = :normalizedName")
    suspend fun getByNormalizedName(normalizedName: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT DISTINCT t.* FROM tags t
        INNER JOIN note_tags x ON x.tag_id = t.id
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeUsedByNotes(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT DISTINCT t.* FROM tags t
        INNER JOIN idea_tags x ON x.tag_id = t.id
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeUsedByIdeas(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT DISTINCT t.* FROM tags t
        INNER JOIN journal_tags x ON x.tag_id = t.id
        ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeUsedByJournal(): Flow<List<TagEntity>>

    // --- Notes ---

    @Query(
        """
        SELECT t.* FROM tags t INNER JOIN note_tags x ON x.tag_id = t.id
        WHERE x.note_id = :noteId ORDER BY t.name COLLATE NOCASE
        """,
    )
    suspend fun getForNote(noteId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToNote(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE note_id = :noteId AND tag_id = :tagId")
    suspend fun removeFromNote(noteId: String, tagId: String): Int

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearNote(noteId: String)

    // --- Ideas ---

    @Query(
        """
        SELECT t.* FROM tags t INNER JOIN idea_tags x ON x.tag_id = t.id
        WHERE x.idea_id = :ideaId ORDER BY t.name COLLATE NOCASE
        """,
    )
    suspend fun getForIdea(ideaId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToIdea(refs: List<IdeaTagCrossRef>)

    @Query("DELETE FROM idea_tags WHERE idea_id = :ideaId AND tag_id = :tagId")
    suspend fun removeFromIdea(ideaId: String, tagId: String): Int

    @Query("DELETE FROM idea_tags WHERE idea_id = :ideaId")
    suspend fun clearIdea(ideaId: String)

    // --- Journal ---

    @Query(
        """
        SELECT t.* FROM tags t INNER JOIN journal_tags x ON x.tag_id = t.id
        WHERE x.journal_id = :journalId ORDER BY t.name COLLATE NOCASE
        """,
    )
    suspend fun getForJournal(journalId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToJournal(refs: List<JournalTagCrossRef>)

    @Query("DELETE FROM journal_tags WHERE journal_id = :journalId AND tag_id = :tagId")
    suspend fun removeFromJournal(journalId: String, tagId: String): Int

    @Query("DELETE FROM journal_tags WHERE journal_id = :journalId")
    suspend fun clearJournal(journalId: String)

    /** Removes tags no record uses any more. Returns the number deleted. */
    @Query(
        """
        DELETE FROM tags WHERE id NOT IN (
            SELECT tag_id FROM note_tags
            UNION SELECT tag_id FROM idea_tags
            UNION SELECT tag_id FROM journal_tags
        )
        """,
    )
    suspend fun deleteUnused(): Int
}
