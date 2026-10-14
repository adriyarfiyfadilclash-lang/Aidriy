package com.aidriy.app.data.ai

import com.aidriy.app.domain.model.AiError
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/** An image sent with a message (vision models). Base64 without the data: prefix. */
data class AiImage(val mimeType: String, val base64: String)

data class AiTurn(val role: ChatRole, val text: String, val images: List<AiImage> = emptyList())

data class CompletionRequest(
    val model: String,
    val system: String,
    val turns: List<AiTurn>,
    val maxOutputTokens: Int = MAX_OUTPUT_TOKENS,
)

sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val error: AiError) : AiResult
}

/**
 * Provider adapter. Each implementation owns its endpoint, payload shape and response parsing,
 * so providers can change independently. Request bodies and keys are never logged.
 *
 * With [onText] set, the answer is streamed (server-sent events) and [onText] receives the text
 * so far after every chunk; the full answer is still returned at the end.
 */
interface AiProvider {
    val id: AiProviderId
    suspend fun complete(apiKey: String, request: CompletionRequest, onText: ((String) -> Unit)? = null): AiResult
}

object AiProviders {
    fun forId(id: AiProviderId, otherBaseUrl: String = AiProviderId.DEFAULT_OTHER_BASE_URL): AiProvider = when (id) {
        AiProviderId.OPENAI -> OpenAiCompatible(AiProviderId.OPENAI, "https://api.openai.com/v1", sendMaxTokens = false)
        AiProviderId.OTHER -> OpenAiCompatible(AiProviderId.OTHER, otherBaseUrl.trim().trimEnd('/'), sendMaxTokens = true)
        AiProviderId.ANTHROPIC -> AnthropicProvider
        AiProviderId.GEMINI -> GeminiProvider
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 90_000
internal const val MAX_OUTPUT_TOKENS = 2048

private class OpenAiCompatible(
    override val id: AiProviderId,
    private val baseUrl: String,
    private val sendMaxTokens: Boolean,
) : AiProvider {

    override suspend fun complete(apiKey: String, request: CompletionRequest, onText: ((String) -> Unit)?): AiResult {
        if (!baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) return AiResult.Failure(AiError.Rejected(0, "URL tidak valid"))
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", request.system))
        request.turns.forEach { turn ->
            val content: Any = if (turn.images.isEmpty()) {
                turn.text
            } else {
                JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", turn.text))
                    turn.images.forEach { img ->
                        put(
                            JSONObject().put("type", "image_url")
                                .put("image_url", JSONObject().put("url", "data:${img.mimeType};base64,${img.base64}")),
                        )
                    }
                }
            }
            messages.put(JSONObject().put("role", turn.role.wireName()).put("content", content))
        }
        val body = JSONObject().put("model", request.model).put("messages", messages)
        if (sendMaxTokens) body.put("max_tokens", request.maxOutputTokens)
        if (onText != null) body.put("stream", true)
        return post(
            url = "$baseUrl/chat/completions",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = body,
            model = request.model,
            stream = onText != null,
            onText = onText,
            parseFull = { json -> json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content") },
            parseChunk = { json ->
                json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                    .takeIf { it != "null" }.orEmpty()
            },
        )
    }
}

private object AnthropicProvider : AiProvider {
    override val id = AiProviderId.ANTHROPIC

    override suspend fun complete(apiKey: String, request: CompletionRequest, onText: ((String) -> Unit)?): AiResult {
        val messages = JSONArray()
        request.turns.forEach { turn ->
            val content: Any = if (turn.images.isEmpty()) {
                turn.text
            } else {
                JSONArray().apply {
                    turn.images.forEach { img ->
                        put(
                            JSONObject().put("type", "image").put(
                                "source",
                                JSONObject().put("type", "base64").put("media_type", img.mimeType).put("data", img.base64),
                            ),
                        )
                    }
                    put(JSONObject().put("type", "text").put("text", turn.text))
                }
            }
            messages.put(JSONObject().put("role", turn.role.wireName()).put("content", content))
        }
        val body = JSONObject()
            .put("model", request.model)
            .put("max_tokens", request.maxOutputTokens)
            .put("system", request.system)
            .put("messages", messages)
        if (onText != null) body.put("stream", true)
        return post(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body = body,
            model = request.model,
            stream = onText != null,
            onText = onText,
            parseFull = { json ->
                val content = json.getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.getString("text"))
                    }
                }
            },
            parseChunk = { json ->
                if (json.optString("type") == "content_block_delta") json.optJSONObject("delta")?.optString("text").orEmpty() else ""
            },
        )
    }
}

private object GeminiProvider : AiProvider {
    override val id = AiProviderId.GEMINI

    override suspend fun complete(apiKey: String, request: CompletionRequest, onText: ((String) -> Unit)?): AiResult {
        val contents = JSONArray()
        request.turns.forEach { turn ->
            val parts = JSONArray().put(JSONObject().put("text", turn.text))
            turn.images.forEach { img ->
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", img.mimeType).put("data", img.base64)))
            }
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "model")
                    .put("parts", parts),
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", request.maxOutputTokens))
        val encodedModel = URLEncoder.encode(request.model, "UTF-8")
        val base = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel"
        val parse: (JSONObject) -> String = { json ->
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            buildString {
                if (parts != null) for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
            }
        }
        return post(
            url = if (onText != null) "$base:streamGenerateContent?alt=sse" else "$base:generateContent",
            headers = mapOf("x-goog-api-key" to apiKey),
            body = body,
            model = request.model,
            stream = onText != null,
            onText = onText,
            parseFull = parse,
            parseChunk = parse,
        )
    }
}

private fun ChatRole.wireName(): String = if (this == ChatRole.USER) "user" else "assistant"

/**
 * HTTPS POST with JSON. Non-streaming responses are parsed with [parseFull]; streaming ones are
 * read as server-sent events, each `data:` line parsed with [parseChunk]. Cancelling the
 * coroutine disconnects the request.
 */
private suspend fun post(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    model: String,
    stream: Boolean,
    onText: ((String) -> Unit)?,
    parseFull: (JSONObject) -> String,
    parseChunk: (JSONObject) -> String,
): AiResult = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    // Blocking socket reads ignore coroutine cancellation; this watcher closes the socket instead.
    val watcher = launch {
        try {
            awaitCancellation()
        } finally {
            connection.disconnect()
        }
    }
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val text = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return@withContext AiResult.Failure(mapHttpError(code, text, model))
        }

        val answer = if (stream) {
            val sb = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val json = try {
                        JSONObject(data)
                    } catch (e: JSONException) {
                        continue
                    }
                    // Some providers send errors inside the stream.
                    json.optJSONObject("error")?.let { err ->
                        return@withContext AiResult.Failure(AiError.Rejected(code, err.optString("message").take(200)))
                    }
                    val piece = try {
                        parseChunk(json)
                    } catch (e: JSONException) {
                        ""
                    }
                    if (piece.isNotEmpty()) {
                        sb.append(piece)
                        onText?.invoke(sb.toString())
                    }
                }
            }
            sb.toString()
        } else {
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            try {
                parseFull(JSONObject(text))
            } catch (e: JSONException) {
                return@withContext AiResult.Failure(AiError.BadResponse)
            }
        }.trim()
        if (answer.isEmpty()) AiResult.Failure(AiError.BadResponse) else AiResult.Success(answer)
    } catch (e: SocketTimeoutException) {
        AiResult.Failure(AiError.Timeout)
    } catch (e: UnknownHostException) {
        AiResult.Failure(AiError.Offline)
    } catch (e: IOException) {
        AiResult.Failure(AiError.Offline)
    } finally {
        watcher.cancel()
        connection.disconnect()
    }
}

private fun mapHttpError(code: Int, body: String, model: String): AiError {
    val detail = (try {
        val json = JSONObject(body)
        val error = json.opt("error")
        when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> json.optString("message")
        }
    } catch (e: JSONException) {
        ""
    }).take(200)
    val lower = detail.lowercase()
    return when {
        code == 401 || code == 403 -> AiError.InvalidKey
        code == 429 -> AiError.RateLimited
        code == 404 -> AiError.ModelNotFound(model)
        code == 400 && "api key" in lower -> AiError.InvalidKey
        code == 400 && "model" in lower -> AiError.ModelNotFound(model)
        code >= 500 -> AiError.Server(code)
        else -> AiError.Rejected(code, detail)
    }
}
