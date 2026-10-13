package com.aidriy.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IndonesianDateFormatterTest {

    @Test
    fun formats_weekday_day_month_and_year_in_indonesian() {
        assertEquals(
            "Rabu, 14 Oktober 2026",
            IndonesianDateFormatter.formatLong(LocalDate.of(2026, 10, 14)),
        )
    }

    @Test
    fun day_of_month_has_no_leading_zero() {
        assertEquals(
            "Kamis, 1 Januari 2026",
            IndonesianDateFormatter.formatLong(LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun covers_sunday_and_december() {
        assertEquals(
            "Minggu, 27 Desember 2026",
            IndonesianDateFormatter.formatLong(LocalDate.of(2026, 12, 27)),
        )
    }

    @Test
    fun handles_leap_day() {
        assertEquals(
            "Selasa, 29 Februari 2028",
            IndonesianDateFormatter.formatLong(LocalDate.of(2028, 2, 29)),
        )
    }
}
