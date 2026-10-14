package com.aidriy.app.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A signed-in Supabase session. Tokens are secrets: never logged, stored encrypted. */
data class CloudSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis when [accessToken] expires. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
) {
    fun expiresSoon(now: Long = System.currentTimeMillis()) = expiresAt - now < 60_000
}

/**
 * Keeps the session encrypted with an Android Keystore AES-GCM key (like API keys), plus a few
 * non-secret sync facts: which account this phone last synced with and the pull cursors.
 */
class SessionStore(context: Context) {

    private val secrets = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val meta = context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(read())
    val session: StateFlow<CloudSession?> = _session.asStateFlow()

    fun save(session: CloudSession) {
        val json = JSONObject()
            .put("a", session.accessToken).put("r", session.refreshToken).put("e", session.expiresAt)
            .put("u", session.userId).put("m", session.email)
            .toString()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.iv + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            secrets.edit().putString(KEY_SESSION, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        } catch (e: GeneralSecurityException) {
            // Without encryption the session is kept in memory only (signed out after a restart).
            Log.w(TAG, "Session not persisted: ${e.javaClass.simpleName}")
        }
        _session.value = session
    }

    fun clear() {
        secrets.edit().remove(KEY_SESSION).apply()
        _session.value = null
    }

    /** The account whose data this phone holds (kept after signing out). */
    var lastUserId: String?
        get() = meta.getString(KEY_LAST_USER, null)
        set(value) = meta.edit().putString(KEY_LAST_USER, value).apply()

    var lastSyncAt: Long
        get() = meta.getLong(KEY_LAST_SYNC, 0L)
        set(value) = meta.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun cursor(table: String): Pair<String?, Set<String>> {
        val raw = meta.getString("cursor_$table", null) ?: return null to emptySet()
        return try {
            val o = JSONObject(raw)
            val ids = o.optJSONArray("i")
            (if (o.isNull("v")) null else o.getString("v")) to
                (0 until (ids?.length() ?: 0)).map { ids!!.getString(it) }.toSet()
        } catch (e: JSONException) {
            null to emptySet()
        }
    }

    fun saveCursor(table: String, value: String?, ids: Set<String>) {
        val o = JSONObject().put("v", value ?: JSONObject.NULL).put("i", org.json.JSONArray(ids.toList()))
        meta.edit().putString("cursor_$table", o.toString()).apply()
    }

    fun resetCursors() {
        val editor = meta.edit()
        meta.all.keys.filter { it.startsWith("cursor_") }.forEach { editor.remove(it) }
        editor.remove(KEY_LAST_SYNC).apply()
    }

    private fun read(): CloudSession? {
        val stored = secrets.getString(KEY_SESSION, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val o = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            CloudSession(o.getString("a"), o.getString("r"), o.getLong("e"), o.getString("u"), o.getString("m"))
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS = "aidriy_session"
        const val META_PREFS = "aidriy_sync_meta"
        const val KEY_SESSION = "session"
        const val KEY_LAST_USER = "last_user"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_ALIAS = "aidriy_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
