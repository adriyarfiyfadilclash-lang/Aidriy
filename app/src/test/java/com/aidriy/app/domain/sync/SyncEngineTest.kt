package com.aidriy.app.domain.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory cloud: server time is a zero-padded counter so strings sort like timestamps. */
private class FakeCloud : RemoteSync {
    var clock = 0
    val tables = mutableMapOf<SyncKind, MutableMap<String, SyncRecord>>()
    val uploads = mutableListOf<String>()

    private fun tick() = (++clock).toString().padStart(8, '0')
    private fun table(kind: SyncKind) = tables.getOrPut(kind) { linkedMapOf() }

    override suspend fun fetch(kind: SyncKind, since: String?, limit: Int): List<SyncRecord> =
        table(kind).values.filter { since == null || it.serverUpdated >= since }
            .sortedWith(compareBy({ it.serverUpdated }, { it.id })).take(limit)

    override suspend fun upsert(kind: SyncKind, records: List<SyncRecord>) {
        records.forEach { table(kind)[it.id] = it.copy(deleted = false, serverUpdated = tick()) }
    }

    override suspend fun markDeleted(kind: SyncKind, ids: List<String>) {
        ids.forEach { id -> table(kind)[id]?.let { table(kind)[id] = it.copy(deleted = true, serverUpdated = tick()) } }
    }

    override suspend fun retireOtherJournals(date: String, keepId: String) {
        table(SyncKind.JOURNAL).values.filter { it.fields["date"] == date && it.id != keepId && !it.deleted }
            .forEach { table(SyncKind.JOURNAL)[it.id] = it.copy(deleted = true, serverUpdated = tick()) }
    }

    override suspend fun uploadFile(record: SyncRecord) {
        uploads += record.id
    }
}

/** In-memory phone: rows per kind plus the dirty list a trigger would maintain. */
private class FakePhone : LocalSync {
    val rows = mutableMapOf<SyncKind, MutableMap<String, Map<String, Any?>>>()
    private val dirtyList = linkedMapOf<Pair<SyncKind, String>, Long>()
    private var seq = 0L
    private val cursors = mutableMapOf<SyncKind, Cursor>()
    val uploaded = mutableSetOf<String>()

    private fun table(kind: SyncKind) = rows.getOrPut(kind) { linkedMapOf() }

    // "User" actions: they mark rows dirty like the SQLite triggers do.
    fun write(kind: SyncKind, id: String, fields: Map<String, Any?>) {
        table(kind)[id] = fields
        dirtyList[kind to id] = ++seq
    }

    fun remove(kind: SyncKind, id: String) {
        table(kind).remove(id)
        dirtyList[kind to id] = ++seq
    }

    fun dirtyCount() = dirtyList.size

    override suspend fun dirty(limit: Int) = dirtyList.entries.take(limit).map { DirtyEntry(it.key.first, it.key.second, it.value) }

    override suspend fun read(kind: SyncKind, id: String) = table(kind)[id]?.let { SyncRecord(kind, id, it) }

    override suspend fun clearDirty(entries: List<DirtyEntry>) {
        entries.forEach { e -> if (dirtyList[e.kind to e.id] == e.seq) dirtyList.remove(e.kind to e.id) }
    }

    override suspend fun apply(records: List<SyncRecord>, skip: Set<Pair<SyncKind, String>>): Int {
        var n = 0
        records.filter { (it.kind to it.id) !in skip }.forEach { r ->
            if (r.deleted) table(r.kind).remove(r.id) else table(r.kind)[r.id] = r.fields
            n++
        }
        return n
    }

    override suspend fun cursor(kind: SyncKind) = cursors[kind] ?: Cursor()
    override suspend fun saveCursor(kind: SyncKind, cursor: Cursor) {
        cursors[kind] = cursor
    }

    override suspend fun ensureUploaded(record: SyncRecord, upload: suspend (SyncRecord) -> Unit) {
        if (record.id !in uploaded) {
            upload(record)
            uploaded += record.id
        }
    }
}

class SyncEngineTest {

    private val cloud = FakeCloud()
    private val phoneA = FakePhone()
    private val phoneB = FakePhone()
    private fun sync(phone: FakePhone) = runBlocking { SyncEngine(phone, cloud, pageSize = 2).sync() }
    private fun note(title: String) = mapOf("title" to title, "content" to "isi", "tags" to listOf("kerja"))

    @Test
    fun new_note_is_pushed_and_dirty_list_cleared() {
        phoneA.write(SyncKind.NOTES, "n1", note("Rapat"))
        val report = sync(phoneA)
        assertEquals(1, report.pushed)
        assertEquals("Rapat", cloud.tables[SyncKind.NOTES]!!["n1"]!!.fields["title"])
        assertEquals(0, phoneA.dirtyCount())
    }

    @Test
    fun second_phone_receives_notes_and_deletions() {
        phoneA.write(SyncKind.NOTES, "n1", note("Rapat"))
        phoneA.write(SyncKind.NOTES, "n2", note("Belanja"))
        phoneA.write(SyncKind.NOTES, "n3", note("Ide"))
        sync(phoneA)

        sync(phoneB)
        assertEquals(setOf("n1", "n2", "n3"), phoneB.rows[SyncKind.NOTES]!!.keys)

        phoneB.remove(SyncKind.NOTES, "n2")
        sync(phoneB)
        assertTrue(cloud.tables[SyncKind.NOTES]!!["n2"]!!.deleted)

        sync(phoneA)
        assertEquals(setOf("n1", "n3"), phoneA.rows[SyncKind.NOTES]!!.keys)
    }

    @Test
    fun unsent_local_edit_wins_over_cloud_and_is_pushed() {
        phoneA.write(SyncKind.NOTES, "n1", note("v1"))
        sync(phoneA)
        sync(phoneB)

        phoneB.write(SyncKind.NOTES, "n1", note("dari B"))
        sync(phoneB)
        phoneA.write(SyncKind.NOTES, "n1", note("dari A, belum terkirim"))
        sync(phoneA)

        assertEquals("dari A, belum terkirim", phoneA.rows[SyncKind.NOTES]!!["n1"]!!["title"])
        assertEquals("dari A, belum terkirim", cloud.tables[SyncKind.NOTES]!!["n1"]!!.fields["title"])
        sync(phoneB)
        assertEquals("dari A, belum terkirim", phoneB.rows[SyncKind.NOTES]!!["n1"]!!["title"])
    }

    @Test
    fun pulling_twice_does_not_reapply_rows() {
        phoneA.write(SyncKind.NOTES, "n1", note("a"))
        phoneA.write(SyncKind.NOTES, "n2", note("b"))
        sync(phoneA)
        assertEquals(2, sync(phoneB).pulled)
        assertEquals(0, sync(phoneB).pulled)
    }

    @Test
    fun one_journal_per_date_across_phones() {
        phoneA.write(SyncKind.JOURNAL, "jA", mapOf("date" to "2026-10-14", "content" to "dari A"))
        sync(phoneA)
        phoneB.write(SyncKind.JOURNAL, "jB", mapOf("date" to "2026-10-14", "content" to "dari B"))
        sync(phoneB)
        val live = cloud.tables[SyncKind.JOURNAL]!!.values.filter { !it.deleted }
        assertEquals(listOf("jB"), live.map { it.id })
    }

    @Test
    fun files_are_uploaded_once() {
        phoneA.write(SyncKind.FILES, "f1", mapOf("name" to "a.pdf"))
        sync(phoneA)
        phoneA.write(SyncKind.FILES, "f1", mapOf("name" to "b.pdf"))
        sync(phoneA)
        assertEquals(listOf("f1"), cloud.uploads)
    }

    @Test
    fun deleting_something_never_synced_is_harmless() {
        phoneA.write(SyncKind.NOTES, "tmp", note("x"))
        phoneA.remove(SyncKind.NOTES, "tmp")
        sync(phoneA)
        assertNull(cloud.tables[SyncKind.NOTES]?.get("tmp"))
        assertEquals(0, phoneA.dirtyCount())
    }

    @Test
    fun cursor_keeps_ids_seen_at_the_same_server_time() {
        val c = SyncEngine.advance(Cursor(), listOf(SyncRecord(SyncKind.NOTES, "a", emptyMap(), serverUpdated = "5")))
        val c2 = SyncEngine.advance(c, listOf(SyncRecord(SyncKind.NOTES, "b", emptyMap(), serverUpdated = "5")))
        assertEquals(Cursor("5", setOf("a", "b")), c2)
        assertFalse(SyncEngine.advance(c2, listOf(SyncRecord(SyncKind.NOTES, "c", emptyMap(), serverUpdated = "6"))).idsAtValue.contains("a"))
    }
}
