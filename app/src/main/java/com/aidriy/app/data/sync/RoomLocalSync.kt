package com.aidriy.app.data.sync

import androidx.room.withTransaction
import com.aidriy.app.data.cloud.SessionStore
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.local.entity.AiConversationEntity
import com.aidriy.app.data.local.entity.AiMessageEntity
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.SyncUploadedEntity
import com.aidriy.app.data.repository.TagWriter
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.sync.Cursor
import com.aidriy.app.domain.sync.DirtyEntry
import com.aidriy.app.domain.sync.LocalSync
import com.aidriy.app.domain.sync.SyncKind
import com.aidriy.app.domain.sync.SyncRecord
import java.io.File

/** [LocalSync] on the app's Room database. Cloud rows are applied with change tracking paused. */
internal class RoomLocalSync(
    private val db: AidriyDatabase,
    private val tags: TagWriter,
    private val sessions: SessionStore,
    private val storageDir: () -> File,
    /** Downloads a file's bytes after its row was applied; failures are retried on the next sync. */
    private val download: suspend (storagePath: String, target: File) -> Unit,
) : LocalSync {

    private val dao get() = db.syncDao()

    override suspend fun dirty(limit: Int): List<DirtyEntry> =
        dao.dirty(limit).mapNotNull { e -> SyncKind.fromTable(e.kind)?.let { DirtyEntry(it, e.id, e.seq) } }

    override suspend fun clearDirty(entries: List<DirtyEntry>) {
        db.withTransaction { entries.forEach { dao.clearDirty(it.kind.table, it.id, it.seq) } }
    }

    override suspend fun read(kind: SyncKind, id: String): SyncRecord? {
        val fields: Map<String, Any?> = when (kind) {
            SyncKind.FOLDERS -> dao.folder(id)?.let { mapOf("name" to it.name, "created_at" to it.createdAt) }
            SyncKind.NOTES -> dao.note(id)?.let {
                mapOf(
                    "title" to it.title, "content" to it.content, "created_at" to it.createdAt, "updated_at" to it.updatedAt,
                    "is_favorite" to it.isFavorite, "is_archived" to it.isArchived, "reminder_at" to it.reminderAt,
                    "folder_id" to it.folderId, "tags" to dao.noteTags(id),
                )
            }
            SyncKind.IDEAS -> dao.idea(id)?.let {
                mapOf(
                    "title" to it.title, "content" to it.content, "status" to it.status, "created_at" to it.createdAt,
                    "updated_at" to it.updatedAt, "tags" to dao.ideaTags(id),
                )
            }
            SyncKind.JOURNAL -> dao.journal(id)?.let {
                mapOf(
                    "date" to it.date, "title" to it.title, "content" to it.content, "created_at" to it.createdAt,
                    "updated_at" to it.updatedAt, "tags" to dao.journalTags(id),
                )
            }
            SyncKind.FILES -> dao.file(id)?.let {
                mapOf(
                    "name" to it.name, "mime_type" to it.mimeType, "size_bytes" to it.sizeBytes, "folder_id" to it.folderId,
                    "note_id" to it.noteId, "created_at" to it.createdAt, "extracted_text" to it.extractedText,
                    "extraction_status" to it.extractionStatus,
                )
            }
            SyncKind.AI_CONVERSATIONS -> dao.conversation(id)?.let {
                mapOf("title" to it.title, "created_at" to it.createdAt, "updated_at" to it.updatedAt)
            }
            SyncKind.AI_MESSAGES -> dao.message(id)?.let {
                mapOf(
                    "conversation_id" to it.conversationId, "role" to it.role, "content" to it.content, "provider" to it.provider,
                    "model" to it.model, "sources" to it.sources, "no_local_match" to it.noLocalMatch, "is_error" to it.isError,
                    "created_at" to it.createdAt, "rating" to it.rating,
                )
            }
        } ?: return null
        return SyncRecord(kind, id, fields)
    }

    override suspend fun apply(records: List<SyncRecord>, skip: Set<Pair<SyncKind, String>>): Int {
        val downloads = mutableListOf<Pair<String, File>>()
        var applied = 0
        db.withTransaction {
            val previous = dao.paused() ?: 0
            dao.setPaused(1)
            try {
                for (r in records) {
                    if ((r.kind to r.id) in skip) continue
                    if (applyOne(r, skip, downloads)) applied++
                }
            } finally {
                dao.setPaused(previous)
            }
        }
        for ((path, target) in downloads) {
            runCatching { download(path, target) }
        }
        return applied
    }

    private suspend fun applyOne(r: SyncRecord, skip: Set<Pair<SyncKind, String>>, downloads: MutableList<Pair<String, File>>): Boolean {
        val f = r.fields
        fun s(k: String) = f[k] as? String
        fun l(k: String) = (f[k] as? Number)?.toLong()
        fun b(k: String) = f[k] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        fun tagsOf() = cleanTags(f["tags"] as? List<String> ?: emptyList())
        suspend fun folderOrNull(id: String?) = id?.takeIf { dao.folder(it) != null }

        if (r.deleted) {
            when (r.kind) {
                SyncKind.FOLDERS -> dao.deleteFolder(r.id)
                SyncKind.NOTES -> dao.deleteNote(r.id).also { tags.pruneUnused() }
                SyncKind.IDEAS -> dao.deleteIdea(r.id).also { tags.pruneUnused() }
                SyncKind.JOURNAL -> dao.deleteJournal(r.id).also { tags.pruneUnused() }
                SyncKind.FILES -> {
                    dao.file(r.id)?.let { File(it.localPath).delete() }
                    dao.deleteFile(r.id)
                }
                SyncKind.AI_CONVERSATIONS -> dao.deleteConversation(r.id)
                SyncKind.AI_MESSAGES -> dao.deleteMessage(r.id)
            }
            return true
        }
        when (r.kind) {
            SyncKind.FOLDERS -> dao.upsertFolder(FolderEntity(r.id, s("name").orEmpty(), l("created_at") ?: 0))
            SyncKind.NOTES -> {
                dao.upsertNote(
                    NoteEntity(
                        id = r.id, title = s("title").orEmpty(), content = s("content").orEmpty(),
                        createdAt = l("created_at") ?: 0, updatedAt = l("updated_at") ?: 0,
                        isFavorite = b("is_favorite"), isArchived = b("is_archived"),
                        reminderAt = l("reminder_at"), folderId = folderOrNull(s("folder_id")),
                    ),
                )
                tags.replaceNoteTags(r.id, tagsOf())
            }
            SyncKind.IDEAS -> {
                dao.upsertIdea(
                    IdeaEntity(
                        id = r.id, title = s("title").orEmpty(), content = s("content").orEmpty(), status = s("status") ?: "INBOX",
                        createdAt = l("created_at") ?: 0, updatedAt = l("updated_at") ?: 0,
                    ),
                )
                tags.replaceIdeaTags(r.id, tagsOf())
            }
            SyncKind.JOURNAL -> {
                val date = s("date") ?: return false
                val sameDay = dao.journalByDate(date)
                if (sameDay != null && sameDay.id != r.id) {
                    // One entry per date: an unsent local entry wins, otherwise the cloud one replaces it.
                    if ((SyncKind.JOURNAL to sameDay.id) in skip) return false
                    dao.deleteJournal(sameDay.id)
                }
                dao.upsertJournal(
                    JournalEntryEntity(
                        id = r.id, date = date, title = s("title").orEmpty(), content = s("content").orEmpty(),
                        createdAt = l("created_at") ?: 0, updatedAt = l("updated_at") ?: 0,
                    ),
                )
                tags.replaceJournalTags(r.id, tagsOf())
            }
            SyncKind.FILES -> {
                val target = File(storageDir(), r.id)
                dao.upsertFile(
                    FileEntity(
                        id = r.id, name = s("name").orEmpty(), mimeType = s("mime_type") ?: "application/octet-stream",
                        sizeBytes = l("size_bytes") ?: 0, localPath = target.absolutePath,
                        folderId = folderOrNull(s("folder_id")), noteId = s("note_id")?.takeIf { dao.note(it) != null },
                        createdAt = l("created_at") ?: 0, extractedText = s("extracted_text"),
                        extractionStatus = s("extraction_status") ?: "PENDING",
                    ),
                )
                // Bytes from the cloud are already uploaded; fetch them if this phone lacks them.
                dao.markUploaded(SyncUploadedEntity(r.id, System.currentTimeMillis()))
                val path = s("storage_path")
                if (path != null && !target.exists()) downloads += path to target
            }
            SyncKind.AI_CONVERSATIONS -> dao.upsertConversation(
                AiConversationEntity(r.id, s("title").orEmpty(), l("created_at") ?: 0, l("updated_at") ?: 0),
            )
            SyncKind.AI_MESSAGES -> {
                val conversation = s("conversation_id") ?: return false
                if (dao.conversation(conversation) == null) return false
                dao.upsertMessage(
                    AiMessageEntity(
                        id = r.id, conversationId = conversation, role = s("role") ?: "user", content = s("content").orEmpty(),
                        provider = s("provider"), model = s("model"), sources = s("sources").orEmpty(),
                        noLocalMatch = b("no_local_match"), isError = b("is_error"), createdAt = l("created_at") ?: 0,
                        rating = (f["rating"] as? Number)?.toInt() ?: 0,
                    ),
                )
            }
        }
        return true
    }

    private fun cleanTags(names: List<String>): List<String> = when (val r = TagNames.cleanList(names)) {
        is DataResult.Success -> r.value
        is DataResult.Failure -> names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { TagNames.normalize(it) }.take(10)
    }

    override suspend fun cursor(kind: SyncKind): Cursor {
        val (value, ids) = sessions.cursor(kind.table)
        return Cursor(value, ids)
    }

    override suspend fun saveCursor(kind: SyncKind, cursor: Cursor) {
        sessions.saveCursor(kind.table, cursor.value, cursor.idsAtValue)
    }

    override suspend fun ensureUploaded(record: SyncRecord, upload: suspend (SyncRecord) -> Unit) {
        if (dao.isUploaded(record.id)) return
        upload(record)
        dao.markUploaded(SyncUploadedEntity(record.id, System.currentTimeMillis()))
    }
}
