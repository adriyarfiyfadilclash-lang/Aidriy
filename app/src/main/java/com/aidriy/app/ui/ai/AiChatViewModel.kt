package com.aidriy.app.ui.ai

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.ai.AiChatRepository
import com.aidriy.app.data.ai.AiImage
import com.aidriy.app.data.ai.SendOutcome
import com.aidriy.app.data.files.AttachmentStore
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.data.settings.SecretStore
import com.aidriy.app.data.settings.SettingsRepository
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.DataResult
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import com.aidriy.app.domain.model.ContextItem
import com.aidriy.app.domain.model.Conversation
import com.aidriy.app.domain.model.ExtractionStatus
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import com.aidriy.app.domain.model.StoredFile
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Something attached to the next message: a note, a Berkas document, or an image. */
data class ChatAttachment(
    val ref: SourceRef,
    val sizeBytes: Long? = null,
    val isImage: Boolean = false,
)

/** A send waiting for the user to allow local content to go to the provider. */
data class PendingSend(
    val question: String,
    val provider: AiProviderId,
    val context: List<ContextItem>,
    val retrievalRan: Boolean,
    val attachments: List<ChatAttachment>,
)

sealed interface AiEvent {
    data class Message(val text: UiText) : AiEvent
    data object OpenSettings : AiEvent
}

/** Feedback values stored with an answer. */
object Rating {
    const val NONE = 0
    const val UP = 1
    const val DOWN = -1
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AiChatViewModel(
    private val handle: SavedStateHandle,
    private val chat: AiChatRepository,
    private val settingsRepo: SettingsRepository,
    secrets: SecretStore,
    private val files: AttachmentStore? = null,
) : ViewModel() {

    var input by mutableStateOf("")
    var attachments by mutableStateOf<List<ChatAttachment>>(emptyList())
        private set
    var isSending by mutableStateOf(false)
        private set
    var isAttaching by mutableStateOf(false)
        private set
    var pending by mutableStateOf<PendingSend?>(null)
        private set
    var historyQuery by mutableStateOf("")

    /** Text of the answer while it streams in; null when not streaming. */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val conversationId = MutableStateFlow(handle.get<String>(KEY_CONVERSATION))
    val currentConversation: StateFlow<String?> = conversationId

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val configured: StateFlow<Set<AiProviderId>> = secrets.configured

    val messages: StateFlow<List<ChatMessage>> = conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chat.observeMessages(id) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversations: StateFlow<List<Conversation>> = snapshotFlow { historyQuery.trim() }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else 200L }
        .flatMapLatest { q -> if (q.isEmpty()) chat.observeConversations() else chat.searchConversations(q) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Title of the open conversation (from the history list, so renames show at once). */
    val currentTitle: StateFlow<String?> = combine(conversationId, chat.observeConversations().catch { emit(emptyList()) }) { id, list ->
        id?.let { cid -> list.firstOrNull { it.id == cid }?.title }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val eventChannel = Channel<AiEvent>(Channel.BUFFERED)
    val events: Flow<AiEvent> = eventChannel.receiveAsFlow()

    private var sendJob: Job? = null

    init {
        // With "Simpan riwayat chat" off, chats from previous sessions are removed on start.
        viewModelScope.launch {
            if (!settingsRepo.settings.first().saveChatHistory && conversationId.value == null) {
                chat.deleteAllConversations()
            }
        }
    }

    fun provider(): AiProviderId = settings.value.defaultProvider

    // ------------------------------------------------------------------ conversations

    fun openConversation(id: String?) {
        cancel()
        conversationId.value = id
        handle[KEY_CONVERSATION] = id
        attachments = emptyList()
    }

    fun newConversation() = openConversation(null)

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chat.deleteConversation(id)
            if (conversationId.value == id) openConversation(null)
            eventChannel.send(AiEvent.Message(UiText(R.string.ai_chat_deleted)))
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { chat.renameConversation(id, title) }
    }

    // ------------------------------------------------------------------ attachments

    fun applyPrompt(prompt: String) {
        input = prompt
    }

    fun applySuggestion(suggestion: AiSuggestion) {
        input = suggestion.prompt
    }

    fun attach(ref: SourceRef?) {
        if (ref == null) {
            attachments = emptyList()
            return
        }
        if (attachments.none { it.ref.type == ref.type && it.ref.id == ref.id }) {
            attachments = (attachments + ChatAttachment(ref)).takeLast(MAX_ATTACHMENTS)
        }
    }

    fun removeAttachment(attachment: ChatAttachment) {
        attachments = attachments - attachment
    }

    fun attachStoredFile(file: StoredFile) {
        val ref = SourceRef(SourceType.FILE, file.id, file.name)
        if (attachments.none { it.ref.id == file.id }) {
            attachments = (attachments + ChatAttachment(ref, file.sizeBytes, file.isImage)).takeLast(MAX_ATTACHMENTS)
        }
    }

    /**
     * Copies a picked file into Berkas (so it stays available offline) and attaches it. Documents
     * wait briefly for text extraction, which the AI needs to read them.
     */
    fun attachFromDevice(uri: Uri) {
        val store = files ?: return
        viewModelScope.launch {
            isAttaching = true
            try {
                when (val result = store.import(uri, null, null)) {
                    is DataResult.Failure -> eventChannel.send(AiEvent.Message(result.error.toUiText()))
                    is DataResult.Success -> {
                        var file = result.value
                        if (!file.isImage) {
                            var waited = 0L
                            while (file.extractionStatus == ExtractionStatus.PENDING && waited < EXTRACTION_WAIT_MS) {
                                delay(500)
                                waited += 500
                                file = store.getFile(file.id) ?: break
                            }
                            if (file.extractedText.isNullOrBlank()) {
                                eventChannel.send(AiEvent.Message(UiText(R.string.ai_file_no_text)))
                            }
                        }
                        attachStoredFile(file)
                    }
                }
            } finally {
                isAttaching = false
            }
        }
    }

    // ------------------------------------------------------------------ sending

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || isSending || isAttaching) return
        val provider = provider()
        if (provider !in configured.value) {
            eventChannel.trySend(AiEvent.Message(UiText(R.string.ai_error_missing_key)))
            eventChannel.trySend(AiEvent.OpenSettings)
            return
        }
        val attached = attachments
        viewModelScope.launch {
            val textAttachments = attached.filter { !it.isImage }
            val fromAttachments = textAttachments.mapNotNull { chat.contextFor(it.ref) }
            if (textAttachments.isNotEmpty() && fromAttachments.isEmpty() && attached.none { it.isImage }) {
                eventChannel.send(AiEvent.Message(UiText(R.string.ai_source_unavailable)))
                return@launch
            }
            val retrieval = attached.isEmpty() && settings.value.useNotesData
            val context = if (retrieval) chat.findContext(question) else fromAttachments
            val p = PendingSend(question, provider, context, retrieval, attached)
            if (context.isNotEmpty() || attached.isNotEmpty()) {
                // Private content leaves the device only after an explicit "Izinkan".
                pending = p
            } else {
                dispatch(p)
            }
        }
    }

    fun confirmPending(allow: Boolean) {
        val p = pending ?: return
        pending = null
        dispatch(if (allow) p else p.copy(context = emptyList(), retrievalRan = false, attachments = emptyList()))
    }

    fun dismissPending() {
        pending = null
    }

    /** Re-sends the question of a failed answer (e.g. after being offline). */
    fun retry(errorMessage: ChatMessage) = resend(errorMessage)

    /** Asks again for a new answer to the same question. */
    fun regenerate(answer: ChatMessage) = resend(answer)

    private fun resend(answer: ChatMessage) {
        if (isSending) return
        val list = messages.value
        val index = list.indexOfFirst { it.id == answer.id }
        val question = list.take(if (index < 0) list.size else index).lastOrNull { it.role == ChatRole.USER } ?: return
        viewModelScope.launch {
            chat.deleteMessage(answer.id)
            chat.deleteMessage(question.id)
            input = question.content
            attachments = question.sources.map { ChatAttachment(it, isImage = isImageRef(it)) }
            send()
        }
    }

    private suspend fun isImageRef(ref: SourceRef): Boolean =
        ref.type == SourceType.FILE && files?.getFile(ref.id)?.isImage == true

    fun rate(message: ChatMessage, value: Int) {
        val next = if (message.rating == value) Rating.NONE else value
        viewModelScope.launch {
            chat.setRating(message.id, next)
            if (next != Rating.NONE) eventChannel.send(AiEvent.Message(UiText(R.string.ai_rating_saved)))
        }
    }

    fun cancel() {
        sendJob?.cancel()
        sendJob = null
        isSending = false
        _streaming.value = null
    }

    private fun dispatch(p: PendingSend) {
        isSending = true
        input = ""
        attachments = emptyList()
        sendJob = viewModelScope.launch {
            try {
                val images: List<AiImage> = p.attachments.filter { it.isImage }.mapNotNull { chat.imageForAi(it.ref.id) }
                val outcome = chat.send(
                    conversationId = conversationId.value,
                    question = p.question,
                    provider = p.provider,
                    settings = settings.value,
                    context = p.context,
                    retrievalRan = p.retrievalRan,
                    attachments = p.attachments.map { it.ref },
                    images = images,
                    onText = { _streaming.value = it },
                )
                val id = when (outcome) {
                    is SendOutcome.Answered -> outcome.conversationId
                    is SendOutcome.Failed -> outcome.conversationId
                }
                if (conversationId.value == null) {
                    conversationId.value = id
                    handle[KEY_CONVERSATION] = id
                }
            } finally {
                isSending = false
                _streaming.value = null
            }
        }
    }

    companion object {
        const val KEY_CONVERSATION = "ai_conversation"
        private const val MAX_ATTACHMENTS = 3
        private const val EXTRACTION_WAIT_MS = 20_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                AiChatViewModel(createSavedStateHandle(), c.aiChatRepository, c.settingsRepository, c.secretStore, c.fileRepository)
            }
        }
    }
}
