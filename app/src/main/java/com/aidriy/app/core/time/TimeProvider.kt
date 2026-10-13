package com.aidriy.app.core.time

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Source of "now" for business logic. Injected so date-dependent logic
 * (greetings, today's journal date) is deterministic in tests.
 */
fun interface TimeProvider {
    fun now(): ZonedDateTime
}

/** Uses the device clock and the user's current local time zone. */
object SystemTimeProvider : TimeProvider {
    override fun now(): ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
}
