package com.aidriy.app.data.cloud

import android.net.Uri
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object EmailNotConfirmed : AuthError
    data object UserExists : AuthError
    data class WeakPassword(val detail: String) : AuthError
    data object InvalidEmail : AuthError
    data object RateLimited : AuthError
    data object Offline : AuthError
    data object SessionExpired : AuthError
    data class Other(val code: Int, val detail: String) : AuthError
}

sealed interface AuthResult {
    data class SignedIn(val session: CloudSession) : AuthResult

    /** Account created; the user must open the confirmation link in the email first. */
    data class ConfirmEmail(val email: String) : AuthResult
    data class Failed(val error: AuthError) : AuthResult
}

/** Supabase Auth (GoTrue) over plain HTTPS: email + password. */
class CloudAuth {

    private val base = "${CloudConfig.URL}/auth/v1"
    private val redirect = URLEncoder.encode(CloudConfig.REDIRECT, "UTF-8")

    suspend fun signUp(email: String, password: String): AuthResult = call {
        val r = CloudHttp.request("POST", "$base/signup?redirect_to=$redirect", emptyMap(), body(email, password))
        val o = JSONObject(r.body)
        if (o.has("access_token")) AuthResult.SignedIn(parseSession(o)) else AuthResult.ConfirmEmail(email)
    }

    suspend fun signIn(email: String, password: String): AuthResult = call {
        val r = CloudHttp.request("POST", "$base/token?grant_type=password", emptyMap(), body(email, password))
        AuthResult.SignedIn(parseSession(JSONObject(r.body)))
    }

    suspend fun refresh(session: CloudSession): AuthResult = call {
        val payload = JSONObject().put("refresh_token", session.refreshToken).toString().toByteArray()
        val r = CloudHttp.request("POST", "$base/token?grant_type=refresh_token", emptyMap(), payload)
        AuthResult.SignedIn(parseSession(JSONObject(r.body)))
    }

    /** Sends the "reset password" email. The link opens the app (aidriy://auth). */
    suspend fun sendPasswordReset(email: String): AuthError? = when (
        val r = call {
            CloudHttp.request("POST", "$base/recover?redirect_to=$redirect", emptyMap(), JSONObject().put("email", email.trim()).toString().toByteArray())
            AuthResult.ConfirmEmail(email)
        }
    ) {
        is AuthResult.Failed -> r.error
        else -> null
    }

    suspend fun updatePassword(session: CloudSession, password: String): AuthError? = when (
        val r = call {
            CloudHttp.request(
                "PUT", "$base/user", bearer(session),
                JSONObject().put("password", password).toString().toByteArray(),
            )
            AuthResult.SignedIn(session)
        }
    ) {
        is AuthResult.Failed -> r.error
        else -> null
    }

    /** Best effort: revokes the refresh token on the server. */
    suspend fun signOut(session: CloudSession) {
        runCatching { CloudHttp.request("POST", "$base/logout", bearer(session), ByteArray(0)) }
    }

    /**
     * Handles the link from a confirmation or reset email: aidriy://auth#access_token=...&type=...
     * Returns the session and the link type ("signup", "recovery", ...), or an error.
     */
    suspend fun fromRedirect(uri: Uri): Pair<AuthResult, String?> {
        val params = (uri.fragment ?: uri.query).orEmpty().split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        params["error_description"]?.let { return AuthResult.Failed(AuthError.Other(400, it)) to params["type"] }
        val access = params["access_token"] ?: return AuthResult.Failed(AuthError.Other(400, "")) to null
        val refresh = params["refresh_token"].orEmpty()
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600
        val result = call {
            val r = CloudHttp.request("GET", "$base/user", mapOf("Authorization" to "Bearer $access"))
            val user = JSONObject(r.body)
            AuthResult.SignedIn(
                CloudSession(access, refresh, System.currentTimeMillis() + expiresIn * 1000, user.getString("id"), user.optString("email")),
            )
        }
        return result to params["type"]
    }

    private fun body(email: String, password: String) =
        JSONObject().put("email", email.trim()).put("password", password).toString().toByteArray()

    private fun bearer(session: CloudSession) = mapOf("Authorization" to "Bearer ${session.accessToken}")

    private fun parseSession(o: JSONObject): CloudSession {
        val user = o.getJSONObject("user")
        return CloudSession(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            userId = user.getString("id"),
            email = user.optString("email"),
        )
    }

    private suspend fun call(block: suspend () -> AuthResult): AuthResult = try {
        block()
    } catch (e: CloudOfflineException) {
        AuthResult.Failed(AuthError.Offline)
    } catch (e: CloudHttpException) {
        AuthResult.Failed(mapError(e.code, e.body))
    } catch (e: JSONException) {
        AuthResult.Failed(AuthError.Other(0, "bad response"))
    }

    companion object {
        /** Maps GoTrue error codes (error_code / error) to app errors. */
        fun mapError(code: Int, body: String): AuthError {
            val o = try {
                JSONObject(body)
            } catch (e: JSONException) {
                JSONObject()
            }
            val errorCode = o.optString("error_code").ifEmpty { o.optString("error") }
            val message = o.optString("msg").ifEmpty { o.optString("error_description").ifEmpty { o.optString("message") } }
            return when {
                errorCode == "invalid_credentials" || errorCode == "invalid_grant" && "credentials" in message.lowercase() ->
                    AuthError.InvalidCredentials
                errorCode == "email_not_confirmed" -> AuthError.EmailNotConfirmed
                errorCode == "user_already_exists" || errorCode == "email_exists" -> AuthError.UserExists
                errorCode == "weak_password" -> AuthError.WeakPassword(message)
                errorCode == "email_address_invalid" || errorCode == "validation_failed" && "email" in message.lowercase() ->
                    AuthError.InvalidEmail
                code == 429 || errorCode.startsWith("over_") -> AuthError.RateLimited
                errorCode == "refresh_token_not_found" || errorCode == "refresh_token_already_used" ||
                    errorCode == "session_not_found" || errorCode == "invalid_grant" -> AuthError.SessionExpired
                else -> AuthError.Other(code, message.take(200))
            }
        }
    }
}
