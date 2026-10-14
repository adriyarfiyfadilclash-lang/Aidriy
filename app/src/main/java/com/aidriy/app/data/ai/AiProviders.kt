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

data class AiTurn(val role: ChatRole, val text: String)

sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val error: AiError) : AiResult
}

/**
 * Provider adapter. Each implementation owns its endpoint, payload shape and response parsing,
 * so providers can change independently. Request bodies and keys are never logged.
 */
interface AiProvider {
    val id: AiProviderId
    suspend fun complete(apiKey: String, model: String, system: String, turns: List<AiTurn>): AiResult
}

object AiProviders {
    fun forId(id: AiProviderId): AiProvider = when (id) {
        AiProviderId.OPENAI -> OpenAiProvider
        AiProviderId.ANTHROPIC -> AnthropicProvider
        AiProviderId.GEMINI -> GeminiProvider
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 90_000
private const val MAX_OUTPUT_TOKENS = 2048

private object OpenAiProvider : AiProvider {
    override val id = AiProviderId.OPENAI

    override suspend fun complete(apiKey: String, model: String, system: String, turns: List<AiTurn>): AiResult {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        turns.forEach { messages.put(JSONObject().put("role", it.role.wireName()).put("content", it.text)) }
        val body = JSONObject().put("model", model).put("messages", messages)
        return postJson(
            url = "https://api.openai.com/v1/chat/completions",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = body,
            model = model,
        ) { json ->
            json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }
}

private object AnthropicProvider : AiProvider {
    override val id = AiProviderId.ANTHROPIC

    override suspend fun complete(apiKey: String, model: String, system: String, turns: List<AiTurn>): AiResult {
        val messages = JSONArray()
        turns.forEach { messages.put(JSONObject().put("role", it.role.wireName()).put("content", it.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_OUTPUT_TOKENS)
            .put("system", system)
            .put("messages", messages)
        return postJson(
            url = "https://api.anthropic.com/v1/messages",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body = body,
            model = model,
        ) { json ->
            val content = json.getJSONArray("content")
            buildString {
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.optString("type") == "text") append(block.getString("text"))
                }
            }
        }
    }
}

private object GeminiProvider : AiProvider {
    override val id = AiProviderId.GEMINI

    override suspend fun complete(apiKey: String, model: String, system: String, turns: List<AiTurn>): AiResult {
        val contents = JSONArray()
        turns.forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == ChatRole.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.text))),
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val encodedModel = URLEncoder.encode(model, "UTF-8")
        return postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent",
            headers = mapOf("x-goog-api-key" to apiKey),
            body = body,
            model = model,
        ) { json ->
            val parts = json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            buildString {
                for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
            }
        }
    }
}

private fun ChatRole.wireName(): String = if (this == ChatRole.USER) "user" else "assistant"

/** HTTPS POST with JSON in and out. Cancelling the coroutine disconnects the request. */
private suspend fun postJson(
    url: String,
    headers: Map<String, String>,
    body: JSONObject,
    model: String,
    parse: (JSONObject) -> String,
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
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

        if (code !in 200..299) return@withContext AiResult.Failure(mapHttpError(code, text, model))
        val answer = try {
            parse(JSONObject(text)).trim()
        } catch (e: JSONException) {
            return@withContext AiResult.Failure(AiError.BadResponse)
        }
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
