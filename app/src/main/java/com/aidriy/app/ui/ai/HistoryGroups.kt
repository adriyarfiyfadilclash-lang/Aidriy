package com.aidriy.app.ui.ai

import com.aidriy.app.domain.model.Conversation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sections of the chat history sidebar. */
enum class HistoryGroup { TODAY, YESTERDAY, THIS_WEEK, OLDER }

/** Groups conversations (already newest first) by their last update, in the device's time zone. */
fun groupConversations(
    conversations: List<Conversation>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<HistoryGroup, List<Conversation>>> {
    val groups = conversations.groupBy { c ->
        val day = Instant.ofEpochMilli(c.updatedAt).atZone(zone).toLocalDate()
        when {
            !day.isBefore(today) -> HistoryGroup.TODAY
            day == today.minusDays(1) -> HistoryGroup.YESTERDAY
            day.isAfter(today.minusDays(7)) -> HistoryGroup.THIS_WEEK
            else -> HistoryGroup.OLDER
        }
    }
    return HistoryGroup.entries.mapNotNull { g -> groups[g]?.let { g to it } }
}
