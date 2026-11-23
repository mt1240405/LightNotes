package com.lightnotes.app.data

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREF_NAME = "lightnotes_app_prefs"
    private const val KEY_ADVANCED_MODE = "key_advanced_mode"
    private const val KEY_KATEX_ENGINE = "key_katex_engine"
    private const val KEY_DYNAMIC_COLORS = "key_dynamic_colors"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isDynamicColors(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DYNAMIC_COLORS, false)
    }

    fun setDynamicColors(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply()
    }

    fun isAdvancedMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ADVANCED_MODE, false)
    }

    fun setAdvancedMode(context: Context, isAdvanced: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ADVANCED_MODE, isAdvanced).apply()
    }

    fun isKaTeXEngine(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_KATEX_ENGINE, false)
    }

    fun setKaTeXEngine(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_KATEX_ENGINE, enabled).apply()
    }
}
