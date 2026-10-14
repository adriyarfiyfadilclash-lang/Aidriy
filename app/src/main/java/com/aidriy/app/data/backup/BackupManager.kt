package com.aidriy.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aidriy.app.data.files.FileRepository
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.IdeaTagCrossRef
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.entity.JournalTagCrossRef
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.entity.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val notes: Int,
    val ideas: Int,
    val journalEntries: Int,
    val files: Int,
    val createdAt: Long,
)

sealed interface BackupResult<out T> {
    data class Ok<T>(val value: T) : BackupResult<T>
    data class Error(val reason: BackupProblem) : BackupResult<Nothing>
}

enum class BackupProblem { NOT_A_BACKUP, NEWER_VERSION, DAMAGED, IO }

/** Parsed and validated backup, ready to replace local data. */
class PendingRestore internal constructor(
    val summary: BackupSummary,
    internal val data: BackupData,
    internal val extractedFilesDir: File,
)

internal data class BackupData(
    val notes: List<NoteEntity>,
    val ideas: List<IdeaEntity>,
    val journal: List<JournalEntryEntity>,
    val tags: List<TagEntity>,
    val noteTags: List<NoteTagCrossRef>,
    val ideaTags: List<IdeaTagCrossRef>,
    val journalTags: List<JournalTagCrossRef>,
    val folders: List<FolderEntity>,
    val files: List<FileEntity>,
    val createdAt: Long,
)

/**
 * Exports notes, ideas, journal, tags, folders and Berkas files to a single .zip the user saves
 * wherever they choose. Nothing is uploaded. AI chat history and API keys are never included.
 */
class BackupManager(
    private val context: Context,
    private val db: AidriyDatabase,
    private val files: FileRepository,
) {
    suspend fun export(target: Uri): BackupResult<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val dao = db.backupDao()
            val data = BackupData(
                dao.notes(), dao.ideas(), dao.journal(), dao.tags(), dao.noteTags(), dao.ideaTags(),
                dao.journalTags(), dao.folders(), dao.files(), System.currentTimeMillis(),
            )
            val stream = context.contentResolver.openOutputStream(target, "w")
                ?: return@withContext BackupResult.Error(BackupProblem.IO)
            ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(toJson(data).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                data.files.forEach { f ->
                    val source = File(f.localPath)
                    if (source.exists()) {
                        zip.putNextEntry(ZipEntry("$FILES_DIR/${f.id}"))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            BackupResult.Ok(data.summary())
        } catch (e: IOException) {
            BackupResult.Error(BackupProblem.IO)
        } catch (e: SecurityException) {
            BackupResult.Error(BackupProblem.IO)
        }
    }

    /** Reads and validates a backup without changing anything. */
    suspend fun read(source: Uri): BackupResult<PendingRestore> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var manifest: String? = null
            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext BackupResult.Error(BackupProblem.IO)
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == MANIFEST -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("$FILES_DIR/") && !entry.isDirectory -> {
                            val id = entry.name.removePrefix("$FILES_DIR/")
                            // Reject path tricks: ids are flat UUID-like names.
                            if (id.isNotEmpty() && !id.contains('/') && !id.contains("..")) {
                                File(staging, id).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
            val json = manifest ?: return@withContext BackupResult.Error(BackupProblem.NOT_A_BACKUP)
            val parsed = fromJson(JSONObject(json))
            parsed?.let { BackupResult.Ok(PendingRestore(it.summary(), it, staging)) }
                ?: BackupResult.Error(BackupProblem.NOT_A_BACKUP)
        } catch (e: NewerBackupException) {
            BackupResult.Error(BackupProblem.NEWER_VERSION)
        } catch (e: JSONException) {
            BackupResult.Error(BackupProblem.DAMAGED)
        } catch (e: IOException) {
            BackupResult.Error(BackupProblem.DAMAGED)
        } catch (e: SecurityException) {
            BackupResult.Error(BackupProblem.IO)
        }
    }

    /**
     * Replaces all notes, ideas, journal, tags, folders and files with the backup, in one
     * database transaction. If anything fails, the database keeps its previous content.
     */
    suspend fun restore(pending: PendingRestore): BackupResult<BackupSummary> = withContext(Dispatchers.IO) {
        val dir = files.storageDir
        val data = pending.data
        val newFiles = data.files.filter { File(pending.extractedFilesDir, it.id).exists() }
            .map { it.copy(localPath = File(dir, it.id).absolutePath) }
        try {
            val oldFiles = db.backupDao().files()
            db.withTransaction {
                val dao = db.backupDao()
                dao.clearNoteTags(); dao.clearIdeaTags(); dao.clearJournalTags()
                dao.clearFiles(); dao.clearFolders(); dao.clearTags()
                dao.clearNotes(); dao.clearIdeas(); dao.clearJournal()
                dao.insertIdeas(data.ideas)
                dao.insertJournal(data.journal)
                dao.insertTags(data.tags)
                dao.insertNoteTags(data.noteTags)
                dao.insertIdeaTags(data.ideaTags)
                dao.insertJournalTags(data.journalTags)
                // Folders first: notes and files reference them.
                dao.insertFolders(data.folders)
                dao.insertNotes(data.notes.map { it.copy(reminderAt = null) })
                dao.insertFiles(newFiles)
            }
            // Database committed: now swap stored file copies.
            oldFiles.forEach { File(it.localPath).delete() }
            newFiles.forEach { f -> File(pending.extractedFilesDir, f.id).copyTo(File(f.localPath), overwrite = true) }
            pending.extractedFilesDir.deleteRecursively()
            BackupResult.Ok(data.copy(files = newFiles).summary())
        } catch (e: IOException) {
            BackupResult.Error(BackupProblem.IO)
        } catch (e: android.database.SQLException) {
            BackupResult.Error(BackupProblem.DAMAGED)
        }
    }

    private fun BackupData.summary() = BackupSummary(notes.size, ideas.size, journal.size, files.size, createdAt)

    // ------------------------------------------------------------ JSON

    private class NewerBackupException : Exception()

    private fun toJson(d: BackupData): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("createdAt", d.createdAt)
        .put("notes", JSONArray(d.notes.map {
            JSONObject().put("id", it.id).put("title", it.title).put("content", it.content)
                .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                .put("isFavorite", it.isFavorite).put("isArchived", it.isArchived)
                .put("folderId", it.folderId ?: JSONObject.NULL)
        }))
        .put("ideas", JSONArray(d.ideas.map {
            JSONObject().put("id", it.id).put("title", it.title).put("content", it.content).put("status", it.status)
                .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
        }))
        .put("journal", JSONArray(d.journal.map {
            JSONObject().put("id", it.id).put("date", it.date).put("title", it.title).put("content", it.content)
                .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
        }))
        .put("tags", JSONArray(d.tags.map {
            JSONObject().put("id", it.id).put("name", it.name).put("normalizedName", it.normalizedName)
                .put("color", it.color ?: JSONObject.NULL)
        }))
        .put("noteTags", JSONArray(d.noteTags.map { JSONObject().put("a", it.noteId).put("t", it.tagId) }))
        .put("ideaTags", JSONArray(d.ideaTags.map { JSONObject().put("a", it.ideaId).put("t", it.tagId) }))
        .put("journalTags", JSONArray(d.journalTags.map { JSONObject().put("a", it.journalId).put("t", it.tagId) }))
        .put("folders", JSONArray(d.folders.map {
            JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt)
        }))
        .put("files", JSONArray(d.files.map {
            JSONObject().put("id", it.id).put("name", it.name).put("mimeType", it.mimeType)
                .put("sizeBytes", it.sizeBytes).put("folderId", it.folderId ?: JSONObject.NULL)
                .put("noteId", it.noteId ?: JSONObject.NULL).put("createdAt", it.createdAt)
                .put("extractedText", it.extractedText ?: JSONObject.NULL).put("extractionStatus", it.extractionStatus)
        }))

    private fun fromJson(o: JSONObject): BackupData? {
        if (o.optString("format") != FORMAT) return null
        if (o.optInt("version", 0) > VERSION) throw NewerBackupException()
        fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
        fun <T> JSONObject.list(key: String, map: (JSONObject) -> T): List<T> {
            val arr = optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }
        val notes = o.list("notes") {
            NoteEntity(it.getString("id"), it.getString("title"), it.getString("content"), it.getLong("createdAt"),
                it.getLong("updatedAt"), it.getBoolean("isFavorite"), it.getBoolean("isArchived"),
                folderId = if (it.has("folderId")) it.str("folderId") else null)
        }
        val ideas = o.list("ideas") {
            IdeaEntity(it.getString("id"), it.getString("title"), it.getString("content"), it.getString("status"),
                it.getLong("createdAt"), it.getLong("updatedAt"))
        }
        val journal = o.list("journal") {
            JournalEntryEntity(it.getString("id"), it.getString("date"), it.getString("title"), it.getString("content"),
                it.getLong("createdAt"), it.getLong("updatedAt"))
        }
        val tags = o.list("tags") { TagEntity(it.getString("id"), it.getString("name"), it.getString("normalizedName"), it.str("color")) }
        val noteIds = notes.map { it.id }.toSet()
        val ideaIds = ideas.map { it.id }.toSet()
        val journalIds = journal.map { it.id }.toSet()
        val tagIds = tags.map { it.id }.toSet()
        val folders = o.list("folders") { FolderEntity(it.getString("id"), it.getString("name"), it.getLong("createdAt")) }
        val folderIds = folders.map { it.id }.toSet()
        // Drop dangling references instead of failing the whole restore on foreign keys.
        return BackupData(
            notes = notes.map { n -> n.copy(folderId = n.folderId?.takeIf { f -> f in folderIds }) },
            ideas = ideas,
            journal = journal,
            tags = tags,
            noteTags = o.list("noteTags") { NoteTagCrossRef(it.getString("a"), it.getString("t")) }
                .filter { it.noteId in noteIds && it.tagId in tagIds },
            ideaTags = o.list("ideaTags") { IdeaTagCrossRef(it.getString("a"), it.getString("t")) }
                .filter { it.ideaId in ideaIds && it.tagId in tagIds },
            journalTags = o.list("journalTags") { JournalTagCrossRef(it.getString("a"), it.getString("t")) }
                .filter { it.journalId in journalIds && it.tagId in tagIds },
            folders = folders,
            files = o.list("files") {
                FileEntity(
                    id = it.getString("id"), name = it.getString("name"), mimeType = it.getString("mimeType"),
                    sizeBytes = it.getLong("sizeBytes"), localPath = "",
                    folderId = it.str("folderId")?.takeIf { f -> f in folderIds },
                    noteId = it.str("noteId")?.takeIf { n -> n in noteIds },
                    createdAt = it.getLong("createdAt"), extractedText = it.str("extractedText"),
                    extractionStatus = it.getString("extractionStatus"),
                )
            },
            createdAt = o.optLong("createdAt"),
        )
    }

    private companion object {
        const val FORMAT = "aidriy-backup"
        const val VERSION = 1
        const val MANIFEST = "backup.json"
        const val FILES_DIR = "files"
    }
}
