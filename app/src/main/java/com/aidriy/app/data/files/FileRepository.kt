package com.aidriy.app.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.nowMillis
import com.aidriy.app.data.local.dao.FileDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.entity.FileEntity
import com.aidriy.app.data.local.entity.FolderEntity
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.ExtractionStatus
import com.aidriy.app.domain.model.FileKind
import com.aidriy.app.domain.model.Folder
import com.aidriy.app.domain.model.GroupStats
import com.aidriy.app.domain.model.StoredFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What the note editor needs from Berkas (an interface so the editor can be unit-tested). */
interface AttachmentStore {
    fun observeAttachments(noteId: String): Flow<List<StoredFile>>
    suspend fun import(uri: Uri, folderId: String? = null, noteId: String? = null): DataResult<StoredFile>
}

/**
 * Berkas: files are copied into app-private storage and indexed in Room.
 * Text extraction runs in [appScope] after import, so it continues if the screen closes.
 */
class FileRepository(
    private val context: Context,
    private val fileDao: FileDao,
    private val noteDao: NoteDao,
    private val extractor: TextExtractor,
    private val ocr: OcrService,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    private val appScope: CoroutineScope,
) : AttachmentStore {
    val storageDir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun observeFiles(query: String, kind: FileKind?, folderId: String?): Flow<List<StoredFile>> =
        fileDao.observeFiles(SearchQuery.toLikePattern(query), kind?.filterKey, folderId)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeFile(id: String): Flow<StoredFile?> =
        if (!InputRules.isValidId(id)) flowOf(null) else fileDao.observeById(id).map { it?.toDomain() }

    override fun observeAttachments(noteId: String): Flow<List<StoredFile>> =
        fileDao.observeForNote(noteId).map { rows -> rows.map { it.toDomain() } }

    fun observeStats(kind: FileKind?): Flow<GroupStats> =
        fileDao.observeStats(kind?.filterKey).map { GroupStats(it.count, it.latest) }

    fun observeNoteStats(archived: Boolean): Flow<GroupStats> =
        noteDao.observeStats(archived).map { GroupStats(it.count, it.latest) }

    fun observeFolders(): Flow<List<Folder>> = fileDao.observeFoldersWithStats().map { rows ->
        rows.map { Folder(it.id, it.name, it.createdAt, it.count, it.latest) }
    }

    fun observeReadableFiles(): Flow<List<StoredFile>> =
        fileDao.observeWithText().map { rows -> rows.map { it.toDomain() } }

    fun observeTotalSize(): Flow<Long> = fileDao.observeTotalSize()

    suspend fun getFile(id: String): StoredFile? = if (!InputRules.isValidId(id)) null else fileDao.getById(id)?.toDomain()

    /** Copies the document behind [uri] into Berkas. */
    override suspend fun import(uri: Uri, folderId: String?, noteId: String?): DataResult<StoredFile> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var name = "berkas"
                var declaredSize = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { idx ->
                            c.getString(idx)?.let { name = it }
                        }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { idx ->
                            if (!c.isNull(idx)) declaredSize = c.getLong(idx)
                        }
                    }
                }
                if (uri.scheme == "file") uri.lastPathSegment?.let { name = it }
                if (declaredSize > MAX_BYTES) return@withContext DataResult.Failure(DataError.FileTooLarge(MAX_BYTES))

                val mime = resolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"

                val id = ids.newId()
                val target = File(storageDir, id)
                val input = resolver.openInputStream(uri) ?: return@withContext DataResult.Failure(DataError.FileUnreadable)
                var copied = 0L
                input.use { inp ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buffer)
                            if (n < 0) break
                            copied += n
                            if (copied > MAX_BYTES) {
                                out.close()
                                target.delete()
                                return@withContext DataResult.Failure(DataError.FileTooLarge(MAX_BYTES))
                            }
                            out.write(buffer, 0, n)
                        }
                    }
                }
                val entity = FileEntity(
                    id = id,
                    name = name.take(200),
                    mimeType = mime,
                    sizeBytes = copied,
                    localPath = target.absolutePath,
                    folderId = folderId,
                    noteId = noteId,
                    createdAt = time.nowMillis(),
                    extractedText = null,
                    extractionStatus = ExtractionStatus.PENDING.name,
                )
                try {
                    fileDao.insert(entity)
                } catch (e: android.database.sqlite.SQLiteException) {
                    target.delete()
                    return@withContext DataResult.Failure(DataError.Storage(e))
                }
                appScope.launch { runExtraction(id) }
                DataResult.Success(entity.toDomain())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                DataResult.Failure(DataError.FileUnreadable)
            } catch (e: IOException) {
                Log.w(TAG, "Import failed: ${e.javaClass.simpleName}")
                DataResult.Failure(DataError.FileUnreadable)
            }
        }

    /** Extracts text and stores the outcome. Never throws: failures become a status. */
    suspend fun runExtraction(id: String) {
        val file = fileDao.getById(id) ?: return
        fileDao.setExtraction(id, null, ExtractionStatus.PENDING.name)
        val result = extractor.extract(File(file.localPath), file.mimeType, file.name)
        fileDao.setExtraction(id, result.text, result.status.name)
    }

    /** OCR for images and scanned PDFs. Returns the recognized text (possibly empty). */
    suspend fun runOcr(id: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): DataResult<String> {
        val file = fileDao.getById(id) ?: return DataResult.Failure(DataError.NotFound)
        return try {
            val text = when {
                file.mimeType.startsWith("image/") -> ocr.recognize(Uri.fromFile(File(file.localPath)))
                file.mimeType == "application/pdf" -> ocr.recognizePdf(File(file.localPath), onProgress = onProgress)
                else -> return DataResult.Failure(DataError.FileUnreadable)
            }.trim()
            fileDao.setExtraction(
                id,
                text.ifEmpty { null },
                if (text.isEmpty()) ExtractionStatus.NO_TEXT.name else ExtractionStatus.DONE.name,
            )
            DataResult.Success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "OCR failed: ${e.javaClass.simpleName}")
            DataResult.Failure(DataError.OcrFailed)
        }
    }

    suspend fun rename(id: String, name: String): DataResult<Unit> {
        val clean = name.trim()
        if (clean.isEmpty()) return DataResult.Failure(DataError.EmptyName)
        return if (fileDao.rename(id, clean.take(200)) == 0) DataResult.Failure(DataError.NotFound) else DataResult.Success(Unit)
    }

    suspend fun move(id: String, folderId: String?): DataResult<Unit> =
        if (fileDao.moveToFolder(id, folderId) == 0) DataResult.Failure(DataError.NotFound) else DataResult.Success(Unit)

    /** Removes the database row first, then the stored copy. */
    suspend fun delete(id: String): DataResult<Unit> {
        val file = fileDao.getById(id) ?: return DataResult.Failure(DataError.NotFound)
        if (fileDao.deleteById(id) == 0) return DataResult.Failure(DataError.NotFound)
        withContext(Dispatchers.IO) { File(file.localPath).delete() }
        return DataResult.Success(Unit)
    }

    suspend fun createFolder(name: String): DataResult<Folder> {
        val clean = name.trim()
        if (clean.isEmpty()) return DataResult.Failure(DataError.EmptyName)
        if (clean.length > 60) return DataResult.Failure(DataError.TitleTooLong(60))
        if (fileDao.getFolderByName(clean) != null) return DataResult.Failure(DataError.DuplicateName)
        val folder = FolderEntity(id = ids.newId(), name = clean, createdAt = time.nowMillis())
        fileDao.insertFolder(folder)
        return DataResult.Success(Folder(folder.id, folder.name, folder.createdAt, 0, null))
    }

    suspend fun renameFolder(id: String, name: String): DataResult<Unit> {
        val clean = name.trim()
        if (clean.isEmpty()) return DataResult.Failure(DataError.EmptyName)
        val existing = fileDao.getFolderByName(clean)
        if (existing != null && existing.id != id) return DataResult.Failure(DataError.DuplicateName)
        return if (fileDao.renameFolder(id, clean.take(60)) == 0) DataResult.Failure(DataError.NotFound) else DataResult.Success(Unit)
    }

    /** Files inside are kept and become unfiled (ON DELETE SET NULL). */
    suspend fun deleteFolder(id: String): DataResult<Unit> =
        if (fileDao.deleteFolder(id) == 0) DataResult.Failure(DataError.NotFound) else DataResult.Success(Unit)

    suspend fun getFolderName(id: String): String? = fileDao.getFolder(id)?.name

    suspend fun searchReadable(query: String, limit: Int): List<StoredFile> =
        fileDao.searchText(SearchQuery.toLikePattern(query), limit).map { it.toDomain() }

    companion object {
        const val DIR = "berkas"
        const val MAX_BYTES = 100L * 1024 * 1024
        private const val TAG = "FileRepository"
    }
}

internal fun FileEntity.toDomain() = StoredFile(
    id = id,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localPath = localPath,
    folderId = folderId,
    noteId = noteId,
    createdAt = createdAt,
    extractedText = extractedText,
    extractionStatus = ExtractionStatus.from(extractionStatus),
)
