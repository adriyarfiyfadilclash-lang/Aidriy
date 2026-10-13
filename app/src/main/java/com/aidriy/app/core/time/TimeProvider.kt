package com.aidriy.app.core.time

import java.time.LocalDate
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

/** Record timestamps are UTC epoch milliseconds. */
fun TimeProvider.nowMillis(): Long = now().toInstant().toEpochMilli()

/** The user's local calendar date. Used for journals; never derived from UTC. */
fun TimeProvider.today(): LocalDate = now().toLocalDate()
