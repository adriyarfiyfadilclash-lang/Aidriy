package com.aidriy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

/** Aggregate row for a Berkas category or folder card. */
data class FileGroupStats(
    val count: Int,
    /** Newest created_at in the group, or null when empty. */
    val latest: Long?,
)

data class FolderWithStats(
    val id: String,
    val name: String,
    val createdAt: Long,
    val count: Int,
    val latest: Long?,
)

/**
 * `kind` filters by MIME family: 'document', 'image', 'audio', 'video' or NULL for all.
 * Documents are everything that is not image/audio/video.
 */
private const val KIND_FILTER = """
    (:kind IS NULL
     OR (:kind = 'image' AND mime_type LIKE 'image/%')
     OR (:kind = 'audio' AND mime_type LIKE 'audio/%')
     OR (:kind = 'video' AND mime_type LIKE 'video/%')
     OR (:kind = 'document' AND mime_type NOT LIKE 'image/%' AND mime_type NOT LIKE 'audio/%' AND mime_type NOT LIKE 'video/%'))
"""

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(file: FileEntity)

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getById(id: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id")
    fun observeById(id: String): Flow<FileEntity?>

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("UPDATE files SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String): Int

    @Query("UPDATE files SET folder_id = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?): Int

    @Query("UPDATE files SET extracted_text = :text, extraction_status = :status WHERE id = :id")
    suspend fun setExtraction(id: String, text: String?, status: String): Int

    @Query(
        """
        SELECT * FROM files
        WHERE name LIKE :pattern ESCAPE '!'
          AND $KIND_FILTER
          AND (:folderId IS NULL OR folder_id = :folderId)
        ORDER BY created_at DESC
        """,
    )
    fun observeFiles(pattern: String, kind: String?, folderId: String?): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE note_id = :noteId ORDER BY created_at ASC")
    fun observeForNote(noteId: String): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) AS count, MAX(created_at) AS latest FROM files WHERE $KIND_FILTER")
    fun observeStats(kind: String?): Flow<FileGroupStats>

    /** Files with readable text, for AI document analysis and local retrieval. */
    @Query("SELECT * FROM files WHERE extraction_status = 'DONE' ORDER BY created_at DESC")
    fun observeWithText(): Flow<List<FileEntity>>

    @Query(
        """
        SELECT * FROM files
        WHERE extraction_status = 'DONE' AND (name LIKE :pattern ESCAPE '!' OR extracted_text LIKE :pattern ESCAPE '!')
        ORDER BY created_at DESC LIMIT :limit
        """,
    )
    suspend fun searchText(pattern: String, limit: Int): List<FileEntity>

    @Query("SELECT * FROM files")
    suspend fun getAll(): List<FileEntity>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM files")
    fun observeTotalSize(): Flow<Long>

    // --- Folders ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getFolderByName(name: String): FolderEntity?

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: String, name: String): Int

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String): Int

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    suspend fun getFolders(): List<FolderEntity>

    @Query(
        """
        SELECT f.id AS id, f.name AS name, f.created_at AS createdAt,
               COUNT(x.id) AS count, MAX(x.created_at) AS latest
        FROM folders f LEFT JOIN files x ON x.folder_id = f.id
        GROUP BY f.id
        ORDER BY f.name COLLATE NOCASE
        """,
    )
    fun observeFoldersWithStats(): Flow<List<FolderWithStats>>
}
