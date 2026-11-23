package com.lightnotes.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lightnotes_preferences")

class PreferencesRepository private constructor(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0=system, 1=light, 2=dark
        val KEY_EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size") // 12f - 22f
        val KEY_EDITOR_LINE_SPACING = floatPreferencesKey("editor_line_spacing") // e.g. 1.45f
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: 0
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    val editorFontSizeFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_EDITOR_FONT_SIZE] ?: 15f
    }

    suspend fun setEditorFontSize(size: Float) {
        context.dataStore.edit { it[KEY_EDITOR_FONT_SIZE] = size.coerceIn(12f, 22f) }
    }

    val editorLineSpacingFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_EDITOR_LINE_SPACING] ?: 1.45f
    }

    suspend fun setEditorLineSpacing(spacing: Float) {
        context.dataStore.edit { it[KEY_EDITOR_LINE_SPACING] = spacing }
    }

    val sortOrderFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORT_ORDER] ?: "updated_desc"
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { it[KEY_SORT_ORDER] = order }
    }

    val biometricLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_LOCK_ENABLED] ?: false
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK_ENABLED] = enabled }
    }
}
