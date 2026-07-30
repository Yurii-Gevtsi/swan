package com.gysignalstudio.blackswan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "black_swan_preferences")

class LocalDataStore(private val context: Context) {

    companion object {
        private val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        private val THEME_SELECTION = stringPreferencesKey("theme_selection")
        private val ACCEPTED_DISCLAIMER = booleanPreferencesKey("accepted_disclaimer")
        private val ACCEPTED_DISCLAIMER_VERSION = stringPreferencesKey("accepted_disclaimer_version")
        private val LAST_DATA_VERSION = stringPreferencesKey("last_data_version")
        private val LAST_SUCCESSFUL_SYNC = longPreferencesKey("last_successful_sync")
        private val ADS_DISABLED = booleanPreferencesKey("ads_disabled")
        private val MAP_LAST_VIEWPORT = stringPreferencesKey("map_last_viewport")
        private val APP_SESSION_COUNT = intPreferencesKey("app_session_count")
        private val RATE_APP_LAST_SHOWN_SESSION = intPreferencesKey("rate_app_last_shown_session")
        private val RATE_APP_COMPLETED = booleanPreferencesKey("rate_app_completed")
    }

    val selectedLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_LANGUAGE] ?: "en"
    }

    suspend fun setSelectedLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_LANGUAGE] = language
        }
    }

    val themeSelection: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_SELECTION] ?: "dark" // Default visual theme for Black Swan: War Impact Map.
    }

    suspend fun setThemeSelection(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_SELECTION] = theme
        }
    }

    val acceptedDisclaimer: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ACCEPTED_DISCLAIMER] ?: false
    }

    suspend fun setAcceptedDisclaimer(accepted: Boolean, version: String = "1.0") {
        context.dataStore.edit { preferences ->
            preferences[ACCEPTED_DISCLAIMER] = accepted
            preferences[ACCEPTED_DISCLAIMER_VERSION] = version
        }
    }

    val lastDataVersion: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LAST_DATA_VERSION] ?: "2026-07-09"
    }

    suspend fun setLastDataVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_DATA_VERSION] = version
        }
    }

    val lastSuccessfulSync: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SUCCESSFUL_SYNC] ?: 0L
    }

    suspend fun setLastSuccessfulSync(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SUCCESSFUL_SYNC] = timestamp
        }
    }

    val adsDisabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ADS_DISABLED] ?: false
    }

    suspend fun setAdsDisabled(disabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ADS_DISABLED] = disabled
        }
    }

    val mapLastViewport: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[MAP_LAST_VIEWPORT] ?: ""
    }

    suspend fun setMapLastViewport(viewport: String) {
        context.dataStore.edit { preferences ->
            preferences[MAP_LAST_VIEWPORT] = viewport
        }
    }

    suspend fun incrementSessionCount(): Int {
        var updatedCount = 1
        context.dataStore.edit { preferences ->
            updatedCount = (preferences[APP_SESSION_COUNT] ?: 0) + 1
            preferences[APP_SESSION_COUNT] = updatedCount
        }
        return updatedCount
    }

    val appSessionCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[APP_SESSION_COUNT] ?: 0
    }

    val rateAppLastShownSession: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[RATE_APP_LAST_SHOWN_SESSION] ?: 0
    }

    suspend fun setRateAppLastShownSession(sessionCount: Int) {
        context.dataStore.edit { preferences ->
            preferences[RATE_APP_LAST_SHOWN_SESSION] = sessionCount
        }
    }

    val rateAppCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RATE_APP_COMPLETED] ?: false
    }

    suspend fun setRateAppCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RATE_APP_COMPLETED] = completed
        }
    }
}
