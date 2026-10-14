package com.aidriy.app.ui.ai

import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole

/** Plain Markdown text of a conversation for copying or sharing; failed attempts are left out. */
object ChatTranscript {
    fun build(title: String?, messages: List<ChatMessage>, userLabel: String, aiLabel: String): String = buildString {
        if (!title.isNullOrBlank()) append("# ").append(title.trim()).append("\n\n")
        messages.filterNot { it.isError || it.content.isBlank() }.forEachIndexed { i, m ->
            if (i > 0) append("\n\n")
            append("**").append(if (m.role == ChatRole.USER) userLabel else aiLabel).append(":**\n")
            append(m.content.trim())
        }
    }.trim()
}
