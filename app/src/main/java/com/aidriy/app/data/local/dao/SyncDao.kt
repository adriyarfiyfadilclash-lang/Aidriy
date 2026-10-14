package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aidriy.app.data.local.entity.AiConversationEntity
import com.aidriy.app.data.local.entity.AiMessageEntity
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.SyncDirtyEntity
import com.aidriy.app.data.local.entity.SyncUploadedEntity
import kotlinx.coroutines.flow.Flow

/** Low-level access for cloud sync. Upserts update in place (never delete + insert), so links survive. */
@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_dirty ORDER BY seq ASC LIMIT :limit")
    suspend fun dirty(limit: Int): List<SyncDirtyEntity>

    @Query("SELECT COUNT(*) FROM sync_dirty")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM sync_dirty WHERE kind = :kind AND id = :id AND seq <= :seq")
    suspend fun clearDirty(kind: String, id: String, seq: Long)

    @Query("DELETE FROM sync_dirty")
    suspend fun clearAllDirty()

    @Query("UPDATE sync_meta SET paused = :paused WHERE id = 1")
    suspend fun setPaused(paused: Int)

    @Query("SELECT paused FROM sync_meta WHERE id = 1")
    suspend fun paused(): Int?

    // ------------------------------------------------------------------ reads

    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: String): NoteEntity?
    @Query("SELECT * FROM ideas WHERE id = :id") suspend fun idea(id: String): IdeaEntity?
    @Query("SELECT * FROM journal_entries WHERE id = :id") suspend fun journal(id: String): JournalEntryEntity?
    @Query("SELECT * FROM journal_entries WHERE date = :date") suspend fun journalByDate(date: String): JournalEntryEntity?
    @Query("SELECT * FROM folders WHERE id = :id") suspend fun folder(id: String): FolderEntity?
    @Query("SELECT * FROM files WHERE id = :id") suspend fun file(id: String): FileEntity?
    @Query("SELECT * FROM ai_conversations WHERE id = :id") suspend fun conversation(id: String): AiConversationEntity?
    @Query("SELECT * FROM ai_messages WHERE id = :id") suspend fun message(id: String): AiMessageEntity?

    @Query("SELECT t.name FROM tags t JOIN note_tags x ON x.tag_id = t.id WHERE x.note_id = :id ORDER BY t.name")
    suspend fun noteTags(id: String): List<String>

    @Query("SELECT t.name FROM tags t JOIN idea_tags x ON x.tag_id = t.id WHERE x.idea_id = :id ORDER BY t.name")
    suspend fun ideaTags(id: String): List<String>

    @Query("SELECT t.name FROM tags t JOIN journal_tags x ON x.tag_id = t.id WHERE x.journal_id = :id ORDER BY t.name")
    suspend fun journalTags(id: String): List<String>

    // ------------------------------------------------------------------ writes from the cloud

    @Upsert suspend fun upsertNote(row: NoteEntity)
    @Upsert suspend fun upsertIdea(row: IdeaEntity)
    @Upsert suspend fun upsertJournal(row: JournalEntryEntity)
    @Upsert suspend fun upsertFolder(row: FolderEntity)
    @Upsert suspend fun upsertFile(row: FileEntity)
    @Upsert suspend fun upsertConversation(row: AiConversationEntity)
    @Upsert suspend fun upsertMessage(row: AiMessageEntity)

    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
    @Query("DELETE FROM ideas WHERE id = :id") suspend fun deleteIdea(id: String)
    @Query("DELETE FROM journal_entries WHERE id = :id") suspend fun deleteJournal(id: String)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun deleteFolder(id: String)
    @Query("DELETE FROM files WHERE id = :id") suspend fun deleteFile(id: String)
    @Query("DELETE FROM ai_conversations WHERE id = :id") suspend fun deleteConversation(id: String)
    @Query("DELETE FROM ai_messages WHERE id = :id") suspend fun deleteMessage(id: String)

    // ------------------------------------------------------------------ uploads

    @Query("SELECT COUNT(*) > 0 FROM sync_uploaded WHERE id = :id")
    suspend fun isUploaded(id: String): Boolean

    @Upsert suspend fun markUploaded(row: SyncUploadedEntity)

    @Query("DELETE FROM sync_uploaded")
    suspend fun clearUploaded()
}
