package com.aidriy.app.di

import android.content.Context
import com.aidriy.app.core.UuidGenerator
import com.aidriy.app.core.time.SystemTimeProvider
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.data.LocalDataModule
import com.aidriy.app.data.ai.AiChatRepository
import com.aidriy.app.data.backup.BackupManager
import com.aidriy.app.data.files.FileRepository
import com.aidriy.app.data.files.OcrService
import com.aidriy.app.data.files.TextExtractor
import com.aidriy.app.data.local.AidriyDatabase
import com.aidriy.app.data.reminders.ReminderScheduler
import com.aidriy.app.data.security.AppLockManager
import com.aidriy.app.data.settings.SecretStore
import com.aidriy.app.data.settings.SettingsRepository
import com.aidriy.app.domain.repository.IdeaRepository
import com.aidriy.app.domain.repository.JournalRepository
import com.aidriy.app.domain.repository.NoteRepository
import com.aidriy.app.domain.repository.RecentItemsSource
import com.aidriy.app.domain.repository.SearchRepository
import com.aidriy.app.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val fileRepository: FileRepository
    val aiChatRepository: AiChatRepository
    val settingsRepository: SettingsRepository
    val secretStore: SecretStore
    val reminderScheduler: ReminderScheduler
    val backupManager: BackupManager
    val ocrService: OcrService
    val appLock: AppLockManager
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    /** Work that must finish even if the screen that started it closes (e.g. text extraction). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    override val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    override val secretStore: SecretStore by lazy { SecretStore(appContext) }
    override val ocrService: OcrService by lazy { OcrService(appContext) }

    override val fileRepository: FileRepository by lazy {
        FileRepository(
            context = appContext,
            fileDao = database.fileDao(),
            noteDao = database.noteDao(),
            extractor = TextExtractor(appContext),
            ocr = ocrService,
            time = timeProvider,
            ids = UuidGenerator,
            appScope = appScope,
        )
    }

    override val aiChatRepository: AiChatRepository by lazy {
        AiChatRepository(
            aiDao = database.aiDao(),
            noteDao = database.noteDao(),
            ideaDao = database.ideaDao(),
            journalDao = database.journalDao(),
            files = fileRepository,
            secrets = secretStore,
            time = timeProvider,
            ids = UuidGenerator,
        )
    }

    override val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }
    override val backupManager: BackupManager by lazy { BackupManager(appContext, database, fileRepository) }
    override val appLock: AppLockManager by lazy { AppLockManager(settingsRepository, appScope) }
}
