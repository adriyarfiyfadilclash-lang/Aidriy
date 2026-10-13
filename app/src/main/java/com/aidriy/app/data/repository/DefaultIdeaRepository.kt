package com.aidriy.app.data.repository

import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.nowMillis
import com.aidriy.app.data.local.TransactionRunner
import com.aidriy.app.data.local.dao.IdeaDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.dao.TagDao
import com.aidriy.app.data.local.entity.IdeaEntity
import com.aidriy.app.data.local.entity.NoteEntity
import com.aidriy.app.data.local.entity.NoteTagCrossRef
import com.aidriy.app.data.local.toDomain
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.model.Idea
import com.aidriy.app.domain.model.IdeaDraft
import com.aidriy.app.domain.model.IdeaFilter
import com.aidriy.app.domain.model.IdeaStatus
import com.aidriy.app.domain.model.Note
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.domain.repository.IdeaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DefaultIdeaRepository internal constructor(
    private val ideaDao: IdeaDao,
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val tagWriter: TagWriter,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : IdeaRepository {

    override fun observeIdeas(filter: IdeaFilter): Flow<List<Idea>> =
        ideaDao.observeFiltered(
            status = filter.status?.storageKey,
            pattern = SearchQuery.toLikePattern(filter.query),
            tagId = filter.tagId,
            sortByCreated = filter.sort == SortOrder.CREATED,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Idea>> =
        ideaDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeIdea(id: String): Flow<Idea?> =
        if (!InputRules.isValidId(id)) flowOf(null)
        else ideaDao.observeWithTags(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Idea? =
        if (!InputRules.isValidId(id)) null else ideaDao.getWithTags(id)?.toDomain()

    override suspend fun quickCapture(title: String): DataResult<Idea> =
        create(IdeaDraft(title = title))

    override suspend fun create(draft: IdeaDraft): DataResult<Idea> {
        InputRules.validateIdea(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val tagNames = when (val cleaned = TagNames.cleanList(draft.tagNames)) {
            is DataResult.Success -> cleaned.value
            is DataResult.Failure -> return cleaned
        }
        return storageCall(LOG_TAG) {
            val now = time.nowMillis()
            val entity = IdeaEntity(
                id = ids.newId(),
                title = draft.title.trim(),
                content = draft.content,
                status = draft.status.storageKey,
                createdAt = now,
                updatedAt = now,
            )
            transactions.inTransaction {
                ideaDao.insert(entity)
                tagWriter.replaceIdeaTags(entity.id, tagNames)
                DataResult.Success(loadIdea(entity.id))
            }
        }
    }

    override suspend fun update(id: String, draft: IdeaDraft): DataResult<Idea> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        InputRules.validateIdea(draft.title, draft.content)?.let { return DataResult.Failure(it) }
        val tagNames = when (val cleaned = TagNames.cleanList(draft.tagNames)) {
            is DataResult.Success -> cleaned.value
            is DataResult.Failure -> return cleaned
        }
        return storageCall(LOG_TAG) {
            transactions.inTransaction<DataResult<Idea>> {
                val existing = ideaDao.getById(id)
                    ?: return@inTransaction DataResult.Failure(DataError.NotFound)
                ideaDao.update(
                    existing.copy(
                        title = draft.title.trim(),
                        content = draft.content,
                        status = draft.status.storageKey,
                        updatedAt = maxOf(time.nowMillis(), existing.updatedAt),
                    ),
                )
                tagWriter.replaceIdeaTags(id, tagNames)
                DataResult.Success(loadIdea(id))
            }
        }
    }

    override suspend fun setStatus(id: String, status: IdeaStatus): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            if (ideaDao.setStatus(id, status.storageKey, time.nowMillis()) == 0) {
                DataResult.Failure(DataError.NotFound)
            } else {
                DataResult.Success(Unit)
            }
        }
    }

    override suspend fun delete(id: String): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            transactions.inTransaction<DataResult<Unit>> {
                if (ideaDao.deleteById(id) == 0) {
                    return@inTransaction DataResult.Failure(DataError.NotFound)
                }
                tagWriter.pruneUnused()
                DataResult.Success(Unit)
            }
        }
    }

    override suspend fun convertToNote(id: String): DataResult<Note> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            // Single transaction: the note and its tag links are created together or not at all.
            transactions.inTransaction<DataResult<Note>> {
                val source = ideaDao.getWithTags(id)
                    ?: return@inTransaction DataResult.Failure(DataError.NotFound)
                val now = time.nowMillis()
                val note = NoteEntity(
                    id = ids.newId(),
                    title = source.idea.title,
                    content = source.idea.content,
                    createdAt = now,
                    updatedAt = now,
                    isFavorite = false,
                    isArchived = false,
                )
                noteDao.insert(note)
                tagDao.addToNote(source.tags.map { NoteTagCrossRef(noteId = note.id, tagId = it.id) })
                val created = checkNotNull(noteDao.getWithTags(note.id)) { "Note missing right after write" }
                DataResult.Success(created.toDomain())
            }
        }
    }

    private suspend fun loadIdea(id: String): Idea =
        checkNotNull(ideaDao.getWithTags(id)) { "Idea missing right after write" }.toDomain()

    private companion object {
        const val LOG_TAG = "IdeaRepository"
    }
}
