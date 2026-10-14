package com.aidriy.app.domain.sync

/** Column types used in the cloud tables (see the Supabase migration "aidriy_sync_schema"). */
enum class ColumnType { TEXT, LONG, INT, BOOL, TAGS }

data class Column(val name: String, val type: ColumnType, val nullable: Boolean = false)

/** Synced columns per table, besides id, user_id, deleted and server_updated (managed separately). */
object SyncSchema {
    private fun text(n: String, nullable: Boolean = false) = Column(n, ColumnType.TEXT, nullable)
    private fun long(n: String, nullable: Boolean = false) = Column(n, ColumnType.LONG, nullable)
    private fun bool(n: String) = Column(n, ColumnType.BOOL)

    val columns: Map<SyncKind, List<Column>> = mapOf(
        SyncKind.FOLDERS to listOf(text("name"), long("created_at")),
        SyncKind.NOTES to listOf(
            text("title"), text("content"), long("created_at"), long("updated_at"), bool("is_favorite"),
            bool("is_archived"), long("reminder_at", nullable = true), text("folder_id", nullable = true),
            Column("tags", ColumnType.TAGS),
        ),
        SyncKind.IDEAS to listOf(
            text("title"), text("content"), text("status"), long("created_at"), long("updated_at"), Column("tags", ColumnType.TAGS),
        ),
        SyncKind.JOURNAL to listOf(
            text("date"), text("title"), text("content"), long("created_at"), long("updated_at"), Column("tags", ColumnType.TAGS),
        ),
        SyncKind.FILES to listOf(
            text("name"), text("mime_type"), long("size_bytes"), text("folder_id", nullable = true),
            text("note_id", nullable = true), long("created_at"), text("extracted_text", nullable = true),
            text("extraction_status"), text("storage_path", nullable = true),
        ),
        SyncKind.AI_CONVERSATIONS to listOf(text("title"), long("created_at"), long("updated_at")),
        SyncKind.AI_MESSAGES to listOf(
            text("conversation_id"), text("role"), text("content"), text("provider", nullable = true),
            text("model", nullable = true), text("sources"), bool("no_local_match"), bool("is_error"),
            long("created_at"), Column("rating", ColumnType.INT),
        ),
    )
}
