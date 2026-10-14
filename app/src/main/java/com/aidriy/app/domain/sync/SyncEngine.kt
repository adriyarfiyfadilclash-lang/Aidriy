package com.aidriy.app.domain.sync

/*
 * Two-way sync between the local Room database and the user's cloud copy (Supabase).
 *
 * - Local changes are recorded by SQLite triggers in a "dirty" list (kind + id). Push sends the
 *   current row, or a tombstone when the row no longer exists locally.
 * - Pull reads cloud rows changed since the last cursor (server timestamp) and applies them,
 *   except rows that still have unsent local changes: those win and are pushed afterwards.
 * - Deletions travel as tombstones (deleted = true), so every device removes the record.
 *
 * Pure Kotlin: the Room and HTTP sides are behind [LocalSync] and [RemoteSync].
 */

/** Cloud table names; also the "kind" stored in the local dirty list. Order = safe apply order. */
enum class SyncKind(val table: String) {
    FOLDERS("folders"),
    NOTES("notes"),
    IDEAS("ideas"),
    JOURNAL("journal_entries"),
    FILES("files"),
    AI_CONVERSATIONS("ai_conversations"),
    AI_MESSAGES("ai_messages");

    companion object {
        fun fromTable(table: String): SyncKind? = entries.firstOrNull { it.table == table }
    }
}

/** One row as plain values (String, Long, Int, Boolean, List<String>, null). */
data class SyncRecord(
    val kind: SyncKind,
    val id: String,
    val fields: Map<String, Any?>,
    val deleted: Boolean = false,
    /** Server change time, as returned by the cloud (opaque, ordered). Empty for local rows. */
    val serverUpdated: String = "",
)

data class DirtyEntry(val kind: SyncKind, val id: String, val seq: Long)

interface LocalSync {
    suspend fun dirty(limit: Int): List<DirtyEntry>
    suspend fun read(kind: SyncKind, id: String): SyncRecord?
    suspend fun clearDirty(entries: List<DirtyEntry>)

    /** Applies cloud rows in one transaction without marking them dirty. Returns how many changed. */
    suspend fun apply(records: List<SyncRecord>, skip: Set<Pair<SyncKind, String>>): Int

    suspend fun cursor(kind: SyncKind): Cursor
    suspend fun saveCursor(kind: SyncKind, cursor: Cursor)

    /** Uploads the bytes of a Berkas file if the cloud does not have them yet. */
    suspend fun ensureUploaded(record: SyncRecord, upload: suspend (SyncRecord) -> Unit)
}

interface RemoteSync {
    /** Rows of [kind] with server time >= cursor.value, oldest first. */
    suspend fun fetch(kind: SyncKind, since: String?, limit: Int): List<SyncRecord>
    suspend fun upsert(kind: SyncKind, records: List<SyncRecord>)
    suspend fun markDeleted(kind: SyncKind, ids: List<String>)

    /** One journal entry per date: older cloud entries with the same date are retired. */
    suspend fun retireOtherJournals(date: String, keepId: String)
    suspend fun uploadFile(record: SyncRecord)
}

/** Pull position: the last server time seen and the ids already applied at exactly that time. */
data class Cursor(val value: String? = null, val idsAtValue: Set<String> = emptySet())

data class SyncReport(val pulled: Int, val pushed: Int)

class SyncEngine(
    private val local: LocalSync,
    private val remote: RemoteSync,
    private val pageSize: Int = 500,
) {
    suspend fun sync(): SyncReport {
        val pulled = pull()
        val pushed = push()
        return SyncReport(pulled, pushed)
    }

    suspend fun pull(): Int {
        var applied = 0
        for (kind in SyncKind.entries) {
            var cursor = local.cursor(kind)
            while (true) {
                val page = remote.fetch(kind, cursor.value, pageSize)
                val fresh = page.filter { !(it.serverUpdated == cursor.value && it.id in cursor.idsAtValue) }
                if (fresh.isEmpty()) break
                val skip = local.dirty(Int.MAX_VALUE).map { it.kind to it.id }.toSet()
                applied += local.apply(fresh, skip)
                cursor = advance(cursor, fresh)
                local.saveCursor(kind, cursor)
                if (page.size < pageSize) break
            }
        }
        return applied
    }

    suspend fun push(): Int {
        var pushed = 0
        while (true) {
            val batch = local.dirty(pageSize)
            if (batch.isEmpty()) break
            for ((kind, entries) in batch.groupBy { it.kind }.toSortedMap()) {
                val rows = entries.map { it to local.read(kind, it.id) }
                val present = rows.mapNotNull { it.second }
                val gone = rows.filter { it.second == null }.map { it.first.id }
                if (kind == SyncKind.FILES) {
                    present.forEach { record -> local.ensureUploaded(record) { remote.uploadFile(it) } }
                }
                if (present.isNotEmpty()) remote.upsert(kind, present)
                if (kind == SyncKind.JOURNAL) {
                    present.forEach { r -> (r.fields["date"] as? String)?.let { remote.retireOtherJournals(it, r.id) } }
                }
                if (gone.isNotEmpty()) remote.markDeleted(kind, gone)
                pushed += entries.size
            }
            local.clearDirty(batch)
            if (batch.size < pageSize) break
        }
        return pushed
    }

    companion object {
        fun advance(cursor: Cursor, page: List<SyncRecord>): Cursor {
            val last = page.last().serverUpdated
            val atLast = page.filter { it.serverUpdated == last }.map { it.id }.toSet()
            return if (last == cursor.value) cursor.copy(idsAtValue = cursor.idsAtValue + atLast) else Cursor(last, atLast)
        }
    }
}
