package com.aidriy.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aidriy.app.data.local.entity.JournalEntryEntity
import com.aidriy.app.domain.DataError
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.JournalDraft
import com.aidriy.app.domain.model.JournalEntry
import com.aidriy.app.domain.model.JournalFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class JournalRepositoryTest : DatabaseTestBase() {

    private val journal get() = data.journalRepository
    private val today = LocalDate.of(2026, 10, 14)

    private suspend fun save(date: LocalDate, content: String, expectedId: String? = null): DataResult<JournalEntry> =
        journal.save(date, JournalDraft(title = "", content = content), expectedId)

    @Test
    fun creates_and_reads_entry_by_local_date() = runTest {
        val entry = (save(today, "Hari yang baik") as DataResult.Success).value
        assertEquals(today, entry.date)
        assertEquals(entry.id, journal.getByDate(today)!!.id)
        assertEquals("2026-10-14", db.journalDao().getById(entry.id)!!.date)
    }

    @Test
    fun second_entry_for_same_date_is_reported_as_duplicate_not_overwritten() = runTest {
        val first = (save(today, "pertama") as DataResult.Success).value

        val result = save(today, "kedua", expectedId = null)

        assertEquals(DataResult.Failure(DataError.DuplicateJournalDate(today, first.id)), result)
        assertEquals("pertama", journal.getByDate(today)!!.content)
    }

    @Test
    fun editing_existing_entry_with_its_id_updates_it() = runTest {
        val first = (save(today, "pertama") as DataResult.Success).value
        val updated = (save(today, "diedit", expectedId = first.id) as DataResult.Success).value
        assertEquals(first.id, updated.id)
        assertEquals("diedit", journal.getByDate(today)!!.content)
    }

    @Test
    fun database_enforces_one_entry_per_date() = runTest {
        save(today, "ada")
        try {
            db.journalDao().insert(
                JournalEntryEntity(id = "other", date = "2026-10-14", title = "", content = "x", createdAt = 0, updatedAt = 0),
            )
            fail("Unique index on date should reject a second row")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }

    @Test
    fun future_dates_are_rejected_using_the_local_calendar() = runTest {
        assertEquals(DataResult.Failure(DataError.FutureJournalDate), save(today.plusDays(1), "besok"))
        assertTrue(save(today, "hari ini") is DataResult.Success)
    }

    @Test
    fun date_does_not_shift_when_the_time_zone_changes() = runTest {
        // Written at 23:00 on the 14th in Jakarta (UTC+7). The entry is stored under the local date 2026-10-14.
        clock.current = clock.current.withHour(23)
        val entry = (save(today, "malam") as DataResult.Success).value

        // User flies to Honolulu (UTC-10): the stored entry must still belong to the 14th.
        clock.current = clock.current.withZoneSameInstant(ZoneId.of("Pacific/Honolulu"))
        assertEquals(today, journal.getByDate(today)!!.date)
        assertEquals(entry.id, journal.getByDate(today)!!.id)
    }

    @Test
    fun history_is_newest_first_and_range_is_chronological() = runTest {
        save(today.minusDays(2), "dua hari lalu")
        save(today, "hari ini")
        save(today.minusDays(1), "kemarin")

        assertEquals(
            listOf(today, today.minusDays(1), today.minusDays(2)),
            journal.observeEntries(JournalFilter()).first().map { it.date },
        )
        assertEquals(
            listOf(today.minusDays(2), today.minusDays(1)),
            journal.observeRange(today.minusDays(2), today.minusDays(1)).first().map { it.date },
        )
    }

    @Test
    fun search_and_delete() = runTest {
        val entry = (save(today, "Belajar Kotlin") as DataResult.Success).value
        save(today.minusDays(1), "Jalan sore")

        assertEquals(listOf(today), journal.observeEntries(JournalFilter(query = "kotlin")).first().map { it.date })

        assertEquals(DataResult.Success(Unit), journal.delete(entry.id))
        assertEquals(null, journal.getByDate(today))
    }

    @Test
    fun saving_an_entry_that_was_deleted_meanwhile_reports_not_found() = runTest {
        val entry = (save(today, "isi") as DataResult.Success).value
        journal.delete(entry.id)
        assertEquals(DataResult.Failure(DataError.NotFound), save(today, "isi baru", expectedId = entry.id))
    }
}
