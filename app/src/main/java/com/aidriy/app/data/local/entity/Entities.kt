package com.aidriy.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema version 1. Column names are snake_case and stable: renaming a column requires a migration.
 * Timestamps are UTC epoch milliseconds. Journal dates are local calendar dates (ISO yyyy-MM-dd).
 */

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["created_at"]),
        Index(value = ["is_archived", "is_favorite"]),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
)

@Entity(
    tableName = "ideas",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["created_at"]),
    ],
)
data class IdeaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    /** One of IdeaStatus.storageKey; validated by the repository before every write. */
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "journal_entries",
    // One entry per local calendar date (MVP rule), enforced by the database itself.
    indices = [Index(value = ["date"], unique = true)],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    /** Local calendar date, ISO-8601 (yyyy-MM-dd). Sorts correctly as text. */
    val date: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalized_name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    /** Display name as the user typed it (cleaned). */
    val name: String,
    /** Lowercased, trimmed name used for duplicate detection. */
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val color: String?,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "idea_tags",
    primaryKeys = ["idea_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = IdeaEntity::class,
            parentColumns = ["id"],
            childColumns = ["idea_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class IdeaTagCrossRef(
    @ColumnInfo(name = "idea_id") val ideaId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "journal_tags",
    primaryKeys = ["journal_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class JournalTagCrossRef(
    @ColumnInfo(name = "journal_id") val journalId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)
