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
        val KEY_IS_ADVANCED_MODE = booleanPreferencesKey("is_advanced_mode")
        val KEY_MATH_ENGINE = stringPreferencesKey("math_engine") // "jlatexmath" or "katex"
        val KEY_ENABLE_MERMAID = booleanPreferencesKey("enable_mermaid")
        val KEY_AUTOSAVE_INTERVAL_SEC = intPreferencesKey("autosave_interval_sec")
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        
        // PDF Export defaults
        val KEY_PDF_PAGE_SIZE = stringPreferencesKey("pdf_page_size")
        val KEY_PDF_CUSTOM_WIDTH_MM = floatPreferencesKey("pdf_custom_width_mm")
        val KEY_PDF_SCALE_PERCENT = intPreferencesKey("pdf_scale_percent")
        val KEY_PDF_MARGIN_TOP_MM = floatPreferencesKey("pdf_margin_top_mm")
        val KEY_PDF_MARGIN_BOTTOM_MM = floatPreferencesKey("pdf_margin_bottom_mm")
        val KEY_PDF_MARGIN_LEFT_MM = floatPreferencesKey("pdf_margin_left_mm")
        val KEY_PDF_MARGIN_RIGHT_MM = floatPreferencesKey("pdf_margin_right_mm")
        val KEY_PDF_SHOW_HEADER = booleanPreferencesKey("pdf_show_header")
        val KEY_PDF_SHOW_FOOTER = booleanPreferencesKey("pdf_show_footer")
        val KEY_PDF_CUSTOM_CSS = stringPreferencesKey("pdf_custom_css")
        val KEY_PDF_PRESET = stringPreferencesKey("pdf_preset")

        // Auto backup & Security
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        val KEY_AUTO_BACKUP_FREQ = stringPreferencesKey("auto_backup_freq")
        val KEY_BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val KEY_INACTIVITY_LOCK_TIMEOUT_MIN = intPreferencesKey("inactivity_lock_timeout_min")

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

    val isAdvancedModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_ADVANCED_MODE] ?: false
    }

    suspend fun setAdvancedMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IS_ADVANCED_MODE] = enabled }
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

    val mathEngineFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MATH_ENGINE] ?: "jlatexmath"
    }

    suspend fun setMathEngine(engine: String) {
        context.dataStore.edit { it[KEY_MATH_ENGINE] = engine }
    }

    val enableMermaidFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_MERMAID] ?: true
    }

    suspend fun setEnableMermaid(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_MERMAID] = enabled }
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

    val autoBackupEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_ENABLED] ?: false
    }

    val autoBackupUriFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP_URI] ?: ""
    }

    suspend fun setAutoBackupConfig(enabled: Boolean, uri: String, freq: String) {
        context.dataStore.edit {
            it[KEY_AUTO_BACKUP_ENABLED] = enabled
            it[KEY_AUTO_BACKUP_URI] = uri
            it[KEY_AUTO_BACKUP_FREQ] = freq
        }
    }

    val pdfCustomCssFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PDF_CUSTOM_CSS] ?: ""
    }

    suspend fun setPdfCustomCss(css: String) {
        context.dataStore.edit { it[KEY_PDF_CUSTOM_CSS] = css }
    }

    val pdfPresetFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PDF_PRESET] ?: "LaTeX Academic"
    }

    suspend fun setPdfPreset(preset: String) {
        context.dataStore.edit { it[KEY_PDF_PRESET] = preset }
    }
}
