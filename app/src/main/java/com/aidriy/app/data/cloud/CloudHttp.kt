package com.aidriy.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The AIDRIY cloud project (Supabase). The publishable key is meant to ship in apps; RLS protects the data. */
object CloudConfig {
    const val URL = "https://qwugctxeakfllzgfbymy.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_IbsbZQXw6DrhkR8FhXheeQ_A2k7Eogr"
    const val REDIRECT = "aidriy://auth"
    const val BUCKET = "berkas"
}

/** No network, or the server could not be reached. */
class CloudOfflineException(cause: Throwable) : IOException(cause)

/** A non-2xx answer. [body] is the server's (JSON) error text, never logged. */
class CloudHttpException(val code: Int, val body: String) : IOException("HTTP $code")

/** Minimal HTTPS client for Supabase. Cancelling the coroutine closes the connection. */
internal object CloudHttp {

    class Response(val code: Int, val body: String)

    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
        contentType: String = "application/json",
    ): Response = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw CloudHttpException(code, text)
            Response(code, text)
        } catch (e: CloudHttpException) {
            throw e
        } catch (e: IOException) {
            throw CloudOfflineException(e)
        } finally {
            watcher.cancel()
            connection.disconnect()
        }
    }

    /** Streams [file] as the request body (Storage uploads), without loading it into memory. */
    suspend fun upload(url: String, headers: Map<String, String>, file: File, contentType: String) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", contentType)
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            file.inputStream().use { input -> connection.outputStream.use { input.copyTo(it) } }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw CloudHttpException(code, connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }
        } catch (e: CloudHttpException) {
            throw e
        } catch (e: IOException) {
            throw CloudOfflineException(e)
        } finally {
            watcher.cancel()
            connection.disconnect()
        }
    }

    /** Streams a response body to [target] (Storage downloads). */
    suspend fun download(url: String, headers: Map<String, String>, target: File) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                connection.disconnect()
            }
        }
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("apikey", CloudConfig.PUBLISHABLE_KEY)
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw CloudHttpException(code, connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
            }
            val tmp = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: CloudHttpException) {
            throw e
        } catch (e: IOException) {
            throw CloudOfflineException(e)
        } finally {
            watcher.cancel()
            connection.disconnect()
        }
    }
}
