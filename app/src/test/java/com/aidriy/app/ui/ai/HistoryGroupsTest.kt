package com.aidriy.app.ui.ai

import com.aidriy.app.domain.model.Conversation
import com.aidriy.app.domain.model.ResponseLength
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryGroupsTest {

    private val zone = ZoneId.of("Asia/Makassar")
    private val today = LocalDate.of(2026, 10, 14)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun groups_by_day_in_local_time() {
        val list = listOf(
            Conversation("a", "Belajar Kotlin", at(today, 7)),
            Conversation("b", "Materi Coking", at(today, 0)),
            Conversation("c", "Rangkuman PDF", at(today.minusDays(1), 23)),
            Conversation("d", "Analisis dokumen", at(today.minusDays(2), 10)),
            Conversation("e", "Ide konten", at(today.minusDays(6), 10)),
            Conversation("f", "Lama", at(today.minusDays(7), 10)),
        )
        val groups = groupConversations(list, today, zone)
        assertEquals(
            listOf(HistoryGroup.TODAY, HistoryGroup.YESTERDAY, HistoryGroup.THIS_WEEK, HistoryGroup.OLDER),
            groups.map { it.first },
        )
        assertEquals(listOf("a", "b"), groups[0].second.map { it.id })
        assertEquals(listOf("c"), groups[1].second.map { it.id })
        assertEquals(listOf("d", "e"), groups[2].second.map { it.id })
        assertEquals(listOf("f"), groups[3].second.map { it.id })
    }

    @Test
    fun empty_groups_are_left_out() {
        val groups = groupConversations(listOf(Conversation("a", "x", at(today, 9))), today, zone)
        assertEquals(listOf(HistoryGroup.TODAY), groups.map { it.first })
    }

    @Test
    fun response_length_defaults_to_medium() {
        assertEquals(ResponseLength.MEDIUM, ResponseLength.fromKey(null))
        assertEquals(ResponseLength.LONG, ResponseLength.fromKey("long"))
    }
}
