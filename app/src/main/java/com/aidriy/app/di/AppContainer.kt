package com.aidriy.app.di

import android.content.Context
import com.aidriy.app.core.time.SystemTimeProvider
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.data.LocalDataModule
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.domain.repository.IdeaRepository
import com.aidriy.app.domain.repository.JournalRepository
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.RecentItemsSource
import com.aidriy.app.domain.repository.SearchRepository
import com.aidriy.app.domain.repository.TagRepository

/**
 * Manual dependency container, created once per process by [com.aidriy.app.AidriyApplication].
 * Manual DI keeps the dependency list small; swap for Hilt if the module count grows.
 */
interface AppContainer {
    val timeProvider: TimeProvider
    val noteRepository: NoteRepository
    val ideaRepository: IdeaRepository
    val journalRepository: JournalRepository
    val tagRepository: TagRepository
    val searchRepository: SearchRepository
    val recentItemsSource: RecentItemsSource
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val timeProvider: TimeProvider = SystemTimeProvider

    /** Building is cheap; Room opens the file lazily on the first query, off the main thread. */
    val database: AidriyDatabase by lazy { AidriyDatabase.create(appContext) }

    private val data: LocalDataModule by lazy { LocalDataModule(database, timeProvider) }

    override val noteRepository: NoteRepository get() = data.noteRepository
    override val ideaRepository: IdeaRepository get() = data.ideaRepository
    override val journalRepository: JournalRepository get() = data.journalRepository
    override val tagRepository: TagRepository get() = data.tagRepository
    override val searchRepository: SearchRepository get() = data.searchRepository
    override val recentItemsSource: RecentItemsSource get() = data.recentItemsSource
}
