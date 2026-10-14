package com.aidriy.app.domain.model

/**
 * Supported AI providers. Model IDs are only defaults: users can change them in Preferensi AI,
 * because providers rename and retire models over time.
 */
enum class AiProviderId(val key: String, val displayName: String, val company: String, val defaultModel: String) {
    OPENAI("openai", "OpenAI", "OpenAI", "gpt-4o"),
    ANTHROPIC("anthropic", "Claude", "Anthropic", "claude-sonnet-5-5"),
    GEMINI("gemini", "Gemini", "Google", "gemini-2.5-flash");

    companion object {
        fun fromKey(key: String?): AiProviderId = entries.firstOrNull { it.key == key } ?: OPENAI
    }
}

enum class AnswerStyle(val key: String) {
    BALANCED("balanced"), CONCISE("concise"), DETAILED("detailed");

    companion object {
        fun fromKey(key: String?): AnswerStyle = entries.firstOrNull { it.key == key } ?: BALANCED
    }
}

enum class AiLanguage(val key: String) {
    INDONESIAN("id"), ENGLISH("en");

    companion object {
        fun fromKey(key: String?): AiLanguage = entries.firstOrNull { it.key == key } ?: INDONESIAN
    }
}

enum class ChatRole { USER, ASSISTANT }

/** A local record that was (or will be) sent to the AI as context. */
data class SourceRef(val type: SourceType, val id: String, val title: String) {
    fun encode(): String = "${type.key}:$id|${title.replace('\n', ' ').replace('|', ' ')}"

    companion object {
        fun decode(raw: String): SourceRef? {
            val keyAndRest = raw.split(':', limit = 2)
            if (keyAndRest.size != 2) return null
            val type = SourceType.entries.firstOrNull { it.key == keyAndRest[0] } ?: return null
            val idAndTitle = keyAndRest[1].split('|', limit = 2)
            return SourceRef(type, idAndTitle[0], idAndTitle.getOrElse(1) { "" })
        }

        fun encodeAll(sources: List<SourceRef>): String = sources.joinToString("\n") { it.encode() }
        fun decodeAll(raw: String): List<SourceRef> = raw.lines().filter { it.isNotBlank() }.mapNotNull(::decode)
    }
}

enum class SourceType(val key: String) { NOTE("note"), IDEA("idea"), JOURNAL("journal"), FILE("file") }

/** Retrieved local content with the text that would be shared with the provider. */
data class ContextItem(val ref: SourceRef, val text: String)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: ChatRole,
    val content: String,
    val provider: AiProviderId?,
    val model: String?,
    val sources: List<SourceRef>,
    val noLocalMatch: Boolean,
    val isError: Boolean,
    val createdAt: Long,
)

data class Conversation(val id: String, val title: String, val updatedAt: Long)

/** Provider failures, mapped to understandable messages in the UI. */
sealed interface AiError {
    data object MissingKey : AiError
    data object InvalidKey : AiError
    data object RateLimited : AiError
    data class ModelNotFound(val model: String) : AiError
    data object Offline : AiError
    data object Timeout : AiError
    data class Server(val code: Int) : AiError
    data object BadResponse : AiError
    data class Rejected(val code: Int, val detail: String) : AiError
}
