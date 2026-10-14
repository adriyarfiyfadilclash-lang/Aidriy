package com.aidriy.app.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.aidriy.app.domain.model.AiProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores AI API keys encrypted with an AES-256-GCM key that lives in the Android Keystore
 * (it never leaves secure hardware where available). Only ciphertext is written to disk.
 * Keys are never logged.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _configured = MutableStateFlow(readConfigured())

    /** Providers that currently have a stored key. */
    val configured: StateFlow<Set<AiProviderId>> = _configured.asStateFlow()

    fun hasKey(provider: AiProviderId): Boolean = prefs.contains(prefKey(provider))

    fun getKey(provider: AiProviderId): String? {
        val stored = prefs.getString(prefKey(provider), null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipherText = bytes.copyOfRange(IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // Keystore key was reset (e.g. device credential change): the stored value is unusable.
            Log.w(TAG, "Stored API key could not be decrypted: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Stored API key is malformed")
            null
        }
    }

    /** Returns false if encryption is unavailable on this device. */
    fun setKey(provider: AiProviderId, apiKey: String): Boolean {
        val clean = apiKey.trim()
        require(clean.isNotEmpty())
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val encrypted = cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(prefKey(provider), Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
            _configured.value = readConfigured()
            true
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Could not encrypt API key: ${e.javaClass.simpleName}")
            false
        }
    }

    fun removeKey(provider: AiProviderId) {
        prefs.edit().remove(prefKey(provider)).apply()
        _configured.value = readConfigured()
    }

    fun removeAll() {
        prefs.edit().clear().apply()
        _configured.value = emptySet()
    }

    private fun readConfigured(): Set<AiProviderId> =
        AiProviderId.entries.filter { prefs.contains(prefKey(it)) }.toSet()

    private fun prefKey(provider: AiProviderId) = "key_${provider.key}"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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
        const val TAG = "SecretStore"
        const val PREFS = "aidriy_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "aidriy_api_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
