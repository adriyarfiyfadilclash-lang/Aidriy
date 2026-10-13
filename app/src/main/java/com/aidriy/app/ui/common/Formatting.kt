package com.aidriy.app.ui.common

import com.aidriy.app.core.time.IndonesianDateFormatter
import com.aidriy.app.core.time.localDateOf

/** "14 Okt 2026" for a stored UTC timestamp, shown in the user's current local time zone. */
fun formatTimestamp(epochMillis: Long): String =
    IndonesianDateFormatter.formatShort(localDateOf(epochMillis))

/** Debounce applied to search input before querying Room. Empty queries apply immediately. */
const val SEARCH_DEBOUNCE_MS = 250L
