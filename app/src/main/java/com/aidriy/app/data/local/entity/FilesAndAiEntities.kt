package com.aidriy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-created folder in Berkas. */
@Entity(
    tableName = "folders",
    indices = [Index(value = ["name"])],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * A file copied into app-private storage (filesDir/berkas/<id>). Copying (instead of keeping a
 * content:// link) means the file stays readable even if the original is moved or deleted.
 */
@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["note_id"]),
        Index(value = ["created_at"]),
    ],
)
data class FileEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    /** Absolute path inside the app's private files directory. */
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    /** Set when the file is an attachment of a note. */
    @ColumnInfo(name = "note_id") val noteId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "extracted_text") val extractedText: String?,
    /** One of ExtractionStatus.name. */
    @ColumnInfo(name = "extraction_status") val extractionStatus: String,
)

@Entity(
    tableName = "ai_conversations",
    indices = [Index(value = ["updated_at"])],
)
data class AiConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AiConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversation_id", "created_at"])],
)
data class AiMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    /** "user" or "assistant". */
    val role: String,
    val content: String,
    /** Provider and model that produced an assistant message; null for user messages. */
    val provider: String?,
    val model: String?,
    /** Encoded list of local sources used ("note:<id>", "idea:<id>", "journal:<date>", "file:<id>"), newline-separated. */
    val sources: String,
    /** True when retrieval ran but found nothing relevant in local data. */
    @ColumnInfo(name = "no_local_match") val noLocalMatch: Boolean,
    @ColumnInfo(name = "is_error") val isError: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
