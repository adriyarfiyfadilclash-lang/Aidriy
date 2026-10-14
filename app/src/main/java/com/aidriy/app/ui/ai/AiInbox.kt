package com.aidriy.app.ui.ai

import com.aidriy.app.domain.model.SourceRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A request for the AI tab from another screen (suggestion, saved conversation, or a document). */
data class AiRequest(
    val suggestionId: String? = null,
    val conversationId: String? = null,
    val source: SourceRef? = null,
)

/** In-memory hand-off to the AI tab; consumed once by the chat screen. */
object AiInbox {
    private val _request = MutableStateFlow<AiRequest?>(null)
    val request: StateFlow<AiRequest?> = _request.asStateFlow()

    fun post(request: AiRequest) {
        _request.value = request
    }

    fun consume() {
        _request.value = null
    }
}
