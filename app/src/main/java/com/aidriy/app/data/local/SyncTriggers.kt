package com.aidriy.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite triggers that record every local change in `sync_dirty`. They fire only while
 * `sync_meta.paused = 0` (signed in and not applying rows that came from the cloud).
 */
object SyncTriggers {

    /** Synced tables and the cloud table (kind) their changes belong to. */
    private val tables = listOf(
        "notes", "ideas", "journal_entries", "folders", "files", "ai_conversations", "ai_messages",
    )

    /** Tag links count as a change of their parent record. */
    private val links = listOf(
        Triple("note_tags", "notes", "note_id"),
        Triple("idea_tags", "ideas", "idea_id"),
        Triple("journal_tags", "journal_entries", "journal_id"),
    )

    private const val ACTIVE = "(SELECT paused FROM sync_meta WHERE id = 1) = 0"

    private fun mark(kind: String, idExpr: String) =
        "INSERT OR REPLACE INTO sync_dirty (kind, id, seq) " +
            "VALUES ('$kind', $idExpr, (SELECT IFNULL(MAX(seq), 0) + 1 FROM sync_dirty));"

    fun statements(): List<String> = buildList {
        add("INSERT OR IGNORE INTO sync_meta (id, paused) VALUES (1, 1)")
        tables.forEach { t ->
            add("CREATE TRIGGER IF NOT EXISTS sync_${t}_ins AFTER INSERT ON `$t` WHEN $ACTIVE BEGIN ${mark(t, "NEW.id")} END")
            add("CREATE TRIGGER IF NOT EXISTS sync_${t}_upd AFTER UPDATE ON `$t` WHEN $ACTIVE BEGIN ${mark(t, "NEW.id")} END")
            add("CREATE TRIGGER IF NOT EXISTS sync_${t}_del AFTER DELETE ON `$t` WHEN $ACTIVE BEGIN ${mark(t, "OLD.id")} END")
        }
        links.forEach { (link, kind, column) ->
            add("CREATE TRIGGER IF NOT EXISTS sync_${link}_ins AFTER INSERT ON `$link` WHEN $ACTIVE BEGIN ${mark(kind, "NEW.$column")} END")
            add("CREATE TRIGGER IF NOT EXISTS sync_${link}_del AFTER DELETE ON `$link` WHEN $ACTIVE BEGIN ${mark(kind, "OLD.$column")} END")
        }
    }

    fun install(db: SupportSQLiteDatabase) {
        statements().forEach(db::execSQL)
    }

    /** Every existing record, e.g. right after signing in on a phone that already has data. */
    fun markAllSql(): List<String> = tables.map { t ->
        "INSERT OR REPLACE INTO sync_dirty (kind, id, seq) " +
            "SELECT '$t', id, (SELECT IFNULL(MAX(seq), 0) FROM sync_dirty) + ROWID FROM `$t`"
    }
}
