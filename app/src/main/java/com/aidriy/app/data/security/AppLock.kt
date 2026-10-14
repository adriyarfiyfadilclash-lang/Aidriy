package com.aidriy.app.data.security

import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aidriy.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN hashing with PBKDF2 (salted, slow). The PIN itself is never stored. */
object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun matches(pin: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expectedHash.toByteArray())

    fun isValidPin(pin: String): Boolean = pin.length in 4..6 && pin.all { it.isDigit() }
}

/**
 * Locks the app on start and after it has been in the background for [GRACE_MS].
 * The lock is a UI gate over local data; data on disk is protected by Android's app sandbox.
 */
class AppLockManager(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Null until settings were read once (prevents a flash of unlocked content). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var backgroundedAt: Long? = null

    fun start() {
        scope.launch {
            val s = settings.settings.first()
            _locked.value = s.appLockEnabled && s.pinHash != null
            _ready.value = true
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (SystemClock.elapsedRealtime() - since < GRACE_MS) return
        scope.launch {
            val s = settings.settings.first()
            if (s.appLockEnabled && s.pinHash != null) _locked.value = true
        }
    }

    suspend fun tryPin(pin: String): Boolean {
        val s = settings.settings.first()
        val hash = s.pinHash ?: return true.also { _locked.value = false }
        val salt = s.pinSalt ?: return false
        val ok = PinHasher.matches(pin, salt, hash)
        if (ok) _locked.value = false
        return ok
    }

    fun unlockWithBiometric() {
        _locked.value = false
    }

    /** Pickers and the camera briefly background the app; don't lock for those. */
    fun suppressNextLock() {
        backgroundedAt = null
    }

    private companion object {
        const val GRACE_MS = 30_000L
    }
}
