package com.aidriy.app.domain.model

import java.time.LocalDate

/**
 * One journal entry per calendar day. [date] is the user's local calendar date and is
 * stored separately from the UTC timestamps, so changing time zones never moves an entry.
 */
data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<Tag> = emptyList(),
)
