package com.aidriy.app.core.time

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * The picked day must survive conversion in any device time zone: a naive conversion
 * through the device zone turns "14 Oct" into "13 Oct" in UTC+7 or "15 Oct" in UTC-10.
 */
class DatePickerDatesTest {

    private lateinit var original: TimeZone

    @Before
    fun saveZone() {
        original = TimeZone.getDefault()
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(original)
    }

    @Test
    fun round_trip_keeps_the_same_calendar_day_in_every_zone() {
        val day = LocalDate.of(2026, 10, 14)
        listOf("Asia/Jakarta", "Asia/Makassar", "Pacific/Honolulu", "Pacific/Kiritimati", "UTC").forEach { zone ->
            TimeZone.setDefault(TimeZone.getTimeZone(zone))
            val millis = DatePickerDates.toPickerMillis(day)
            assertEquals("zone $zone", day, DatePickerDates.fromPickerMillis(millis))
        }
    }

    @Test
    fun picker_millis_are_utc_midnight() {
        assertEquals(0L, DatePickerDates.toPickerMillis(LocalDate.of(1970, 1, 1)))
    }

    @Test
    fun localDateOf_uses_the_given_zone() {
        // 2026-10-14 20:00 UTC is already 15 October in Jakarta (UTC+7).
        val millis = java.time.Instant.parse("2026-10-14T20:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 15), localDateOf(millis, ZoneId.of("Asia/Jakarta")))
        assertEquals(LocalDate.of(2026, 10, 14), localDateOf(millis, ZoneId.of("UTC")))
    }
}
