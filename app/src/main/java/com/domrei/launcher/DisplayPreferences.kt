package com.domrei.launcher

import android.content.Context

/**
 * Persists Display and Renderer settings in SharedPreferences across app restarts:
 * resolution scale, refresh-rate request, system Vulkan driver and game surface mode.
 */
class DisplayPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadResolutionScale(): Int {
        return runCatching {
            prefs.getInt(KEY_RES_SCALE, DEFAULT_RES_SCALE)
        }.getOrDefault(DEFAULT_RES_SCALE).coerceIn(25, 100)
    }

    fun saveResolutionScale(scale: Int) {
        prefs.edit()
            .putInt(KEY_RES_SCALE, scale.coerceIn(25, 100))
            .apply()
    }

    fun resetResolutionScale() {
        saveResolutionScale(DEFAULT_RES_SCALE)
    }

    fun loadRequestMaxRefreshRate(): Boolean = loadBoolean(KEY_MAX_REFRESH_RATE, false)

    fun saveRequestMaxRefreshRate(value: Boolean) = saveBoolean(KEY_MAX_REFRESH_RATE, value)

    fun loadUseSystemVulkanDriver(): Boolean = loadBoolean(KEY_SYSTEM_VULKAN, false)

    fun saveUseSystemVulkanDriver(value: Boolean) = saveBoolean(KEY_SYSTEM_VULKAN, value)

    fun loadUseSurfaceViewRendering(): Boolean = loadBoolean(KEY_SURFACE_VIEW, true)

    fun saveUseSurfaceViewRendering(value: Boolean) = saveBoolean(KEY_SURFACE_VIEW, value)

    private fun loadBoolean(key: String, defaultValue: Boolean): Boolean {
        return runCatching { prefs.getBoolean(key, defaultValue) }.getOrDefault(defaultValue)
    }

    private fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "domrei_display_preferences"
        private const val KEY_RES_SCALE = "resolution_scale"
        private const val KEY_MAX_REFRESH_RATE = "request_max_refresh_rate"
        private const val KEY_SYSTEM_VULKAN = "use_system_vulkan_driver"
        private const val KEY_SURFACE_VIEW = "use_surface_view_rendering"
        const val DEFAULT_RES_SCALE = 100
    }
}
