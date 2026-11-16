package com.domrei.launcher

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.domrei.skinview.DefaultSkin
import com.domrei.skinview.MojangSkinApi
import com.domrei.skinview.SkinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object AccountSkinManager {
    private const val TAG = "AccountSkinManager"
    private const val SKINS_DIR = "skins"
    private const val DEFAULT_SKIN_ASSET = "skin.png"

    fun isMicrosoftAccount(account: LauncherAccount?): Boolean {
        return account != null &&
            account.typeRes == R.string.account_type_microsoft &&
            !account.uuid.isNullOrBlank()
    }

    fun getSkinsDirectory(context: Context): File {
        val dir = File(context.filesDir, SKINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSkinFile(context: Context, key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(getSkinsDirectory(context), "${safeKey}.png")
    }

    fun getSkinCandidates(account: LauncherAccount): List<String> {
        if (!isMicrosoftAccount(account)) return emptyList()
        val uuid = account.uuid?.trim().orEmpty()
        val cleanUuid = uuid.replace("-", "")
        return listOf(uuid, cleanUuid, account.id).distinct()
    }

    fun hasSkin(context: Context, account: LauncherAccount): Boolean {
        if (!isMicrosoftAccount(account)) return false
        return getSkinCandidates(account).any { key ->
            val file = getSkinFile(context, key)
            file.isFile && file.length() > 0
        }
    }

    fun saveSkinBitmap(context: Context, account: LauncherAccount, bitmap: Bitmap) {
        if (!isMicrosoftAccount(account)) return
        val normalized = SkinUtils.normalizeSkin(bitmap)
        val bytes = ByteArrayOutputStream().use { bos ->
            normalized.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
        for (key in getSkinCandidates(account)) {
            runCatching {
                val file = getSkinFile(context, key)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
        }
    }

    fun saveSkinBitmapForKey(context: Context, key: String, bitmap: Bitmap) {
        val normalized = SkinUtils.normalizeSkin(bitmap)
        val file = getSkinFile(context, key)
        file.parentFile?.mkdirs()
        runCatching {
            FileOutputStream(file).use { out ->
                normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    suspend fun downloadAndSaveSkin(context: Context, key: String, url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val secureUrl = if (url.startsWith("http://", ignoreCase = true)) {
                url.replaceFirst("http://", "https://", ignoreCase = true)
            } else {
                url
            }
            val raw = SkinUtils.fromUrl(secureUrl)
            val normalized = SkinUtils.normalizeSkin(raw)
            saveSkinBitmapForKey(context, key, normalized)
            normalized
        }.onFailure { Log.w(TAG, "Failed to download and save skin for $key from $url", it) }
            .getOrNull()
    }

    /**
     * Dynamic skin syncing is viable ONLY for Microsoft accounts using the official Minecraft API and UUID.
     * Offline accounts always use the default Minecraft skin.
     */
    suspend fun syncAccountSkin(
        context: Context,
        account: LauncherAccount,
        force: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!isMicrosoftAccount(account)) {
            return@withContext null
        }

        if (!force && hasSkin(context, account)) {
            return@withContext loadSkin(context, account)
        }

        val uuid = account.uuid?.trim().orEmpty()
        val cleanUuid = uuid.replace("-", "")
        if (cleanUuid.length != 32) {
            Log.w(TAG, "Cannot sync skin: Invalid Minecraft UUID length '${account.uuid}'")
            return@withContext null
        }

        var downloaded: Bitmap? = null

        // 1. Fetch official skin by UUID from Mojang API
        val mojangRes = MojangSkinApi.fetchByUuid(cleanUuid)
        val profile = mojangRes.getOrNull()
        if (profile?.skinBitmap != null) {
            downloaded = profile.skinBitmap
        } else if (!profile?.skinUrl.isNullOrEmpty()) {
            downloaded = runCatching { SkinUtils.fromUrl(profile!!.skinUrl!!) }.getOrNull()
        }

        // 2. Fallback: Crafatar CDN by official UUID (resilient to Mojang session server 429 rate limits)
        if (downloaded == null) {
            downloaded = runCatching {
                SkinUtils.fromUrl("https://crafatar.com/skins/$cleanUuid")
            }.getOrNull()
        }

        if (downloaded != null) {
            val normalized = SkinUtils.normalizeSkin(downloaded)
            saveSkinBitmap(context, account, normalized)
            return@withContext normalized
        }

        null
    }

    /**
     * Loads the skin bitmap for [account].
     * For Microsoft accounts, returns the dynamically cached official Minecraft skin by UUID.
     * For offline accounts, returns the bundled default Minecraft skin.
     */
    fun loadSkin(context: Context, account: LauncherAccount?): Bitmap {
        if (isMicrosoftAccount(account)) {
            for (key in getSkinCandidates(account!!)) {
                val file = getSkinFile(context, key)
                if (file.isFile && file.length() > 0) {
                    val loaded = runCatching {
                        file.inputStream().use { stream ->
                            val raw = SkinUtils.fromStream(stream)
                            SkinUtils.normalizeSkin(raw)
                        }
                    }.getOrNull()
                    if (loaded != null) return loaded
                }
            }
        }
        val defaultSkin = runCatching { SkinUtils.fromAsset(context, DEFAULT_SKIN_ASSET) }
            .mapCatching { SkinUtils.normalizeSkin(it) }
            .getOrElse { DefaultSkin.create(slim = false) }
        return SkinUtils.toClassicModel(defaultSkin)
    }
}
