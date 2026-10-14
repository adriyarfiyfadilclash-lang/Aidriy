package com.aidriy.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidriy.app.R
import com.aidriy.app.data.ai.AiResult
import com.aidriy.app.data.backup.BackupResult
import com.aidriy.app.data.backup.PendingRestore
import com.aidriy.app.data.security.PinHasher
import com.aidriy.app.data.settings.AppSettings
import com.aidriy.app.data.settings.TextSize
import com.aidriy.app.data.settings.ThemeMode
import com.aidriy.app.di.AppContainer
import com.aidriy.app.di.appContainer
import com.aidriy.app.domain.model.AiLanguage
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.ResponseLength
import com.aidriy.app.domain.model.AnswerStyle
import com.aidriy.app.domain.model.SortOrder
import com.aidriy.app.ui.common.UiText
import com.aidriy.app.ui.common.toUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One ViewModel for Pengaturan and its sub-screens; every change is persisted immediately. */
class SettingsViewModel(
    private val app: Application,
    private val c: AppContainer,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = c.settingsRepository.settings
        .catch { emit(AppSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val configured: StateFlow<Set<AiProviderId>> = c.secretStore.configured

    val storageUsed: StateFlow<Long> = c.fileRepository.observeTotalSize()
        .catch { emit(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = messageChannel.receiveAsFlow()

    var testingProvider by mutableStateOf<AiProviderId?>(null)
        private set

    /** Result of the last "Uji Koneksi", shown under the button: true ok, false failed, null none. */
    var testOk by mutableStateOf<Boolean?>(null)
        private set
    var testMessage by mutableStateOf<UiText?>(null)
        private set
    var pendingRestore by mutableStateOf<PendingRestore?>(null)
        private set
    var backupBusy by mutableStateOf(false)
        private set

    private fun say(res: Int, vararg args: Any) {
        messageChannel.trySend(UiText(res, args.toList()))
    }

    // ------------------------------------------------------------ profile

    fun setName(name: String) { viewModelScope.launch {
        c.settingsRepository.setProfileName(name)
        say(R.string.saved)
    } }

    fun setPhoto(uri: Uri) { viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            try {
                val target = File(app.filesDir, "profile.jpg")
                app.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: return@withContext null
                target.absolutePath
            } catch (e: Exception) {
                null
            }
        }
        if (ok == null) {
            say(R.string.error_file_unreadable)
        } else {
            // Force Coil to reload by changing the stored path only when it differs.
            c.settingsRepository.setProfilePhoto(null)
            c.settingsRepository.setProfilePhoto(ok)
        }
    } }

    fun removePhoto() { viewModelScope.launch {
        c.settingsRepository.setProfilePhoto(null)
        withContext(Dispatchers.IO) { File(app.filesDir, "profile.jpg").delete() }
    } }

    // ------------------------------------------------------------ appearance & notes

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { c.settingsRepository.setThemeMode(mode) } }
    fun setTextSize(size: TextSize) { viewModelScope.launch { c.settingsRepository.setTextSize(size) } }
    fun setAutosave(on: Boolean) { viewModelScope.launch { c.settingsRepository.setAutosave(on) } }
    fun setNoteSort(sort: SortOrder) { viewModelScope.launch { c.settingsRepository.setNoteSort(sort) } }

    // ------------------------------------------------------------ AI

    fun setDefaultProvider(p: AiProviderId) { viewModelScope.launch {
        c.settingsRepository.setDefaultProvider(p)
        testOk = null
        testMessage = null
    } }
    fun setStyle(style: AnswerStyle) { viewModelScope.launch { c.settingsRepository.setAnswerStyle(style) } }
    fun setLanguage(language: AiLanguage) { viewModelScope.launch { c.settingsRepository.setAiLanguage(language) } }
    fun setSaveHistory(on: Boolean) { viewModelScope.launch { c.settingsRepository.setSaveChatHistory(on) } }
    fun setShowSuggestions(on: Boolean) { viewModelScope.launch { c.settingsRepository.setShowSuggestions(on) } }
    fun setUseNotes(on: Boolean) { viewModelScope.launch { c.settingsRepository.setUseNotesData(on) } }
    fun setResponseLength(value: ResponseLength) { viewModelScope.launch { c.settingsRepository.setResponseLength(value) } }
    fun setStreaming(on: Boolean) { viewModelScope.launch { c.settingsRepository.setStreaming(on) } }
    fun setOtherBaseUrl(url: String) { viewModelScope.launch {
        val clean = url.trim()
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
            say(R.string.ai_base_url_invalid)
        } else {
            c.settingsRepository.setOtherBaseUrl(clean)
            testOk = null
            say(R.string.saved)
        }
    } }

    fun setModel(p: AiProviderId, model: String) { viewModelScope.launch {
        c.settingsRepository.setModel(p, model)
        say(R.string.saved)
    } }

    fun saveKey(p: AiProviderId, key: String) {
        if (key.isBlank()) return
        if (c.secretStore.setKey(p, key)) say(R.string.ai_key_saved) else say(R.string.ai_key_encrypt_failed)
    }

    fun removeKey(p: AiProviderId) {
        c.secretStore.removeKey(p)
        say(R.string.ai_key_removed)
    }

    fun removeAllKeys() {
        c.secretStore.removeAll()
        say(R.string.ai_key_removed)
    }

    fun clearChatHistory() { viewModelScope.launch {
        c.aiChatRepository.deleteAllConversations()
        say(R.string.ai_history_cleared)
    } }

    fun testConnection(p: AiProviderId) {
        if (testingProvider != null) return
        viewModelScope.launch {
            testingProvider = p
            testOk = null
            testMessage = null
            val result = c.aiChatRepository.testConnection(p, settings.value.modelFor(p), settings.value.otherBaseUrl)
            testingProvider = null
            when (result) {
                is AiResult.Success -> {
                    testOk = true
                    testMessage = UiText(R.string.ai_test_ok, listOf(p.displayName))
                }
                is AiResult.Failure -> {
                    testOk = false
                    testMessage = result.error.toUiText()
                }
            }
        }
    }

    // ------------------------------------------------------------ backup

    fun export(uri: Uri) {
        viewModelScope.launch {
            backupBusy = true
            when (val r = c.backupManager.export(uri)) {
                is BackupResult.Ok -> say(R.string.backup_exported, r.value.notes, r.value.ideas, r.value.journalEntries, r.value.files)
                is BackupResult.Error -> messageChannel.send(r.reason.toUiText())
            }
            backupBusy = false
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            backupBusy = true
            when (val r = c.backupManager.read(uri)) {
                is BackupResult.Ok -> pendingRestore = r.value
                is BackupResult.Error -> messageChannel.send(r.reason.toUiText())
            }
            backupBusy = false
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun confirmRestore() {
        val p = pendingRestore ?: return
        pendingRestore = null
        viewModelScope.launch {
            backupBusy = true
            when (val r = c.backupManager.restore(p)) {
                is BackupResult.Ok -> say(R.string.backup_restored, r.value.notes, r.value.ideas, r.value.journalEntries, r.value.files)
                is BackupResult.Error -> messageChannel.send(r.reason.toUiText())
            }
            backupBusy = false
        }
    }

    // ------------------------------------------------------------ security & notifications

    /** Returns null on success or an error message. */
    fun setPin(pin: String, confirm: String): UiText? {
        if (!PinHasher.isValidPin(pin)) return UiText(R.string.lock_pin_invalid)
        if (pin != confirm) return UiText(R.string.lock_pin_mismatch)
        viewModelScope.launch {
            val salt = PinHasher.newSalt()
            val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
            c.settingsRepository.setPin(hash, salt)
            say(R.string.lock_enabled)
        }
        return null
    }

    fun disableLock() { viewModelScope.launch {
        c.settingsRepository.disableLock()
        say(R.string.lock_disabled)
    } }

    fun setBiometric(on: Boolean) { viewModelScope.launch { c.settingsRepository.setBiometric(on) } }

    fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) { viewModelScope.launch {
        c.settingsRepository.setDailyReminder(enabled, hour, minute)
        c.reminderScheduler.setDaily(enabled, hour, minute)
    } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SettingsViewModel(application, appContainer())
            }
        }
    }
}
