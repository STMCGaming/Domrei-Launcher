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

    fun deleteSkin(context: Context, account: LauncherAccount) {
        for (key in getSkinCandidates(account)) {
            runCatching {
                val file = getSkinFile(context, key)
                if (file.exists()) file.delete()
            }
        }
    }

    fun getCapeFile(context: Context, key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(getSkinsDirectory(context), "${safeKey}_cape.png")
    }

    fun getCapeCheckedFile(context: Context, key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(getSkinsDirectory(context), "${safeKey}_cape.checked")
    }

    fun hasCape(context: Context, account: LauncherAccount): Boolean {
        if (!isMicrosoftAccount(account)) return false
        return getSkinCandidates(account).any { key ->
            val file = getCapeFile(context, key)
            file.isFile && file.length() > 0
        }
    }

    fun hasCapeChecked(context: Context, account: LauncherAccount): Boolean {
        if (!isMicrosoftAccount(account)) return true
        return getSkinCandidates(account).any { key ->
            val capeFile = getCapeFile(context, key)
            val checkedFile = getCapeCheckedFile(context, key)
            (capeFile.isFile && capeFile.length() > 0) || checkedFile.exists()
        }
    }

    fun markCapeChecked(context: Context, account: LauncherAccount) {
        for (key in getSkinCandidates(account)) {
            runCatching {
                val file = getCapeCheckedFile(context, key)
                file.parentFile?.mkdirs()
                if (!file.exists()) file.createNewFile()
            }
        }
    }

    fun markCapeCheckedForKey(context: Context, key: String) {
        runCatching {
            val file = getCapeCheckedFile(context, key)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
        }
    }

    fun saveCapeBitmap(context: Context, account: LauncherAccount, bitmap: Bitmap) {
        if (!isMicrosoftAccount(account)) return
        val normalized = SkinUtils.normalizeCape(bitmap)
        val bytes = ByteArrayOutputStream().use { bos ->
            normalized.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
        for (key in getSkinCandidates(account)) {
            runCatching {
                val file = getCapeFile(context, key)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                markCapeCheckedForKey(context, key)
            }
        }
    }

    fun saveCapeBitmapForKey(context: Context, key: String, bitmap: Bitmap) {
        val normalized = SkinUtils.normalizeCape(bitmap)
        val file = getCapeFile(context, key)
        file.parentFile?.mkdirs()
        runCatching {
            FileOutputStream(file).use { out ->
                normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            markCapeCheckedForKey(context, key)
        }
    }

    fun deleteCape(context: Context, account: LauncherAccount, markChecked: Boolean = true) {
        for (key in getSkinCandidates(account)) {
            runCatching {
                val file = getCapeFile(context, key)
                if (file.exists()) file.delete()
                if (markChecked) {
                    markCapeCheckedForKey(context, key)
                } else {
                    val checkedFile = getCapeCheckedFile(context, key)
                    if (checkedFile.exists()) checkedFile.delete()
                }
            }
        }
    }

    suspend fun downloadAndSaveCape(context: Context, key: String, url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val secureUrl = if (url.startsWith("http://", ignoreCase = true)) {
                url.replaceFirst("http://", "https://", ignoreCase = true)
            } else {
                url
            }
            val raw = SkinUtils.fromUrl(secureUrl)
            val normalized = SkinUtils.normalizeCape(raw)
            saveCapeBitmapForKey(context, key, normalized)
            normalized
        }.onFailure { Log.w(TAG, "Failed to download and save cape for $key from $url", it) }
            .getOrNull()
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

        if (!force && hasSkin(context, account) && hasCapeChecked(context, account)) {
            return@withContext loadSkin(context, account)
        }

        val uuid = account.uuid?.trim().orEmpty()
        val cleanUuid = uuid.replace("-", "")
        if (cleanUuid.length != 32) {
            Log.w(TAG, "Cannot sync skin: Invalid Minecraft UUID length '${account.uuid}'")
            return@withContext null
        }

        var downloaded: Bitmap? = null
        var downloadedCape: Bitmap? = null

        // 1. Fetch official skin & cape by UUID from Mojang API
        val mojangRes = MojangSkinApi.fetchByUuid(cleanUuid)
        val profile = mojangRes.getOrNull()
        if (profile?.skinBitmap != null) {
            downloaded = profile.skinBitmap
        } else if (!profile?.skinUrl.isNullOrEmpty()) {
            downloaded = runCatching { SkinUtils.fromUrl(profile!!.skinUrl!!) }.getOrNull()
        }

        if (profile?.capeBitmap != null) {
            downloadedCape = profile.capeBitmap
        } else if (!profile?.capeUrl.isNullOrEmpty()) {
            downloadedCape = runCatching { SkinUtils.fromUrl(profile!!.capeUrl!!) }.getOrNull()
        }

        // 2. Fallback: Crafatar CDN by official UUID (resilient to Mojang session server 429 rate limits)
        if (downloaded == null) {
            downloaded = runCatching {
                SkinUtils.fromUrl("https://crafatar.com/skins/$cleanUuid")
            }.getOrNull()
        }

        if (downloadedCape == null && profile?.capeUrl != null) {
            downloadedCape = runCatching {
                SkinUtils.fromUrl("https://crafatar.com/capes/$cleanUuid")
            }.getOrNull()
        }

        if (downloadedCape != null) {
            saveCapeBitmap(context, account, downloadedCape)
        } else if (profile != null && profile.capeUrl == null) {
            deleteCape(context, account)
        }

        if (downloaded != null) {
            val normalized = SkinUtils.normalizeSkin(downloaded)
            saveSkinBitmap(context, account, normalized)
            return@withContext normalized
        }

        null
    }

    /**
     * Loads the cape bitmap for [account].
     * ONLY Microsoft accounts with an official equipped cape return a cape.
     * Default/offline accounts and accounts without an equipped cape return null (no cape).
     */
    fun loadCape(context: Context, account: LauncherAccount?): Bitmap? {
        if (!isMicrosoftAccount(account)) return null
        for (key in getSkinCandidates(account!!)) {
            val file = getCapeFile(context, key)
            if (file.isFile && file.length() > 0) {
                val loaded = runCatching {
                    file.inputStream().use { stream ->
                        val raw = SkinUtils.fromStream(stream)
                        SkinUtils.normalizeCape(raw)
                    }
                }.getOrNull()
                if (loaded != null) return loaded
            }
        }
        return null
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
