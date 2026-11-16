package com.domrei.skinview

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result representing a player's official Minecraft profile, skin, and optional cape.
 */
data class MinecraftPlayerProfile(
    val username: String,
    val uuid: String,
    val skinBitmap: Bitmap?,
    val capeBitmap: Bitmap?,
    val isSlim: Boolean,
    val skinUrl: String?,
    val capeUrl: String?,
)

/**
 * Client for the official Mojang API to fetch Minecraft skins and capes by player username or UUID.
 *
 * Endpoints used:
 * 1. Username to UUID: `https://api.mojang.com/users/profiles/minecraft/<username>`
 * 2. UUID to Profile Textures: `https://sessionserver.mojang.com/session/minecraft/profile/<uuid>`
 * 3. Textures CDN: `https://textures.minecraft.net/texture/<hash>`
 */
object MojangSkinApi {

    private const val MOJANG_PROFILE_URL = "https://api.mojang.com/users/profiles/minecraft/"
    private const val MOJANG_SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/"

    /**
     * Fetches official Minecraft player skin and cape by [username].
     */
    suspend fun fetchByUsername(username: String): Result<MinecraftPlayerProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmedName = username.trim()
            require(trimmedName.isNotEmpty()) { "Username cannot be empty" }

            val profileJsonStr = httpGet("$MOJANG_PROFILE_URL$trimmedName")
            if (profileJsonStr != null) {
                val profileJson = runCatching { JSONObject(profileJsonStr) }.getOrNull()
                val uuid = profileJson?.optString("id")?.takeIf { it.isNotEmpty() }
                if (uuid != null) {
                    val resolvedName = profileJson.optString("name", trimmedName)
                    return@runCatching fetchByUuidInternal(uuid, resolvedName)
                }
            }

            // Fallback: Minotar CDN by username (resilient against Mojang API rate limits)
            val minotarUrl = "https://minotar.net/skin/$trimmedName"
            val fallbackBitmap = runCatching {
                SkinUtils.normalizeSkin(SkinUtils.fromUrl(minotarUrl))
            }.getOrNull()

            MinecraftPlayerProfile(
                username = trimmedName,
                uuid = trimmedName,
                skinBitmap = fallbackBitmap,
                capeBitmap = null,
                isSlim = fallbackBitmap?.let { SkinUtils.isSlim(it) } ?: false,
                skinUrl = minotarUrl,
                capeUrl = null,
            )
        }
    }

    /**
     * Fetches official Minecraft player skin and cape by [uuid] (with or without dashes).
     */
    suspend fun fetchByUuid(uuid: String): Result<MinecraftPlayerProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUuid = uuid.replace("-", "").trim()
            require(cleanUuid.length == 32) { "Invalid UUID length: $uuid" }
            fetchByUuidInternal(cleanUuid, null)
        }
    }

    private suspend fun fetchByUuidInternal(cleanUuid: String, resolvedName: String?): MinecraftPlayerProfile {
        val sessionJsonStr = httpGet("$MOJANG_SESSION_URL$cleanUuid")
        if (sessionJsonStr != null) {
            val sessionJson = runCatching { JSONObject(sessionJsonStr) }.getOrNull()
            val name = resolvedName ?: sessionJson?.optString("name", "Player") ?: "Player"
            val properties = sessionJson?.optJSONArray("properties")

            var texturesBase64: String? = null
            if (properties != null) {
                for (i in 0 until properties.length()) {
                    val prop = properties.getJSONObject(i)
                    if (prop.optString("name") == "textures") {
                        texturesBase64 = prop.optString("value")
                        break
                    }
                }
            }

            if (texturesBase64 != null) {
                val decodedBytes = runCatching {
                    android.util.Base64.decode(texturesBase64, android.util.Base64.DEFAULT)
                }.getOrElse {
                    java.util.Base64.getDecoder().decode(texturesBase64.replace("\n", "").replace("\r", "").trim())
                }
                val decodedJsonStr = String(decodedBytes, Charsets.UTF_8)
                val texturesRoot = runCatching { JSONObject(decodedJsonStr).optJSONObject("textures") }.getOrNull()

                if (texturesRoot != null) {
                    // Parse Skin
                    var skinUrl: String? = null
                    var isSlim = false
                    var skinBitmap: Bitmap? = null

                    val skinObj = texturesRoot.optJSONObject("SKIN")
                    if (skinObj != null) {
                        skinUrl = skinObj.optString("url").replace("http://", "https://")
                        val metadata = skinObj.optJSONObject("metadata")
                        isSlim = metadata?.optString("model") == "slim"
                        skinBitmap = runCatching {
                            SkinUtils.normalizeSkin(SkinUtils.fromUrl(skinUrl))
                        }.getOrNull()
                    }

                    // Parse Cape
                    var capeUrl: String? = null
                    var capeBitmap: Bitmap? = null

                    val capeObj = texturesRoot.optJSONObject("CAPE")
                    if (capeObj != null) {
                        capeUrl = capeObj.optString("url").replace("http://", "https://")
                        capeBitmap = runCatching {
                            SkinUtils.normalizeCape(SkinUtils.fromUrl(capeUrl))
                        }.getOrNull()
                    }

                    return MinecraftPlayerProfile(
                        username = name,
                        uuid = cleanUuid,
                        skinBitmap = skinBitmap,
                        capeBitmap = capeBitmap,
                        isSlim = isSlim,
                        skinUrl = skinUrl,
                        capeUrl = capeUrl,
                    )
                }
            }
        }

        // Fallback to Crafatar CDN (resilient to Mojang session server 429 rate limits or transient errors)
        val fallbackSkinUrl = "https://crafatar.com/skins/$cleanUuid"
        val fallbackBitmap = runCatching {
            SkinUtils.normalizeSkin(SkinUtils.fromUrl(fallbackSkinUrl))
        }.getOrNull()

        return MinecraftPlayerProfile(
            username = resolvedName ?: "Player",
            uuid = cleanUuid,
            skinBitmap = fallbackBitmap,
            capeBitmap = null,
            isSlim = fallbackBitmap?.let { SkinUtils.isSlim(it) } ?: false,
            skinUrl = fallbackSkinUrl,
            capeUrl = null,
        )
    }

    private fun httpGet(urlString: String): String? {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DomreiSkinViewer/1.0")
        }
        return try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> conn.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_NOT_FOUND, 429 -> null
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    }
}
