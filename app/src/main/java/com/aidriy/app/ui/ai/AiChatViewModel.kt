package com.aidriy.app.ui.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.ai.AiChatRepository
import com.aidriy.app.data.ai.SendOutcome
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.data.settings.SecretStore
import com.aidriy.app.data.settings.SettingsRepository
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import com.aidriy.app.domain.model.ContextItem
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.ui.common.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A send waiting for the user to confirm that local content may go to the provider. */
data class PendingSend(
    val question: String,
    val provider: AiProviderId,
    val context: List<ContextItem>,
    val retrievalRan: Boolean,
)

sealed interface AiEvent {
    data class Message(val text: UiText) : AiEvent
    data object OpenSettings : AiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val handle: SavedStateHandle,
    private val chat: AiChatRepository,
    private val settingsRepo: SettingsRepository,
    secrets: SecretStore,
) : ViewModel() {

    var input by mutableStateOf("")
    var attached by mutableStateOf<SourceRef?>(null)
        private set
    var isSending by mutableStateOf(false)
        private set
    var pending by mutableStateOf<PendingSend?>(null)
        private set

    private val conversationId = MutableStateFlow(handle.get<String>(KEY_CONVERSATION))
    val currentConversation: StateFlow<String?> = conversationId

    /** Provider chosen with the chips; null = use the default from settings. */
    private val chosenProvider = MutableStateFlow(handle.get<String>(KEY_PROVIDER)?.let(AiProviderId::fromKey))
    val selectedProvider: StateFlow<AiProviderId?> = chosenProvider

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val configured: StateFlow<Set<AiProviderId>> = secrets.configured

    val messages: StateFlow<List<ChatMessage>> = conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chat.observeMessages(id) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun provider(): AiProviderId = chosenProvider.value ?: settings.value.defaultProvider

    fun selectProvider(provider: AiProviderId) {
        chosenProvider.value = provider
        handle[KEY_PROVIDER] = provider.key
    }

    fun openConversation(id: String?) {
        cancel()
        conversationId.value = id
        handle[KEY_CONVERSATION] = id
        attached = null
    }

    fun newConversation() = openConversation(null)

    fun applySuggestion(suggestion: AiSuggestion) {
        input = suggestion.prompt
    }

    fun attach(ref: SourceRef?) {
        attached = ref
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || isSending) return
        val provider = provider()
        if (provider !in configured.value) {
            eventChannel.trySend(AiEvent.Message(UiText(R.string.ai_error_missing_key)))
            eventChannel.trySend(AiEvent.OpenSettings)
            return
        }
        viewModelScope.launch {
            val ref = attached
            val (context, retrieval) = when {
                ref != null -> listOfNotNull(chat.contextFor(ref)) to false
                settings.value.useNotesData -> chat.findContext(question) to true
                else -> emptyList<ContextItem>() to false
            }
            if (ref != null && context.isEmpty()) {
                eventChannel.send(AiEvent.Message(UiText(R.string.ai_source_unavailable)))
                return@launch
            }
            if (context.isNotEmpty()) {
                // Private content leaves the device only after an explicit confirmation.
                pending = PendingSend(question, provider, context, retrieval)
            } else {
                dispatch(PendingSend(question, provider, emptyList(), retrieval))
            }
        }
    }

    fun confirmPending(includeContext: Boolean) {
        val p = pending ?: return
        pending = null
        dispatch(if (includeContext) p else p.copy(context = emptyList(), retrievalRan = false))
    }

    fun dismissPending() {
        pending = null
    }

    /** Re-sends the last question after an error. */
    fun retry(errorMessage: ChatMessage) {
        val lastQuestion = messages.value.lastOrNull { it.role == ChatRole.USER && it.createdAt <= errorMessage.createdAt }
            ?: return
        viewModelScope.launch {
            chat.deleteMessage(errorMessage.id)
            chat.deleteMessage(lastQuestion.id)
            input = lastQuestion.content
            send()
        }
    }

    fun cancel() {
        sendJob?.cancel()
        sendJob = null
        isSending = false
    }

    private fun dispatch(p: PendingSend) {
        isSending = true
        input = ""
        attached = null
        sendJob = viewModelScope.launch {
            try {
                val outcome = chat.send(
                    conversationId = conversationId.value,
                    question = p.question,
                    provider = p.provider,
                    settings = settings.value,
                    context = p.context,
                    retrievalRan = p.retrievalRan,
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
            }
        }
    }

    companion object {
        const val KEY_CONVERSATION = "ai_conversation"
        private const val KEY_PROVIDER = "ai_provider"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                AiChatViewModel(createSavedStateHandle(), c.aiChatRepository, c.settingsRepository, c.secretStore)
            }
        }
    }
}
