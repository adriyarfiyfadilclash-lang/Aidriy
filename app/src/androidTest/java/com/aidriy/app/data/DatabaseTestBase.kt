package com.aidriy.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.data.local.AidriyDatabase
import org.junit.After
import org.junit.Before
import java.time.ZoneId
import java.time.ZonedDateTime

/** Movable clock so tests can verify timestamps and journal "today" rules. */
class TestClock(var current: ZonedDateTime) : TimeProvider {
    override fun now(): ZonedDateTime = current
    fun advanceMinutes(minutes: Long) {
        current = current.plusMinutes(minutes)
    }
}

/** Real SQLite (in memory) with the same repositories the app uses. */
abstract class DatabaseTestBase {
    protected lateinit var db: AidriyDatabase
    protected lateinit var data: LocalDataModule
    protected val clock = TestClock(ZonedDateTime.of(2026, 10, 14, 9, 0, 0, 0, ZoneId.of("Asia/Jakarta")))

    @Before
    fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AidriyDatabase::class.java,
        ).build()
        data = LocalDataModule(db, clock)
    }

    @After
    fun closeDatabase() {
        db.close()
    }
}
