package com.timebox.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timebox.android.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timebox")

/** Connection details and the theme override, the only state the app keeps locally. */
data class AppSettings(
    val baseUrl: String,
    val apiKey: String,
    /** null follows the system setting. */
    val darkTheme: Boolean?,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val darkThemeSet = booleanPreferencesKey("dark_theme_set")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.baseUrl] ?: BuildConfig.DEFAULT_BASE_URL,
            apiKey = prefs[Keys.apiKey].orEmpty(),
            darkTheme = if (prefs[Keys.darkThemeSet] == true) prefs[Keys.darkTheme] else null,
        )
    }

    suspend fun setConnection(baseUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl.trim()
            prefs[Keys.apiKey] = apiKey.trim()
        }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.darkTheme] = dark
            prefs[Keys.darkThemeSet] = true
        }
    }
}
