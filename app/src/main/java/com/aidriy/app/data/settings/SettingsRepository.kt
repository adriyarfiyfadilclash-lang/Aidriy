package com.aidriy.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aidriy.app.domain.model.AiLanguage
import com.aidriy.app.domain.model.AiProviderId
import com.aidriy.app.domain.model.AnswerStyle
import com.aidriy.app.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) { SMALL(0.9f), NORMAL(1.0f), LARGE(1.15f) }

/** Non-sensitive preferences. API keys live in [SecretStore], never here. */
data class AppSettings(
    val profileName: String = "",
    val profilePhotoPath: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val defaultProvider: AiProviderId = AiProviderId.OPENAI,
    val models: Map<AiProviderId, String> = AiProviderId.entries.associateWith { it.defaultModel },
    val answerStyle: AnswerStyle = AnswerStyle.BALANCED,
    val aiLanguage: AiLanguage = AiLanguage.INDONESIAN,
    val saveChatHistory: Boolean = true,
    val showSuggestions: Boolean = true,
    /** Off by default: notes are only shared with an AI provider when the user turns this on. */
    val useNotesData: Boolean = false,
    val noteSort: SortOrder = SortOrder.UPDATED,
    val autosave: Boolean = true,
    val appLockEnabled: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 21,
    val dailyReminderMinute: Int = 0,
) {
    fun modelFor(provider: AiProviderId): String = models[provider]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val profileName = stringPreferencesKey("profile_name")
        val profilePhoto = stringPreferencesKey("profile_photo")
        val theme = stringPreferencesKey("theme_mode")
        val textSize = stringPreferencesKey("text_size")
        val provider = stringPreferencesKey("ai_provider")
        fun model(p: AiProviderId) = stringPreferencesKey("ai_model_${p.key}")
        val style = stringPreferencesKey("ai_style")
        val language = stringPreferencesKey("ai_language")
        val saveHistory = booleanPreferencesKey("ai_save_history")
        val suggestions = booleanPreferencesKey("ai_suggestions")
        val useNotes = booleanPreferencesKey("ai_use_notes")
        val noteSort = stringPreferencesKey("note_sort")
        val autosave = booleanPreferencesKey("note_autosave")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val pinHash = stringPreferencesKey("lock_pin_hash")
        val pinSalt = stringPreferencesKey("lock_pin_salt")
        val biometric = booleanPreferencesKey("lock_biometric")
        val dailyEnabled = booleanPreferencesKey("daily_enabled")
        val dailyHour = intPreferencesKey("daily_hour")
        val dailyMinute = intPreferencesKey("daily_minute")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                profileName = p[Keys.profileName].orEmpty(),
                profilePhotoPath = p[Keys.profilePhoto],
                themeMode = p[Keys.theme]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.SYSTEM,
                textSize = p[Keys.textSize]?.let { v -> TextSize.entries.firstOrNull { it.name == v } } ?: TextSize.NORMAL,
                defaultProvider = AiProviderId.fromKey(p[Keys.provider]),
                models = AiProviderId.entries.associateWith { p[Keys.model(it)] ?: it.defaultModel },
                answerStyle = AnswerStyle.fromKey(p[Keys.style]),
                aiLanguage = AiLanguage.fromKey(p[Keys.language]),
                saveChatHistory = p[Keys.saveHistory] ?: true,
                showSuggestions = p[Keys.suggestions] ?: true,
                useNotesData = p[Keys.useNotes] ?: false,
                noteSort = p[Keys.noteSort]?.let { v -> SortOrder.entries.firstOrNull { it.name == v } } ?: SortOrder.UPDATED,
                autosave = p[Keys.autosave] ?: true,
                appLockEnabled = p[Keys.lockEnabled] ?: false,
                pinHash = p[Keys.pinHash],
                pinSalt = p[Keys.pinSalt],
                biometricEnabled = p[Keys.biometric] ?: false,
                dailyReminderEnabled = p[Keys.dailyEnabled] ?: false,
                dailyReminderHour = p[Keys.dailyHour] ?: 21,
                dailyReminderMinute = p[Keys.dailyMinute] ?: 0,
            )
        }

    suspend fun setProfileName(value: String) = store.edit { it[Keys.profileName] = value.trim().take(40) }
    suspend fun setProfilePhoto(path: String?) = store.edit {
        if (path == null) {
            it.remove(Keys.profilePhoto)
        } else {
            it[Keys.profilePhoto] = path
        }
    }
    suspend fun setThemeMode(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setTextSize(value: TextSize) = store.edit { it[Keys.textSize] = value.name }
    suspend fun setDefaultProvider(value: AiProviderId) = store.edit { it[Keys.provider] = value.key }
    suspend fun setModel(provider: AiProviderId, model: String) = store.edit {
        val clean = model.trim()
        if (clean.isEmpty()) {
            it.remove(Keys.model(provider))
        } else {
            it[Keys.model(provider)] = clean
        }
    }
    suspend fun setAnswerStyle(value: AnswerStyle) = store.edit { it[Keys.style] = value.key }
    suspend fun setAiLanguage(value: AiLanguage) = store.edit { it[Keys.language] = value.key }
    suspend fun setSaveChatHistory(value: Boolean) = store.edit { it[Keys.saveHistory] = value }
    suspend fun setShowSuggestions(value: Boolean) = store.edit { it[Keys.suggestions] = value }
    suspend fun setUseNotesData(value: Boolean) = store.edit { it[Keys.useNotes] = value }
    suspend fun setNoteSort(value: SortOrder) = store.edit { it[Keys.noteSort] = value.name }
    suspend fun setAutosave(value: Boolean) = store.edit { it[Keys.autosave] = value }

    suspend fun setPin(hash: String, salt: String) = store.edit {
        it[Keys.pinHash] = hash
        it[Keys.pinSalt] = salt
        it[Keys.lockEnabled] = true
    }
    suspend fun disableLock() = store.edit {
        it.remove(Keys.pinHash)
        it.remove(Keys.pinSalt)
        it[Keys.lockEnabled] = false
        it[Keys.biometric] = false
    }
    suspend fun setBiometric(value: Boolean) = store.edit { it[Keys.biometric] = value }

    suspend fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) = store.edit {
        it[Keys.dailyEnabled] = enabled
        it[Keys.dailyHour] = hour.coerceIn(0, 23)
        it[Keys.dailyMinute] = minute.coerceIn(0, 59)
    }
}
