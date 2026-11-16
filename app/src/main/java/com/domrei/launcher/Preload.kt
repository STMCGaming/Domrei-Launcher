package com.domrei.launcher

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.domrei.launcher.minecraft.MinecraftInstanceManager
import com.domrei.launcher.minecraft.OfficialMinecraftApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the launcher needs to show its first frame, read once while the splash screen is
 * visible so the UI never re-reads storage or re-downloads the version list on navigation.
 */
data class PreloadedContent(
    val instances: List<LauncherInstance>,
    val selectedInstanceId: String,
    val accounts: List<LauncherAccount>,
    val selectedAccountId: String,
    val backgroundConfig: BackgroundConfig,
    val accentColor: Color,
)

/** Loads persistent launcher content and warms the network caches behind the splash screen. */
object LauncherPreloader {

    /** Fast, always-awaited part: instances, accounts, background and accent color from disk. */
    suspend fun loadLocal(context: Context): PreloadedContent = withContext(Dispatchers.IO) {
        val appearance = AppearancePreferences(context)
        val accountStore = AccountStore(context)
        val instanceManager = MinecraftInstanceManager(context)
        val accounts = accountStore.loadAccounts()
        val storedSelection = accountStore.loadSelectedAccountId()
        val instances = instanceManager.loadInstances()
        val storedInstanceSelection = instanceManager.loadSelectedInstanceId()

        PreloadedContent(
            instances = instances,
            selectedInstanceId = storedInstanceSelection
                .takeIf { saved -> instances.any { it.id == saved } }
                ?: instances.firstOrNull()?.id.orEmpty(),
            accounts = accounts,
            selectedAccountId = storedSelection
                .takeIf { saved -> accounts.any { it.id == saved } }
                ?: accounts.firstOrNull()?.id.orEmpty(),
            backgroundConfig = appearance.loadBackgroundConfig(),
            accentColor = appearance.loadAccentColor(),
        )
    }

    /**
     * Warms the in-memory Mojang/Fabric caches so opening "Add Instance" is instant instead of
     * showing a network spinner. Failures (offline, timeouts) are ignored on purpose.
     */
    suspend fun preloadNetwork() = withContext(Dispatchers.IO) {
        runCatching { OfficialMinecraftApi.fetchVersionManifest() }
        runCatching { OfficialMinecraftApi.fetchFabricGameVersions() }
        Unit
    }
}
