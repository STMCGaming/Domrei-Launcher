package com.domrei.launcher

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.domrei.skinview.DefaultSkin
import com.domrei.skinview.SkinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AccountSkinManager {
    private const val SKINS_DIR = "skins"
    private const val DEFAULT_SKIN_ASSET = "skin.png"

    fun getSkinsDirectory(context: Context): File {
        val dir = File(context.filesDir, SKINS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSkinFile(context: Context, key: String): File {
        return File(getSkinsDirectory(context), "${key}.png")
    }

    suspend fun downloadAndSaveSkin(context: Context, key: String, url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = SkinUtils.fromUrl(url)
            val normalized = SkinUtils.normalizeSkin(raw)
            val classic = SkinUtils.toClassicModel(normalized)
            val file = getSkinFile(context, key)
            FileOutputStream(file).use { out ->
                classic.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            classic
        }.onFailure { Log.w("AccountSkinManager", "Failed to download and save skin for $key", it) }
            .getOrNull()
    }

    fun loadSkin(context: Context, account: LauncherAccount?): Bitmap {
        if (account != null) {
            val candidates = listOfNotNull(account.uuid, account.id)
            for (key in candidates) {
                val file = getSkinFile(context, key)
                if (file.isFile && file.length() > 0) {
                    val loaded = runCatching {
                        file.inputStream().use { stream ->
                            val raw = SkinUtils.fromStream(stream)
                            SkinUtils.toClassicModel(SkinUtils.normalizeSkin(raw))
                        }
                    }.getOrNull()
                    if (loaded != null) return loaded
                }
            }
        }
        return runCatching { SkinUtils.fromAsset(context, DEFAULT_SKIN_ASSET) }
            .mapCatching { SkinUtils.toClassicModel(SkinUtils.normalizeSkin(it)) }
            .getOrElse { DefaultSkin.create() }
    }
}
