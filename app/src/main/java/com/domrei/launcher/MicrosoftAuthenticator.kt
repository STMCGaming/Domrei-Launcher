package com.domrei.launcher

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

data class MicrosoftAuthResult(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val refreshToken: String?,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val isSlim: Boolean = false,
    val doesOwnGame: Boolean = true,
)

data class MinecraftProfileData(
    val username: String,
    val uuid: String,
    val skinUrl: String? = null,
    val capeUrl: String? = null,
    val isSlim: Boolean = false,
)

/**
 * Handles official Microsoft OAuth2 & Xbox Live authentication for Minecraft Java Edition,
 * following the standard OAuth flow used by PojavLauncher and Minecraft services.
 */
object MicrosoftAuthenticator {
    const val CLIENT_ID = "00000000402b5328"
    const val REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
    const val REDIRECT_URL_PREFIX = "https://login.live.com/oauth20_desktop.srf"
    const val XAL_REDIRECT_PREFIX = "ms-xal-00000000402b5328"

    val LOGIN_URL = "https://login.live.com/oauth20_authorize.srf" +
        "?client_id=$CLIENT_ID" +
        "&response_type=code" +
        "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL" +
        "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")

    private const val AUTH_TOKEN_URL = "https://login.live.com/oauth20_token.srf"
    private const val XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
    private const val MC_STORE_URL = "https://api.minecraftservices.com/entitlements/mcstore"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val USER_AGENT = "DomreiLauncher/1.0 (Android; MicrosoftAuth)"

    /**
     * Creates an optimized HTTP connection configured with keep-alive, reasonable timeouts,
     * compression headers, and identification.
     */
    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept-Encoding", "gzip, deflate")
            useCaches = false
        }
    }

    /**
     * Retries an idempotent network call on transient IO / socket timeouts.
     */
    private inline fun <T> retryOnNetworkError(maxRetries: Int = 1, block: () -> T): T {
        var lastEx: IOException? = null
        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: IOException) {
                lastEx = e
                if (attempt < maxRetries) {
                    Thread.sleep(300)
                }
            }
        }
        throw lastEx ?: IOException("Network request failed after retries")
    }

    /**
     * Checks if the given URL is the redirect URL capturing the authorization code.
     */
    fun isRedirectUrl(url: String): Boolean {
        return url.startsWith(REDIRECT_URL_PREFIX) || url.startsWith(XAL_REDIRECT_PREFIX)
    }

    /**
     * Extracts the authorization code or null if not present/error.
     */
    fun extractCode(url: String): String? {
        val queryStart = url.indexOf('?').takeIf { it >= 0 } ?: return null
        val query = url.substring(queryStart + 1)
        for (param in query.split('&')) {
            val parts = param.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == "code") {
                return URLDecoder.decode(parts[1], "UTF-8")
            }
        }
        return null
    }

    /**
     * Exchanges the authorization code for Microsoft, Xbox Live, and Minecraft tokens,
     * and verifies the Minecraft player profile and ownership.
     */
    suspend fun authenticateWithCode(
        code: String,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
    ): Result<MicrosoftAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Exchange code for MSA access & refresh tokens
            onProgress("Exchanging Microsoft authorization token…", 0.15f)
            val (msAccessToken, msRefreshToken) = acquireMsaToken(code)

            // 2. Xbox Live authentication
            onProgress("Authenticating with Xbox Live…", 0.35f)
            val xblToken = acquireXblToken(msAccessToken)

            // 3. XSTS authorization
            onProgress("Verifying Xbox Security Token (XSTS)…", 0.55f)
            val (xblUhs, xstsToken) = acquireXstsToken(xblToken)

            // 4. Minecraft service authentication
            onProgress("Logging into Minecraft Services…", 0.75f)
            val mcToken = acquireMinecraftToken(xblUhs, xstsToken)

            // 5 & 6. Concurrently touch store entitlements & fetch profile to save network latency
            onProgress("Verifying Minecraft ownership & profile…", 0.90f)
            val profileDeferred = async { fetchMinecraftProfile(mcToken) }
            val entitlementsDeferred = async { touchStoreEntitlements(mcToken) }
            entitlementsDeferred.await()
            val profile = profileDeferred.await()

            MicrosoftAuthResult(
                username = profile.username,
                uuid = profile.uuid,
                accessToken = mcToken,
                refreshToken = msRefreshToken,
                skinUrl = profile.skinUrl,
                capeUrl = profile.capeUrl,
                isSlim = profile.isSlim,
                doesOwnGame = true,
            )
        }
    }

    /**
     * Refreshes the session using an existing Microsoft refresh token.
     */
    suspend fun refreshAccount(
        refreshToken: String,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> },
    ): Result<MicrosoftAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress("Refreshing Microsoft authorization…", 0.15f)
            val (msAccessToken, newRefreshToken) = refreshMsaToken(refreshToken)

            onProgress("Authenticating with Xbox Live…", 0.35f)
            val xblToken = acquireXblToken(msAccessToken)

            onProgress("Verifying Xbox Security Token (XSTS)…", 0.55f)
            val (xblUhs, xstsToken) = acquireXstsToken(xblToken)

            onProgress("Logging into Minecraft Services…", 0.75f)
            val mcToken = acquireMinecraftToken(xblUhs, xstsToken)

            onProgress("Verifying Minecraft ownership & profile…", 0.90f)
            val profileDeferred = async { fetchMinecraftProfile(mcToken) }
            val entitlementsDeferred = async { touchStoreEntitlements(mcToken) }
            entitlementsDeferred.await()
            val profile = profileDeferred.await()

            MicrosoftAuthResult(
                username = profile.username,
                uuid = profile.uuid,
                accessToken = mcToken,
                refreshToken = newRefreshToken ?: refreshToken,
                skinUrl = profile.skinUrl,
                capeUrl = profile.capeUrl,
                isSlim = profile.isSlim,
                doesOwnGame = true,
            )
        }
    }

    private fun acquireMsaToken(code: String): Pair<String, String?> = retryOnNetworkError {
        val formData = buildFormData(
            "client_id" to CLIENT_ID,
            "code" to code,
            "grant_type" to "authorization_code",
            "redirect_uri" to REDIRECT_URI,
            "scope" to "service::user.auth.xboxlive.com::MBI_SSL",
        )
        val bodyBytes = formData.toByteArray(StandardCharsets.UTF_8)

        val conn = openConnection(AUTH_TOKEN_URL).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            doInput = true
            doOutput = true
        }

        conn.outputStream.use { it.write(bodyBytes) }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotEmpty() }
            Pair(accessToken, refreshToken)
        } else {
            val error = readStream(conn, error = true)
            throw IllegalStateException("Failed to exchange Microsoft authorization code ($status): $error")
        }
    }

    private fun refreshMsaToken(refreshToken: String): Pair<String, String?> = retryOnNetworkError {
        val formData = buildFormData(
            "client_id" to CLIENT_ID,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
            "scope" to "service::user.auth.xboxlive.com::MBI_SSL",
        )
        val bodyBytes = formData.toByteArray(StandardCharsets.UTF_8)

        val conn = openConnection(AUTH_TOKEN_URL).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            doInput = true
            doOutput = true
        }

        conn.outputStream.use { it.write(bodyBytes) }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            val accessToken = json.getString("access_token")
            val newRefresh = json.optString("refresh_token").takeIf { it.isNotEmpty() }
            Pair(accessToken, newRefresh)
        } else {
            val error = readStream(conn, error = true)
            throw IllegalStateException("Failed to refresh Microsoft token ($status): $error")
        }
    }

    private fun acquireXblToken(msAccessToken: String): String = retryOnNetworkError {
        val body = JSONObject().apply {
            put(
                "Properties",
                JSONObject().apply {
                    put("AuthMethod", "RPS")
                    put("SiteName", "user.auth.xboxlive.com")
                    put("RpsTicket", "d=$msAccessToken")
                },
            )
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }.toString()
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)

        val conn = openConnection(XBL_AUTH_URL).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            doInput = true
            doOutput = true
        }

        conn.outputStream.use { it.write(bodyBytes) }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            json.getString("Token")
        } else {
            val error = readStream(conn, error = true)
            throw IllegalStateException("Xbox Live authentication failed ($status): $error")
        }
    }

    private fun acquireXstsToken(xblToken: String): Pair<String, String> = retryOnNetworkError {
        val body = JSONObject().apply {
            put(
                "Properties",
                JSONObject().apply {
                    put("SandboxId", "RETAIL")
                    put("UserTokens", JSONArray().apply { put(xblToken) })
                },
            )
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }.toString()
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)

        val conn = openConnection(XSTS_AUTH_URL).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            doInput = true
            doOutput = true
        }

        conn.outputStream.use { it.write(bodyBytes) }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            val uhs = json.getJSONObject("DisplayClaims")
                .getJSONArray("xui")
                .getJSONObject(0)
                .getString("uhs")
            val token = json.getString("Token")
            Pair(uhs, token)
        } else {
            val error = readStream(conn, error = true)
            val xerr = runCatching { JSONObject(error).optLong("XErr", -1) }.getOrDefault(-1)
            val message = when (xerr) {
                2148916233L -> "This Microsoft account has no Xbox Live account."
                2148916235L -> "Xbox Live is not available in your country/region."
                2148916236L, 2148916237L -> "Adult verification is required on Xbox Live."
                2148916238L -> "This child account must be added to a Family by an adult."
                else -> "Xbox XSTS authorization failed ($status): $error"
            }
            throw IllegalStateException(message)
        }
    }

    private fun acquireMinecraftToken(xblUhs: String, xstsToken: String): String = retryOnNetworkError {
        val body = JSONObject().apply {
            put("identityToken", "XBL3.0 x=$xblUhs;$xstsToken")
        }.toString()
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)

        val conn = openConnection(MC_LOGIN_URL).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("charset", "utf-8")
            setRequestProperty("Content-Length", bodyBytes.size.toString())
            doInput = true
            doOutput = true
        }

        conn.outputStream.use { it.write(bodyBytes) }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            json.getString("access_token")
        } else {
            val error = readStream(conn, error = true)
            throw IllegalStateException("Minecraft authentication failed ($status): $error")
        }
    }

    private fun touchStoreEntitlements(mcToken: String) {
        runCatching {
            val conn = openConnection(MC_STORE_URL).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $mcToken")
            }
            // Drain stream and close it cleanly so the underlying keep-alive connection stays in the pool
            conn.inputStream?.use { input ->
                val buffer = ByteArray(1024)
                while (input.read(buffer) != -1) { /* drain to keep socket pool alive */ }
            }
        }
    }

    private suspend fun fetchMinecraftProfile(mcToken: String): MinecraftProfileData = retryOnNetworkError {
        val conn = openConnection(MC_PROFILE_URL).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $mcToken")
        }

        val status = conn.responseCode
        if (status in 200..299) {
            val response = readStream(conn)
            val json = JSONObject(response)
            val name = json.getString("name")
            val rawId = json.getString("id")
            val uuid = formatUuidWithDashes(rawId)

            var skinUrl: String? = null
            var isSlim = false
            val skinsArray = json.optJSONArray("skins")
            if (skinsArray != null && skinsArray.length() > 0) {
                for (i in 0 until skinsArray.length()) {
                    val item = skinsArray.getJSONObject(i)
                    val state = item.optString("state", "")
                    if (state.equals("ACTIVE", ignoreCase = true) || skinUrl == null) {
                        skinUrl = item.optString("url").takeIf { it.isNotEmpty() }?.replace("http://", "https://")
                        val variant = item.optString("variant", "CLASSIC")
                        isSlim = variant.equals("SLIM", ignoreCase = true)
                        if (state.equals("ACTIVE", ignoreCase = true)) break
                    }
                }
            }

            var capeUrl: String? = null
            val capesArray = json.optJSONArray("capes")
            if (capesArray != null && capesArray.length() > 0) {
                for (i in 0 until capesArray.length()) {
                    val item = capesArray.getJSONObject(i)
                    val state = item.optString("state", "")
                    if (state.equals("ACTIVE", ignoreCase = true) || capeUrl == null) {
                        capeUrl = item.optString("url").takeIf { it.isNotEmpty() }?.replace("http://", "https://")
                        if (state.equals("ACTIVE", ignoreCase = true)) break
                    }
                }
            }

            if (skinUrl == null) {
                val fallbackProfile = runCatching {
                    com.domrei.skinview.MojangSkinApi.fetchByUuid(uuid).getOrNull()
                }.getOrNull()
                if (fallbackProfile != null) {
                    skinUrl = fallbackProfile.skinUrl
                    isSlim = fallbackProfile.isSlim
                    if (capeUrl == null) {
                        capeUrl = fallbackProfile.capeUrl
                    }
                }
            }

            MinecraftProfileData(
                username = name,
                uuid = uuid,
                skinUrl = skinUrl,
                capeUrl = capeUrl,
                isSlim = isSlim,
            )
        } else if (status == 404) {
            throw IllegalStateException("This Microsoft account does not own Minecraft Java Edition.")
        } else {
            val error = readStream(conn, error = true)
            throw IllegalStateException("Failed to load Minecraft profile ($status): $error")
        }
    }

    private fun formatUuidWithDashes(id: String): String {
        return if (id.length == 32 && !id.contains("-")) {
            id.replaceFirst(
                Regex("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)"),
                "$1-$2-$3-$4-$5",
            )
        } else {
            id
        }
    }

    private fun buildFormData(vararg params: Pair<String, String>): String {
        return params.joinToString("&") { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
    }

    private fun readStream(conn: HttpURLConnection, error: Boolean = false): String {
        val stream = if (error) conn.errorStream else conn.inputStream
        if (stream == null) return ""
        val encoding = conn.contentEncoding
        val effectiveStream = when {
            encoding.equals("gzip", ignoreCase = true) -> GZIPInputStream(stream)
            encoding.equals("deflate", ignoreCase = true) -> InflaterInputStream(stream)
            else -> stream
        }
        return BufferedReader(InputStreamReader(effectiveStream, StandardCharsets.UTF_8), 8192).use { it.readText() }
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8), 8192).use { it.readText() }
    }
}
