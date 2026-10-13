package com.aidriy.app.data.repository

import com.aidriy.app.data.local.dao.IdeaDao
import com.aidriy.app.data.local.dao.JournalDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.dao.TagDao
import com.aidriy.app.data.local.toDomain
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.RecentItems
import com.aidriy.app.domain.model.SearchResults
import com.aidriy.app.domain.model.Tag
import com.aidriy.app.domain.model.TaggableKind
import com.aidriy.app.domain.repository.IdeaRepository
import com.aidriy.app.domain.repository.JournalRepository
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.RecentItemsSource
import com.aidriy.app.domain.repository.SearchRepository
import com.aidriy.app.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DefaultTagRepository internal constructor(
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        tagDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUsedBy(kind: TaggableKind): Flow<List<Tag>> {
        val source = when (kind) {
            TaggableKind.NOTE -> tagDao.observeUsedByNotes()
            TaggableKind.IDEA -> tagDao.observeUsedByIdeas()
            TaggableKind.JOURNAL -> tagDao.observeUsedByJournal()
        }
        return source.map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun deleteUnused(): DataResult<Int> =
        storageCall("TagRepository") { DataResult.Success(tagDao.deleteUnused()) }
}

class DefaultSearchRepository internal constructor(
    private val noteDao: NoteDao,
    private val ideaDao: IdeaDao,
    private val journalDao: JournalDao,
) : SearchRepository {

    override fun search(query: String, limitPerType: Int): Flow<SearchResults> {
        if (SearchQuery.normalize(query).isEmpty()) return flowOf(SearchResults.EMPTY)
        val pattern = SearchQuery.toLikePattern(query)
        return combine(
            noteDao.search(pattern, limitPerType),
            ideaDao.search(pattern, limitPerType),
            journalDao.search(pattern, limitPerType),
        ) { notes, ideas, journal ->
            SearchResults(
                notes = notes.map { it.toDomain() },
                ideas = ideas.map { it.toDomain() },
                journalEntries = journal.map { it.toDomain() },
            )
        }
    }
}

class DefaultRecentItemsSource(
    private val notes: NoteRepository,
    private val ideas: IdeaRepository,
    private val journal: JournalRepository,
    private val notesLimit: Int = 3,
    private val ideasLimit: Int = 3,
    private val journalLimit: Int = 2,
) : RecentItemsSource {
    override fun observe(): Flow<RecentItems> = combine(
        notes.observeRecent(notesLimit),
        ideas.observeRecent(ideasLimit),
        journal.observeRecent(journalLimit),
    ) { n, i, j -> RecentItems(notes = n, ideas = i, journalEntries = j) }
}
