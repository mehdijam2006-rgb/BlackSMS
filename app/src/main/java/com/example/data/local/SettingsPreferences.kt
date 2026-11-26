package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.SmartFilterLevel
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.smartSmsDataStore: DataStore<Preferences> by preferencesDataStore(name = "black_sms_prefs")

data class AppSettings(
    val smartFilterLevel: SmartFilterLevel = SmartFilterLevel.NORMAL,
    val appLanguage: AppLanguage = AppLanguage.FA,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val notificationsEnabled: Boolean = true,
    val deliveryReportsEnabled: Boolean = true,
    val lastSeenBlockedCount: Int = 0,
    val hasSeededInitialData: Boolean = false
)

class SettingsPreferences(private val context: Context) {

    private object Keys {
        val SMART_LEVEL = stringPreferencesKey("smart_filter_level")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DELIVERY_REPORTS_ENABLED = booleanPreferencesKey("delivery_reports_enabled")
        val LAST_SEEN_BLOCKED_COUNT = intPreferencesKey("last_seen_blocked_count")
        val HAS_SEEDED = booleanPreferencesKey("has_seeded_initial_data")
    }

    val settingsFlow: Flow<AppSettings> = context.smartSmsDataStore.data.map { prefs ->
        val levelName = prefs[Keys.SMART_LEVEL] ?: SmartFilterLevel.NORMAL.name
        val level = SmartFilterLevel.entries.firstOrNull { it.name == levelName } ?: SmartFilterLevel.NORMAL
        val langName = prefs[Keys.APP_LANGUAGE] ?: AppLanguage.FA.name
        val lang = AppLanguage.entries.firstOrNull { it.name == langName } ?: AppLanguage.FA
        val themeName = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        val theme = AppThemeMode.entries.firstOrNull { it.name == themeName } ?: AppThemeMode.DARK
        AppSettings(
            smartFilterLevel = level,
            appLanguage = lang,
            themeMode = theme,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            deliveryReportsEnabled = prefs[Keys.DELIVERY_REPORTS_ENABLED] ?: true,
            lastSeenBlockedCount = prefs[Keys.LAST_SEEN_BLOCKED_COUNT] ?: 0,
            hasSeededInitialData = prefs[Keys.HAS_SEEDED] ?: false
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setSmartFilterLevel(level: SmartFilterLevel) {
        context.smartSmsDataStore.edit { it[Keys.SMART_LEVEL] = level.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.smartSmsDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.smartSmsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.smartSmsDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setDeliveryReportsEnabled(enabled: Boolean) {
        context.smartSmsDataStore.edit { it[Keys.DELIVERY_REPORTS_ENABLED] = enabled }
    }

    suspend fun setLastSeenBlockedCount(count: Int) {
        context.smartSmsDataStore.edit { it[Keys.LAST_SEEN_BLOCKED_COUNT] = count.coerceAtLeast(0) }
    }

    suspend fun markSeeded() {
        context.smartSmsDataStore.edit { it[Keys.HAS_SEEDED] = true }
    }
}
