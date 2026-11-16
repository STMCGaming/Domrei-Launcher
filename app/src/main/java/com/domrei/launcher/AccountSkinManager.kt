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

    fun getSkinsDirectory(context: Context): File {
        val dir = File(context.filesDir, SKINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSkinFile(context: Context, key: String): File {
        val safeKey = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(getSkinsDirectory(context), "${safeKey}.png")
    }

    fun getSkinCandidates(account: LauncherAccount): List<String> = buildList {
        add(account.id)
        val uuid = account.uuid?.trim().orEmpty()
        if (uuid.isNotEmpty()) {
            add(uuid)
            add(uuid.replace("-", ""))
        }
        val username = account.username.trim()
        if (username.isNotEmpty()) {
            add(username)
            add(username.lowercase())
        }
    }.distinct()

    fun hasSkin(context: Context, account: LauncherAccount): Boolean {
        return getSkinCandidates(account).any { key ->
            val file = getSkinFile(context, key)
            file.isFile && file.length() > 0
        }
    }

    fun saveSkinBitmap(context: Context, account: LauncherAccount, bitmap: Bitmap) {
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

    suspend fun syncAccountSkin(
        context: Context,
        account: LauncherAccount,
        force: Boolean = false,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!force && hasSkin(context, account)) {
            return@withContext loadSkin(context, account)
        }

        var downloaded: Bitmap? = null

        // 1. If we have a UUID (Microsoft account or official profile), query Mojang API
        val uuid = account.uuid?.trim().orEmpty()
        if (uuid.isNotEmpty()) {
            val mojangRes = MojangSkinApi.fetchByUuid(uuid)
            val profile = mojangRes.getOrNull()
            if (profile?.skinBitmap != null) {
                downloaded = profile.skinBitmap
            } else if (!profile?.skinUrl.isNullOrEmpty()) {
                downloaded = runCatching { SkinUtils.fromUrl(profile!!.skinUrl!!) }.getOrNull()
            }
        }

        // 2. Query Mojang API by username if not Steve/Alex
        val username = account.username.trim()
        if (downloaded == null && username.isNotEmpty() &&
            !username.equals("Steve", ignoreCase = true) &&
            !username.equals("Alex", ignoreCase = true)
        ) {
            val mojangRes = MojangSkinApi.fetchByUsername(username)
            val profile = mojangRes.getOrNull()
            if (profile?.skinBitmap != null) {
                downloaded = profile.skinBitmap
            } else if (!profile?.skinUrl.isNullOrEmpty()) {
                downloaded = runCatching { SkinUtils.fromUrl(profile!!.skinUrl!!) }.getOrNull()
            }
        }

        // 3. Fallback: Crafatar CDN by UUID
        if (downloaded == null && uuid.isNotEmpty()) {
            val cleanUuid = uuid.replace("-", "").trim()
            downloaded = runCatching {
                SkinUtils.fromUrl("https://crafatar.com/skins/$cleanUuid")
            }.getOrNull()
        }

        // 4. Fallback: Minotar CDN by username
        if (downloaded == null && username.isNotEmpty() &&
            !username.equals("Steve", ignoreCase = true) &&
            !username.equals("Alex", ignoreCase = true)
        ) {
            downloaded = runCatching {
                SkinUtils.fromUrl("https://minotar.net/skin/$username")
            }.getOrNull()
        }

        if (downloaded != null) {
            val normalized = SkinUtils.normalizeSkin(downloaded)
            saveSkinBitmap(context, account, normalized)
            return@withContext normalized
        }

        null
    }

    fun loadSkin(context: Context, account: LauncherAccount?): Bitmap {
        if (account != null) {
            for (key in getSkinCandidates(account)) {
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
        return runCatching { SkinUtils.fromAsset(context, DEFAULT_SKIN_ASSET) }
            .mapCatching { SkinUtils.normalizeSkin(it) }
            .getOrElse { DefaultSkin.create() }
    }
}
