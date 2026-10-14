package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.IdeaTagCrossRef
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.JournalTagCrossRef
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.entity.TagEntity

/** Whole-table reads and writes for local backup and restore. Writes run inside one transaction. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM ideas") suspend fun ideas(): List<IdeaEntity>
    @Query("SELECT * FROM journal_entries") suspend fun journal(): List<JournalEntryEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM note_tags") suspend fun noteTags(): List<NoteTagCrossRef>
    @Query("SELECT * FROM idea_tags") suspend fun ideaTags(): List<IdeaTagCrossRef>
    @Query("SELECT * FROM journal_tags") suspend fun journalTags(): List<JournalTagCrossRef>
    @Query("SELECT * FROM folders") suspend fun folders(): List<FolderEntity>
    @Query("SELECT * FROM files") suspend fun files(): List<FileEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNotes(rows: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIdeas(rows: List<IdeaEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertJournal(rows: List<JournalEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTags(rows: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNoteTags(rows: List<NoteTagCrossRef>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertIdeaTags(rows: List<IdeaTagCrossRef>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertJournalTags(rows: List<JournalTagCrossRef>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFolders(rows: List<FolderEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertFiles(rows: List<FileEntity>)

    // Children first so foreign keys never block the delete.
    @Query("DELETE FROM note_tags") suspend fun clearNoteTags()
    @Query("DELETE FROM idea_tags") suspend fun clearIdeaTags()
    @Query("DELETE FROM journal_tags") suspend fun clearJournalTags()
    @Query("DELETE FROM files") suspend fun clearFiles()
    @Query("DELETE FROM folders") suspend fun clearFolders()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM ideas") suspend fun clearIdeas()
    @Query("DELETE FROM journal_entries") suspend fun clearJournal()
}
