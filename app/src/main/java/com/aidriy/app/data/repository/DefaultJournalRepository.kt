package com.aidriy.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.nowMillis
import com.aidriy.app.core.time.today
import com.aidriy.app.data.local.TransactionRunner
import com.aidriy.app.data.local.dao.JournalDao
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.data.local.toDomain
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.InputRules
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.TagNames
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.domain.model.JournalFilter
import com.aidriy.app.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Journal dates are the user's local calendar date (LocalDate) stored as ISO text.
 * They are never derived from the UTC timestamps, so travelling across time zones
 * cannot move an existing entry to another day.
 */
class DefaultJournalRepository internal constructor(
    private val journalDao: JournalDao,
    private val tagWriter: TagWriter,
    private val transactions: TransactionRunner,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : JournalRepository {

    override fun observeEntries(filter: JournalFilter): Flow<List<JournalEntry>> =
        journalDao.observeFiltered(
            pattern = SearchQuery.toLikePattern(filter.query),
            tagId = filter.tagId,
        ).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<JournalEntry>> =
        journalDao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeByDate(date: LocalDate): Flow<JournalEntry?> =
        journalDao.observeByDate(date.toString()).map { it?.toDomain() }

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>> =
        journalDao.observeRange(start.toString(), end.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getByDate(date: LocalDate): JournalEntry? =
        journalDao.getByDate(date.toString())?.let { journalDao.getWithTags(it.id)?.toDomain() }

    override suspend fun save(
        date: LocalDate,
        draft: JournalDraft,
        expectedId: String?,
    ): DataResult<JournalEntry> {
        InputRules.validateJournal(date, time.today(), draft.title, draft.content)
            ?.let { return DataResult.Failure(it) }
        val tagNames = when (val cleaned = TagNames.cleanList(draft.tagNames)) {
            is DataResult.Success -> cleaned.value
            is DataResult.Failure -> return cleaned
        }
        val isoDate = date.toString()

        return storageCall(LOG_TAG) {
            try {
                transactions.inTransaction<DataResult<JournalEntry>> {
                    val existing = journalDao.getByDate(isoDate)
                    val now = time.nowMillis()
                    val id = if (existing == null) {
                        // Editor had an entry, but it was deleted meanwhile.
                        if (expectedId != null) {
                            return@inTransaction DataResult.Failure(DataError.NotFound)
                        }
                        val created = JournalEntryEntity(
                            id = ids.newId(),
                            date = isoDate,
                            title = draft.title.trim(),
                            content = draft.content,
                            createdAt = now,
                            updatedAt = now,
                        )
                        journalDao.insert(created)
                        created.id
                    } else if (existing.id == expectedId) {
                        journalDao.update(
                            existing.copy(
                                title = draft.title.trim(),
                                content = draft.content,
                                updatedAt = maxOf(now, existing.updatedAt),
                            ),
                        )
                        existing.id
                    } else {
                        // An entry for this date exists that the editor didn't load: never overwrite it.
                        return@inTransaction DataResult.Failure(DataError.DuplicateJournalDate(date, existing.id))
                    }
                    tagWriter.replaceJournalTags(id, tagNames)
                    val saved = checkNotNull(journalDao.getWithTags(id)) { "Journal entry missing right after write" }
                    DataResult.Success(saved.toDomain())
                }
            } catch (e: SQLiteConstraintException) {
                // Lost a race with another insert for the same date (unique index on date).
                val winner = journalDao.getByDate(isoDate) ?: throw e
                DataResult.Failure(DataError.DuplicateJournalDate(date, winner.id))
            }
        }
    }

    override suspend fun delete(id: String): DataResult<Unit> {
        if (!InputRules.isValidId(id)) return DataResult.Failure(DataError.NotFound)
        return storageCall(LOG_TAG) {
            transactions.inTransaction<DataResult<Unit>> {
                if (journalDao.deleteById(id) == 0) {
                    return@inTransaction DataResult.Failure(DataError.NotFound)
                }
                tagWriter.pruneUnused()
                DataResult.Success(Unit)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "JournalRepository"
    }
}
