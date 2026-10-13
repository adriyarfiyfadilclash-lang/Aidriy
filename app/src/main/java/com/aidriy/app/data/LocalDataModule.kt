package com.aidriy.app.data

import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.UuidGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.local.RoomTransactionRunner
import com.aidriy.app.data.local.TransactionRunner
import com.aidriy.app.data.repository.DefaultIdeaRepository
import com.aidriy.app.data.repository.DefaultJournalRepository
import com.aidriy.app.data.repository.DefaultNoteRepository
import com.aidriy.app.data.repository.DefaultRecentItemsSource
import com.aidriy.app.data.repository.DefaultSearchRepository
import com.aidriy.app.data.repository.DefaultTagRepository
import com.aidriy.app.data.repository.TagWriter
import com.aidriy.app.domain.repository.IdeaRepository
import com.aidriy.app.domain.repository.JournalRepository
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.RecentItemsSource
import com.aidriy.app.domain.repository.SearchRepository
import com.aidriy.app.domain.repository.TagRepository

/**
 * Wires DAOs into repositories. Used by the app container and by instrumented tests
 * (with an in-memory database), so both exercise exactly the same code.
 */
class LocalDataModule(
    val database: AidriyDatabase,
    time: TimeProvider,
    ids: IdGenerator = UuidGenerator,
    transactions: TransactionRunner = RoomTransactionRunner(database),
) {
    private val tagWriter = TagWriter(database.tagDao(), ids)

    val noteRepository: NoteRepository = DefaultNoteRepository(
        noteDao = database.noteDao(),
        tagWriter = tagWriter,
        transactions = transactions,
        time = time,
        ids = ids,
    )

    val ideaRepository: IdeaRepository = DefaultIdeaRepository(
        ideaDao = database.ideaDao(),
        noteDao = database.noteDao(),
        tagDao = database.tagDao(),
        tagWriter = tagWriter,
        transactions = transactions,
        time = time,
        ids = ids,
    )

    val journalRepository: JournalRepository = DefaultJournalRepository(
        journalDao = database.journalDao(),
        tagWriter = tagWriter,
        transactions = transactions,
        time = time,
        ids = ids,
    )

    val tagRepository: TagRepository = DefaultTagRepository(database.tagDao())

    val searchRepository: SearchRepository = DefaultSearchRepository(
        noteDao = database.noteDao(),
        ideaDao = database.ideaDao(),
        journalDao = database.journalDao(),
    )

    val recentItemsSource: RecentItemsSource =
        DefaultRecentItemsSource(noteRepository, ideaRepository, journalRepository)
}
