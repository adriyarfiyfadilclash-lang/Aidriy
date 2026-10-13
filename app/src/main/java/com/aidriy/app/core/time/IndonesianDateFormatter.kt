package com.aidriy.app.core.time

import java.time.LocalDate

/**
 * Formats dates in Bahasa Indonesia without depending on device locale data,
 * so output is identical on every device and in JVM unit tests.
 */
object IndonesianDateFormatter {

    private val dayNames = listOf(
        "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu",
    )

    private val monthNames = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    private val shortMonthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
    )

    /** Example: "Rabu, 14 Oktober 2026". */
    fun formatLong(date: LocalDate): String {
        val day = dayNames[date.dayOfWeek.value - 1]
        val month = monthNames[date.monthValue - 1]
        return "$day, ${date.dayOfMonth} $month ${date.year}"
    }

    /** Example: "14 Okt 2026". */
    fun formatShort(date: LocalDate): String =
        "${date.dayOfMonth} ${shortMonthNames[date.monthValue - 1]} ${date.year}"
}
