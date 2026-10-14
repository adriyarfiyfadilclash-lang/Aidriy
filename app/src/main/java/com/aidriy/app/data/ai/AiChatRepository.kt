package com.aidriy.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.aidriy.app.domain.model.ResponseLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aidriy.app.domain.markdown.FormatGuard
import com.aidriy.app.domain.markdown.FormatOptions
import com.aidriy.app.domain.markdown.FormatPrompt
import com.aidriy.app.core.IdGenerator
import com.aidriy.app.core.time.TimeProvider
import com.aidriy.app.core.time.nowMillis
import com.aidriy.app.data.files.FileRepository
import com.aidriy.app.data.local.dao.AiDao
import com.aidriy.app.data.local.dao.IdeaDao
import com.aidriy.app.data.local.dao.JournalDao
import com.aidriy.app.data.local.dao.NoteDao
import com.aidriy.app.data.local.entity.AiConversationEntity
import com.aidriy.app.data.local.entity.AiMessageEntity
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.data.settings.SecretStore
import com.aidriy.app.domain.SearchQuery
import com.aidriy.app.domain.model.AiError
import com.aidriy.app.domain.model.AiLanguage
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.AnswerStyle
import com.aidriy.app.domain.model.ChatMessage
import com.aidriy.app.domain.model.ChatRole
import com.aidriy.app.domain.model.ContextItem
import com.aidriy.app.domain.model.Conversation
import com.aidriy.app.domain.model.SourceRef
import com.aidriy.app.domain.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

/** Outcome of [AiChatRepository.send]. The user's message is always stored first. */
sealed interface FormatOutcome {
    data class Checked(val result: FormatGuard.Result) : FormatOutcome
    data class Failed(val error: AiError) : FormatOutcome
    data class TooLong(val maxChars: Int) : FormatOutcome
}

sealed interface SendOutcome {
    data class Answered(val conversationId: String) : SendOutcome
    data class Failed(val conversationId: String, val error: AiError) : SendOutcome
}

class AiChatRepository(
    private val aiDao: AiDao,
    private val noteDao: NoteDao,
    private val ideaDao: IdeaDao,
    private val journalDao: JournalDao,
    private val files: FileRepository,
    private val secrets: SecretStore,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    fun observeConversations(): Flow<List<Conversation>> = aiDao.observeConversations().map { rows ->
        rows.map { Conversation(it.id, it.title, it.updatedAt) }
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        aiDao.observeMessages(conversationId).map { rows -> rows.map { it.toDomain() } }

    suspend fun deleteConversation(id: String) {
        aiDao.deleteConversation(id)
    }

    suspend fun deleteAllConversations() = aiDao.deleteAllConversations()

    // ---------------------------------------------------------------- retrieval (Ask My Brain)

    /**
     * Finds local records relevant to [question] with plain keyword matching (no embeddings,
     * no network). Returns at most [MAX_SOURCES] items within a total character budget.
     */
    suspend fun findContext(question: String): List<ContextItem> {
        val keywords = keywords(question)
        if (keywords.isEmpty()) return emptyList()

        val scores = HashMap<String, Int>()
        val items = HashMap<String, ContextItem>()
        fun add(ref: SourceRef, body: String, title: String, keyword: String) {
            val key = "${ref.type.key}:${ref.id}"
            val bonus = if (title.lowercase(Locale.ROOT).contains(keyword)) 2 else 1
            scores[key] = (scores[key] ?: 0) + bonus
            items.getOrPut(key) { ContextItem(ref, body.take(PER_ITEM_CHARS)) }
        }

        for (keyword in keywords) {
            val pattern = SearchQuery.toLikePattern(keyword)
            noteDao.search(pattern, PER_KEYWORD).first().forEach { row ->
                val n = row.note
                add(SourceRef(SourceType.NOTE, n.id, n.title.ifBlank { "Catatan" }), "${n.title}\n${n.content}", n.title, keyword)
            }
            ideaDao.search(pattern, PER_KEYWORD).first().forEach { row ->
                val i = row.idea
                add(SourceRef(SourceType.IDEA, i.id, i.title), "${i.title}\n${i.content}", i.title, keyword)
            }
            journalDao.search(pattern, PER_KEYWORD).first().forEach { row ->
                val j = row.entry
                add(SourceRef(SourceType.JOURNAL, j.date, "Journal ${j.date}"), "${j.title}\n${j.content}", j.title, keyword)
            }
            files.searchReadable(keyword, PER_KEYWORD).forEach { f ->
                add(SourceRef(SourceType.FILE, f.id, f.name), f.extractedText.orEmpty(), f.name, keyword)
            }
        }

        var budget = TOTAL_CHARS
        return scores.entries.sortedByDescending { it.value }
            .take(MAX_SOURCES)
            .mapNotNull { items[it.key] }
            .mapNotNull { item ->
                if (budget <= 0) return@mapNotNull null
                val text = item.text.take(budget)
                budget -= text.length
                item.copy(text = text)
            }
    }

    /** Context for a record the user picked explicitly (e.g. "Ringkas catatan ini"). */
    suspend fun contextFor(ref: SourceRef): ContextItem? = when (ref.type) {
        SourceType.NOTE -> noteDao.getById(ref.id)?.let {
            ContextItem(ref.copy(title = it.title.ifBlank { "Catatan" }), "${it.title}\n${it.content}".take(SINGLE_ITEM_CHARS))
        }
        SourceType.IDEA -> ideaDao.getById(ref.id)?.let {
            ContextItem(ref.copy(title = it.title), "${it.title}\n${it.content}".take(SINGLE_ITEM_CHARS))
        }
        SourceType.JOURNAL -> journalDao.getByDate(ref.id)?.let {
            ContextItem(ref, "${it.title}\n${it.content}".take(SINGLE_ITEM_CHARS))
        }
        SourceType.FILE -> files.getFile(ref.id)?.takeIf { !it.extractedText.isNullOrBlank() }?.let {
            ContextItem(ref.copy(title = it.name), it.extractedText!!.take(SINGLE_ITEM_CHARS))
        }
    }

    // ---------------------------------------------------------------- sending

    /**
     * Stores the user's message, calls the provider, and stores the answer (or the error).
     * Failures never touch notes, ideas, journals or files.
     */
    suspend fun send(
        conversationId: String?,
        question: String,
        provider: AiProviderId,
        settings: AppSettings,
        context: List<ContextItem>,
        retrievalRan: Boolean,
        attachments: List<SourceRef> = emptyList(),
        images: List<AiImage> = emptyList(),
        onText: ((String) -> Unit)? = null,
    ): SendOutcome {
        val now = time.nowMillis()
        val convId = conversationId ?: ids.newId().also { id ->
            aiDao.insertConversation(AiConversationEntity(id, titleFrom(question), now, now))
        }
        aiDao.touchConversation(convId, aiDao.getConversation(convId)?.title ?: titleFrom(question), now)

        val history = aiDao.getMessages(convId)
            .filter { !it.isError }
            .takeLast(MAX_HISTORY)
            .map { AiTurn(if (it.role == ROLE_USER) ChatRole.USER else ChatRole.ASSISTANT, it.content) }

        aiDao.insertMessage(
            AiMessageEntity(
                id = ids.newId(), conversationId = convId, role = ROLE_USER, content = question,
                provider = null, model = null, sources = SourceRef.encodeAll(attachments), noLocalMatch = false,
                isError = false, createdAt = now,
            ),
        )

        val apiKey = secrets.getKey(provider)
        val model = settings.modelFor(provider)
        val result = if (apiKey == null) {
            AiResult.Failure(AiError.MissingKey)
        } else {
            AiProviders.forId(provider, settings.otherBaseUrl).complete(
                apiKey = apiKey,
                request = CompletionRequest(
                    model = model,
                    system = systemPrompt(settings, hasContext = context.isNotEmpty()),
                    turns = history + AiTurn(ChatRole.USER, userTurn(question, context), images),
                    maxOutputTokens = settings.responseLength.maxTokens,
                ),
                onText = if (settings.streaming) onText else null,
            )
        }

        val answeredAt = maxOf(time.nowMillis(), now + 1)
        return when (result) {
            is AiResult.Success -> {
                aiDao.insertMessage(
                    AiMessageEntity(
                        id = ids.newId(), conversationId = convId, role = ROLE_ASSISTANT, content = result.text,
                        provider = provider.key, model = model,
                        sources = SourceRef.encodeAll(context.map { it.ref }),
                        noLocalMatch = retrievalRan && context.isEmpty(),
                        isError = false, createdAt = answeredAt,
                    ),
                )
                aiDao.touchConversation(convId, aiDao.getConversation(convId)?.title ?: titleFrom(question), answeredAt)
                SendOutcome.Answered(convId)
            }
            is AiResult.Failure -> {
                aiDao.insertMessage(
                    AiMessageEntity(
                        id = ids.newId(), conversationId = convId, role = ROLE_ASSISTANT,
                        content = AiErrorCodec.encode(result.error),
                        provider = provider.key, model = model, sources = "", noLocalMatch = false,
                        isError = true, createdAt = answeredAt,
                    ),
                )
                SendOutcome.Failed(convId, result.error)
            }
        }
    }

    /**
     * "Rapikan Format": asks the AI to reformat [markdown] and verifies that only formatting
     * changed. Images and Berkas links are replaced by placeholders before sending. Nothing is
     * stored; the caller shows a preview and applies it only after the user confirms.
     */
    suspend fun formatNote(
        provider: AiProviderId,
        model: String,
        title: String,
        markdown: String,
        options: FormatOptions,
        otherBaseUrl: String = AiProviderId.DEFAULT_OTHER_BASE_URL,
    ): FormatOutcome {
        if (markdown.length > FormatPrompt.MAX_CHARS) return FormatOutcome.TooLong(FormatPrompt.MAX_CHARS)
        val key = secrets.getKey(provider) ?: return FormatOutcome.Failed(AiError.MissingKey)
        val protected = FormatGuard.protect(markdown)
        val result = AiProviders.forId(provider, otherBaseUrl).complete(
            apiKey = key,
            request = CompletionRequest(
                model = model,
                system = FormatPrompt.system(options),
                turns = listOf(AiTurn(ChatRole.USER, FormatPrompt.user(title, protected.text))),
                maxOutputTokens = FormatPrompt.maxOutputTokens(markdown.length),
            ),
        )
        return when (result) {
            is AiResult.Failure -> FormatOutcome.Failed(result.error)
            is AiResult.Success -> FormatOutcome.Checked(FormatGuard.check(markdown, protected, result.text))
        }
    }

    /** Sends a tiny request to verify a key and model. Nothing is stored. */
    suspend fun testConnection(provider: AiProviderId, model: String, otherBaseUrl: String = AiProviderId.DEFAULT_OTHER_BASE_URL): AiResult {
        val key = secrets.getKey(provider) ?: return AiResult.Failure(AiError.MissingKey)
        return AiProviders.forId(provider, otherBaseUrl).complete(
            apiKey = key,
            request = CompletionRequest(model, "Reply with OK.", listOf(AiTurn(ChatRole.USER, "OK?")), maxOutputTokens = 16),
        )
    }

    fun searchConversations(query: String): Flow<List<Conversation>> =
        aiDao.searchConversations(SearchQuery.toLikePattern(query)).map { rows ->
            rows.map { Conversation(it.id, it.title, it.updatedAt) }
        }

    suspend fun renameConversation(id: String, title: String) {
        val clean = title.replace('\n', ' ').trim().take(80)
        if (clean.isNotEmpty()) aiDao.renameConversation(id, clean)
    }

    suspend fun setRating(messageId: String, rating: Int) {
        aiDao.setRating(messageId, rating.coerceIn(-1, 1))
    }

    /**
     * An image from Berkas, downscaled (longest side 1568 px) and JPEG-encoded for vision
     * models. Returns null when the file is missing or not a decodable image.
     */
    suspend fun imageForAi(fileId: String): AiImage? = withContext(Dispatchers.IO) {
        val file = files.getFile(fileId)?.takeIf { it.isImage } ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.localPath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.localPath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        val scale = MAX_IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
        AiImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    suspend fun deleteMessage(id: String) {
        aiDao.deleteMessage(id)
    }

    private fun userTurn(question: String, context: List<ContextItem>): String {
        if (context.isEmpty()) return question
        val block = context.mapIndexed { i, item -> "[${i + 1}] ${item.ref.title}\n${item.text}" }.joinToString("\n\n---\n\n")
        return "Konteks dari data pribadi pengguna:\n\n$block\n\n---\n\nPertanyaan: $question"
    }

    private fun systemPrompt(settings: AppSettings, hasContext: Boolean): String = buildString {
        val style = settings.answerStyle
        val language = settings.aiLanguage
        append("Kamu adalah asisten pribadi di aplikasi catatan AIDRIY. ")
        append(
            when (language) {
                AiLanguage.INDONESIAN -> "Selalu jawab dalam Bahasa Indonesia. "
                AiLanguage.ENGLISH -> "Always answer in English. "
            },
        )
        append(
            when (style) {
                AnswerStyle.CONCISE -> "Jawab singkat dan langsung ke inti. "
                AnswerStyle.BALANCED -> "Jawab jelas dengan panjang secukupnya. "
                AnswerStyle.DETAILED -> "Jawab lengkap dan rinci, dengan langkah atau contoh bila membantu. "
            },
        )
        append(
            when (settings.responseLength) {
                ResponseLength.SHORT -> "Batasi jawaban sekitar 1-2 paragraf pendek. "
                ResponseLength.MEDIUM -> ""
                ResponseLength.LONG -> "Boleh menjawab panjang bila topiknya membutuhkan. "
            },
        )
        append("Gunakan Markdown (heading, daftar, tabel, **tebal**, `kode`) bila membuat jawaban lebih mudah dibaca. ")
        if (hasContext) {
            append(
                "Pesan pengguna menyertakan konteks bernomor dari catatannya. Gunakan konteks itu bila relevan dan " +
                    "rujuk nomornya, misalnya [1]. Jangan mengarang isi catatan atau dokumen. Jika konteks tidak memuat " +
                    "jawabannya, katakan dengan jelas bahwa catatan pengguna tidak membahasnya.",
            )
        }
    }

    private fun titleFrom(question: String): String =
        question.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: "Percakapan"

    private fun AiMessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = if (role == ROLE_USER) ChatRole.USER else ChatRole.ASSISTANT,
        content = content,
        provider = provider?.let(AiProviderId::fromKey),
        model = model,
        sources = SourceRef.decodeAll(sources),
        noLocalMatch = noLocalMatch,
        isError = isError,
        createdAt = createdAt,
        rating = rating,
    )

    companion object {
        private const val ROLE_USER = "user"
        private const val ROLE_ASSISTANT = "assistant"
        private const val MAX_HISTORY = 10
        private const val MAX_SOURCES = 5
        private const val PER_KEYWORD = 10
        private const val PER_ITEM_CHARS = 2_500
        private const val SINGLE_ITEM_CHARS = 30_000
        private const val TOTAL_CHARS = 9_000
        private const val MAX_IMAGE_SIDE = 1_568

        private val STOPWORDS = setOf(
            "yang", "dan", "atau", "di", "ke", "dari", "ini", "itu", "untuk", "dengan", "pada", "ada", "apa",
            "apakah", "bagaimana", "kenapa", "mengapa", "saya", "aku", "kamu", "tolong", "jelaskan", "buat",
            "buatkan", "tentang", "dalam", "juga", "sudah", "belum", "akan", "bisa", "dong", "nya", "the", "and",
            "for", "what", "how", "why", "about", "with", "this", "that", "please", "catatan", "catatanku",
        )

        /** Lower-cased distinct keywords (3+ chars, no stopwords), at most six. */
        fun keywords(text: String): List<String> =
            text.lowercase(Locale.ROOT)
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 3 && it !in STOPWORDS }
                .distinct()
                .take(6)
    }
}

/** Stores provider errors in chat history as stable codes, rendered in the UI as Indonesian text. */
object AiErrorCodec {
    fun encode(error: AiError): String = when (error) {
        AiError.MissingKey -> "missing_key"
        AiError.InvalidKey -> "invalid_key"
        AiError.RateLimited -> "rate_limited"
        is AiError.ModelNotFound -> "model_not_found|${error.model}"
        AiError.Offline -> "offline"
        AiError.Timeout -> "timeout"
        is AiError.Server -> "server|${error.code}"
        AiError.BadResponse -> "bad_response"
        is AiError.Rejected -> "rejected|${error.code}|${error.detail}"
    }

    fun decode(raw: String): AiError {
        val parts = raw.split('|', limit = 3)
        return when (parts[0]) {
            "missing_key" -> AiError.MissingKey
            "invalid_key" -> AiError.InvalidKey
            "rate_limited" -> AiError.RateLimited
            "model_not_found" -> AiError.ModelNotFound(parts.getOrElse(1) { "" })
            "offline" -> AiError.Offline
            "timeout" -> AiError.Timeout
            "server" -> AiError.Server(parts.getOrNull(1)?.toIntOrNull() ?: 500)
            "rejected" -> AiError.Rejected(parts.getOrNull(1)?.toIntOrNull() ?: 400, parts.getOrElse(2) { "" })
            else -> AiError.BadResponse
        }
    }
}
