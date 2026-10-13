package com.aidriy.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Material 3's DatePicker represents a calendar day as UTC midnight in epoch millis.
 * Converting through UTC (not the device zone) keeps the picked day exact in every time zone,
 * e.g. picking 14 Oct in Jakarta (UTC+7) must not become 13 Oct.
 */
object DatePickerDates {
    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}

/** Local calendar date of a stored UTC timestamp, for display only. */
fun localDateOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
