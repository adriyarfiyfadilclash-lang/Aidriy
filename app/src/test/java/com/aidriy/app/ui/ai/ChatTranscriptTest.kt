package com.aidriy.app.ui.ai

import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTranscriptTest {
    private fun msg(role: ChatRole, text: String, error: Boolean = false) =
        ChatMessage("id$text", "c", role, text, null, null, emptyList(), false, error, 0)

    @Test
    fun builds_markdown_and_skips_errors() {
        val out = ChatTranscript.build(
            "Belajar",
            listOf(msg(ChatRole.USER, "Apa itu X?"), msg(ChatRole.ASSISTANT, "gagal", error = true), msg(ChatRole.ASSISTANT, " X adalah Y. ")),
            "Kamu", "Ai",
        )
        assertEquals("# Belajar\n\n**Kamu:**\nApa itu X?\n\n**Ai:**\nX adalah Y.", out)
    }

    @Test
    fun no_title_no_header() {
        assertEquals("**Ai:**\nHalo", ChatTranscript.build(null, listOf(msg(ChatRole.ASSISTANT, "Halo")), "Kamu", "Ai"))
    }
}
