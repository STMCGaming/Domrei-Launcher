package com.domrei.launcher

import android.content.Context

/**
 * Persists Display and Resolution settings (e.g. resolution scale / resize percentage)
 * in SharedPreferences across app restarts.
 */
class DisplayPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadResolutionScale(): Int {
        return runCatching {
            prefs.getInt(KEY_RES_SCALE, DEFAULT_RES_SCALE)
        }.getOrDefault(DEFAULT_RES_SCALE).coerceIn(0, 100)
    }

    fun saveResolutionScale(scale: Int) {
        prefs.edit()
            .putInt(KEY_RES_SCALE, scale.coerceIn(0, 100))
            .apply()
    }

    fun resetResolutionScale() {
        saveResolutionScale(DEFAULT_RES_SCALE)
    }

    companion object {
        private const val PREFS_NAME = "domrei_display_preferences"
        private const val KEY_RES_SCALE = "resolution_scale"
        const val DEFAULT_RES_SCALE = 100
    }
}
