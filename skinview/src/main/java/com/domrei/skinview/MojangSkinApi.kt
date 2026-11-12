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
                ?: error("Player '$trimmedName' not found")

            val profileJson = JSONObject(profileJsonStr)
            val uuid = profileJson.getString("id")
            val resolvedName = profileJson.optString("name", trimmedName)

            fetchByUuidInternal(uuid, resolvedName)
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
            ?: error("Profile not found for UUID $cleanUuid")

        val sessionJson = JSONObject(sessionJsonStr)
        val name = resolvedName ?: sessionJson.optString("name", "Player")

        val properties = sessionJson.optJSONArray("properties")
            ?: error("No profile properties found for $cleanUuid")

        var texturesBase64: String? = null
        for (i in 0 until properties.length()) {
            val prop = properties.getJSONObject(i)
            if (prop.optString("name") == "textures") {
                texturesBase64 = prop.optString("value")
                break
            }
        }

        requireNotNull(texturesBase64) { "No textures payload found for $cleanUuid" }

        val decodedBytes = runCatching {
            android.util.Base64.decode(texturesBase64, android.util.Base64.DEFAULT)
        }.getOrElse {
            java.util.Base64.getDecoder().decode(texturesBase64.replace("\n", "").replace("\r", "").trim())
        }
        val decodedJsonStr = String(decodedBytes, Charsets.UTF_8)
        val texturesRoot = JSONObject(decodedJsonStr).optJSONObject("textures")
            ?: error("Invalid textures JSON for $cleanUuid")

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
                HttpURLConnection.HTTP_NO_CONTENT, HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("HTTP ${conn.responseCode} from $urlString")
            }
        } finally {
            conn.disconnect()
        }
    }
}
