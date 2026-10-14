package com.aidriy.app.ui.common

import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.localDateOf
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** "14 Okt 2026" for a stored UTC timestamp, shown in the user's current local time zone. */
fun formatTimestamp(epochMillis: Long): String =
    IndonesianDateFormatter.formatShort(localDateOf(epochMillis))

/** "Hari ini · 07:12", "Kemarin · 21:30", "12 Okt · 18:20", or "12 Okt 2025" for older years. */
fun formatRelative(epochMillis: Long, now: ZonedDateTime = ZonedDateTime.now()): String {
    val zone: ZoneId = now.zone
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val time = String.format(Locale.ROOT, "%02d:%02d", at.hour, at.minute)
    val day = at.toLocalDate()
    val today = now.toLocalDate()
    return when {
        day == today -> "Hari ini · $time"
        day == today.minusDays(1) -> "Kemarin · $time"
        day.year == today.year -> {
            val short = IndonesianDateFormatter.formatShort(day)
            "${short.substringBeforeLast(' ')} · $time"
        }
        else -> IndonesianDateFormatter.formatShort(day)
    }
}

/** "1,2 MB", "830 KB". */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale("id"), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale("id"), "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

/** Debounce applied to search input before querying Room. Empty queries apply immediately. */
const val SEARCH_DEBOUNCE_MS = 250L
