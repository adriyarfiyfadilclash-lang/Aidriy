package com.aidriy.app.data.repository

import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.nowMillis
import com.aidriy.app.data.local.TransactionRunner
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.toDomain
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.NoteDraft
import com.aidriy.app.domain.model.NoteFilter
import com.aidriy.app.domain.model.NoteScope
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DefaultNoteRepository internal constructor(
    private val noteDao: NoteDao,
    private val tagWriter: TagWriter,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : NoteRepository {

    override fun observeNotes(filter: NoteFilter): Flow<List<Note>> =
        noteDao.observeFiltered(
            archived = filter.scope == NoteScope.ARCHIVED,
            favoritesOnly = filter.scope == NoteScope.FAVORITES,
            pattern = SearchQuery.toLikePattern(filter.query),
            tagId = filter.tagId,
            sortByCreated = filter.sort == SortOrder.CREATED,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Note>> =
        noteDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeNote(id: String): Flow<Note?> =
        if (!InputRules.isValidId(id)) flowOf(null)
        else noteDao.observeWithTags(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Note? =
        if (!InputRules.isValidId(id)) null else noteDao.getWithTags(id)?.toDomain()

    override suspend fun create(draft: NoteDraft, isFavorite: Boolean): DataResult<Note> {
        InputRules.validateNote(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val tagNames = when (val cleaned = TagNames.cleanList(draft.tagNames)) {
            is DataResult.Success -> cleaned.value
            is DataResult.Failure -> return cleaned
        }
        return storageCall(LOG_TAG) {
            val now = time.nowMillis()
            val entity = NoteEntity(
                id = ids.newId(),
                title = draft.title.trim(),
                content = draft.content,
                createdAt = now,
                updatedAt = now,
                isFavorite = isFavorite,
                isArchived = false,
            )
            transactions.inTransaction {
                noteDao.insert(entity)
                tagWriter.replaceNoteTags(entity.id, tagNames)
                DataResult.Success(loadNote(entity.id))
            }
        }
    }

    override suspend fun update(id: String, draft: NoteDraft): DataResult<Note> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        InputRules.validateNote(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val tagNames = when (val cleaned = TagNames.cleanList(draft.tagNames)) {
            is DataResult.Success -> cleaned.value
            is DataResult.Failure -> return cleaned
        }
        return storageCall(LOG_TAG) {
            transactions.inTransaction<DataResult<Note>> {
                val existing = noteDao.getById(id)
                    ?: return@inTransaction DataResult.Failure(DataError.NotFound)
                val updated = existing.copy(
                    title = draft.title.trim(),
                    content = draft.content,
                    // Never move backwards if the device clock was adjusted.
                    updatedAt = maxOf(time.nowMillis(), existing.updatedAt),
                )
                noteDao.update(updated)
                tagWriter.replaceNoteTags(id, tagNames)
                DataResult.Success(loadNote(id))
            }
        }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            if (noteDao.setFavorite(id, favorite) == 0) DataResult.Failure(DataError.NotFound)
            else DataResult.Success(Unit)
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            if (noteDao.setArchived(id, archived) == 0) DataResult.Failure(DataError.NotFound)
            else DataResult.Success(Unit)
        }
    }

    override suspend fun delete(id: String): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            transactions.inTransaction<DataResult<Unit>> {
                if (noteDao.deleteById(id) == 0) {
                    return@inTransaction DataResult.Failure(DataError.NotFound)
                }
                // Tag links were removed by ON DELETE CASCADE; drop tags nothing uses now.
                tagWriter.pruneUnused()
                DataResult.Success(Unit)
            }
        }
    }

    private suspend fun loadNote(id: String): Note =
        checkNotNull(noteDao.getWithTags(id)) { "Note missing right after write" }.toDomain()

    private companion object {
        const val LOG_TAG = "NoteRepository"
    }
}
