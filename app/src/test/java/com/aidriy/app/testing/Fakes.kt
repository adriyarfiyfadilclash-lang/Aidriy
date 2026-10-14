package com.aidriy.app.testing

import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.NoteFilter
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.model.TaggableKind
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime

class FakeTimeProvider(var current: ZonedDateTime) : TimeProvider {
    override fun now(): ZonedDateTime = current
}

/**
 * In-memory NoteRepository for ViewModel tests. Applies the same validation as the real one.
 * Set [failWritesWith] to simulate a database failure.
 */
class FakeNoteRepository : NoteRepository {
    private val notes = MutableStateFlow<Map<String, Note>>(emptyMap())
    private var nextId = 1
    private var clock = 1_000L

    var failWritesWith: DataError? = null
    var writeCount = 0
        private set

    fun stored(id: String): Note? = notes.value[id]

    override fun observeNotes(filter: NoteFilter): Flow<List<Note>> =
        notes.map { all -> all.values.filter { filter.folderId == null || it.folderId == filter.folderId } }
    override fun observeRecent(limit: Int): Flow<List<Note>> = notes.map { it.values.take(limit) }
    override fun observeNote(id: String): Flow<Note?> = notes.map { it[id] }
    override suspend fun getById(id: String): Note? = notes.value[id]

    override suspend fun create(draft: NoteDraft, isFavorite: Boolean): DataResult<Note> {
        failWritesWith?.let { return DataResult.Failure(it) }
        InputRules.validateNote(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val now = ++clock
        val note = Note(
            id = "note-${nextId++}",
            title = draft.title.trim(),
            content = draft.content,
            createdAt = now,
            updatedAt = now,
            isFavorite = isFavorite,
            tags = draft.tagNames.map { Tag(id = it.lowercase(), name = it) },
        )
        notes.value = notes.value + (note.id to note)
        writeCount++
        return DataResult.Success(note)
    }

    override suspend fun update(id: String, draft: NoteDraft): DataResult<Note> {
        failWritesWith?.let { return DataResult.Failure(it) }
        InputRules.validateNote(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val existing = notes.value[id] ?: return DataResult.Failure(DataError.NotFound)
        val updated = existing.copy(
            title = draft.title.trim(),
            content = draft.content,
            updatedAt = ++clock,
            tags = draft.tagNames.map { Tag(id = it.lowercase(), name = it) },
        )
        notes.value = notes.value + (id to updated)
        writeCount++
        return DataResult.Success(updated)
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): DataResult<Unit> {
        failWritesWith?.let { return DataResult.Failure(it) }
        val existing = notes.value[id] ?: return DataResult.Failure(DataError.NotFound)
        notes.value = notes.value + (id to existing.copy(isFavorite = favorite))
        return DataResult.Success(Unit)
    }

    override suspend fun setArchived(id: String, archived: Boolean): DataResult<Unit> {
        failWritesWith?.let { return DataResult.Failure(it) }
        val existing = notes.value[id] ?: return DataResult.Failure(DataError.NotFound)
        notes.value = notes.value + (id to existing.copy(isArchived = archived))
        return DataResult.Success(Unit)
    }

    override suspend fun setFolder(id: String, folderId: String?): DataResult<Unit> {
        val existing = notes.value[id] ?: return DataResult.Failure(DataError.NotFound)
        notes.value = notes.value + (id to existing.copy(folderId = folderId))
        return DataResult.Success(Unit)
    }

    override suspend fun setReminder(id: String, atMillis: Long?): DataResult<Unit> {
        val existing = notes.value[id] ?: return DataResult.Failure(DataError.NotFound)
        notes.value = notes.value + (id to existing.copy(reminderAt = atMillis))
        return DataResult.Success(Unit)
    }

    override suspend fun clearReminder(id: String) {
        setReminder(id, null)
    }

    override suspend fun delete(id: String): DataResult<Unit> {
        failWritesWith?.let { return DataResult.Failure(it) }
        if (id !in notes.value) return DataResult.Failure(DataError.NotFound)
        notes.value = notes.value - id
        return DataResult.Success(Unit)
    }
}

class FakeTagRepository : TagRepository {
    override fun observeAll(): Flow<List<Tag>> = flowOf(emptyList())
    override fun observeUsedBy(kind: TaggableKind): Flow<List<Tag>> = flowOf(emptyList())
    override suspend fun deleteUnused(): DataResult<Int> = DataResult.Success(0)
}
