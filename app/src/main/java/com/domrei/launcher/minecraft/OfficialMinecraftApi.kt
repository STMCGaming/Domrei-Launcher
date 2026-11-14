package com.domrei.launcher.minecraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class MinecraftVersionFilter(val id: String) {
    RELEASE("release"),
    SNAPSHOT("snapshot"),
    OLD("old"),
}

data class MinecraftVersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String,
    val releaseTime: String,
) {
    val isOld: Boolean
        get() = type == "old_beta" || type == "old_alpha"

    fun matchesFilter(filter: MinecraftVersionFilter): Boolean = when (filter) {
        MinecraftVersionFilter.RELEASE -> type == "release"
        MinecraftVersionFilter.SNAPSHOT -> type == "snapshot"
        MinecraftVersionFilter.OLD -> isOld
    }
}

data class MinecraftVersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MinecraftVersionEntry>,
)

data class FabricLoaderVersionEntry(
    val version: String,
    val maven: String,
    val stable: Boolean,
    val releaseTime: String = "",
)

enum class DownloadCategory {
    VERSION_JSON,
    FABRIC_PROFILE,
    CLIENT_JAR,
    LIBRARIES,
    ASSET_INDEX,
    ASSETS,
}

data class DownloadArtifact(
    val relativePath: String,
    val displayName: String,
    val url: String,
    val sha1: String?,
    val sizeBytes: Long,
    val category: DownloadCategory,
)

data class AssetIndexMetadata(
    val id: String,
    val url: String,
    val sha1: String,
    val sizeBytes: Long,
    val totalSizeBytes: Long,
)

data class ParsedVersionPackage(
    val id: String,
    val type: String,
    val mainClass: String,
    val clientJar: DownloadArtifact,
    val assetIndex: AssetIndexMetadata,
    val libraries: List<DownloadArtifact>,
)

data class ParsedFabricProfile(
    val id: String,
    val inheritsFrom: String,
    val mainClass: String,
    val libraries: List<DownloadArtifact>,
)

/**
 * Client for official Mojang Piston Meta / Resources APIs and Fabric Meta API.
 */
object OfficialMinecraftApi {

    const val VERSION_MANIFEST_V2_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    const val RESOURCES_BASE_URL = "https://resources.download.minecraft.net/"
    const val FABRIC_GAME_VERSIONS_URL = "https://meta.fabricmc.net/v2/versions/game"
    const val FABRIC_LOADERS_URL = "https://meta.fabricmc.net/v2/versions/loader"
    const val FABRIC_MAVEN_BASE_URL = "https://maven.fabricmc.net/"
    const val FABRIC_LOADER_INDEX_URL = "${FABRIC_MAVEN_BASE_URL}net/fabricmc/fabric-loader/"
    private const val USER_AGENT = "DomreiLauncher/1.0"

    private val ISO_DATE_PREFIX_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})(?:$|[T\s])""")
    private val MAVEN_DATE_REGEX = Regex("""^(\d{1,2})-([A-Za-z]{3})-(\d{4})$""")
    private val FABRIC_INDEX_ROW_REGEX = Regex(
        """<a\s+href="[^"]*/">\s*([^/<]+)/</a>\s+(\d{1,2}-[A-Za-z]{3}-\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private val MONTH_ABBREVIATIONS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    private val MONTH_INDEX_BY_ABBR = MONTH_ABBREVIATIONS
        .mapIndexed { index, abbr -> abbr.lowercase(Locale.US) to (index + 1) }
        .toMap()

    private val cacheMutex = Mutex()
    private var cachedManifest: MinecraftVersionManifest? = null
    private var cachedFabricGameVersions: Set<String>? = null
    private val cachedFabricLoaders = mutableMapOf<String, List<FabricLoaderVersionEntry>>()
    private var cachedFabricLoaderReleaseTimes: Map<String, String>? = null

    /**
     * Fetches and caches Mojang's official `version_manifest_v2.json`.
     */
    suspend fun fetchVersionManifest(forceRefresh: Boolean = false): Result<MinecraftVersionManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!forceRefresh) {
                    cacheMutex.withLock { cachedManifest }?.let { return@runCatching it }
                }
                val jsonStr = fetchRawJson(VERSION_MANIFEST_V2_URL)
                val root = JSONObject(jsonStr)
                val latest = root.optJSONObject("latest")
                val latestRelease = latest?.optString("release").orEmpty()
                val latestSnapshot = latest?.optString("snapshot").orEmpty()

                val versionsArray = root.getJSONArray("versions")
                val versions = ArrayList<MinecraftVersionEntry>(versionsArray.length())
                for (i in 0 until versionsArray.length()) {
                    val item = versionsArray.getJSONObject(i)
                    val id = item.optString("id").trim()
                    val url = item.optString("url").trim()
                    if (id.isNotEmpty() && url.isNotEmpty()) {
                        versions.add(
                            MinecraftVersionEntry(
                                id = id,
                                type = item.optString("type", "release"),
                                url = url,
                                sha1 = item.optString("sha1", ""),
                                releaseTime = item.optString("releaseTime", ""),
                            ),
                        )
                    }
                }

                val manifest = MinecraftVersionManifest(
                    latestRelease = latestRelease,
                    latestSnapshot = latestSnapshot,
                    versions = versions,
                )
                cacheMutex.withLock { cachedManifest = manifest }
                manifest
            }
        }

    /**
     * Fetches the set of Minecraft versions officially supported by Fabric Loader.
     */
    suspend fun fetchFabricGameVersions(forceRefresh: Boolean = false): Result<Set<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!forceRefresh) {
                    cacheMutex.withLock { cachedFabricGameVersions }?.let { return@runCatching it }
                }
                val jsonStr = fetchRawJson(FABRIC_GAME_VERSIONS_URL)
                val array = JSONArray(jsonStr)
                val versions = LinkedHashSet<String>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val ver = item.optString("version").trim()
                    if (ver.isNotEmpty()) {
                        versions.add(ver)
                    }
                }
                cacheMutex.withLock { cachedFabricGameVersions = versions }
                versions
            }
        }

    /**
     * Fetches available Fabric Loader versions for [mcVersion] from `/v2/versions/loader/{mcVersion}`,
     * falling back to `/v2/versions/loader` if needed.
     */
    suspend fun fetchFabricLoaders(mcVersion: String): Result<List<FabricLoaderVersionEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedMc = mcVersion.trim()
                require(trimmedMc.isNotEmpty()) { "Minecraft version cannot be empty" }

                cacheMutex.withLock { cachedFabricLoaders[trimmedMc] }?.let { return@runCatching it }

                val encodedMc = URLEncoder.encode(trimmedMc, "UTF-8").replace("+", "%20")
                val versionSpecificUrl = "$FABRIC_LOADERS_URL/$encodedMc"
                val (rawJson, releaseTimes) = coroutineScope {
                    val jsonDeferred = async {
                        runCatching { fetchRawJson(versionSpecificUrl) }
                            .getOrElse { fetchRawJson(FABRIC_LOADERS_URL) }
                    }
                    val releaseTimesDeferred = async {
                        runCatching { fetchFabricLoaderReleaseTimes() }.getOrDefault(emptyMap())
                    }
                    jsonDeferred.await() to releaseTimesDeferred.await()
                }
                val loaders = parseFabricLoadersArray(rawJson, releaseTimes)

                if (loaders.isNotEmpty()) {
                    cacheMutex.withLock { cachedFabricLoaders[trimmedMc] = loaders }
                }
                loaders
            }
        }

    fun fabricProfileJsonUrl(mcVersion: String, loaderVersion: String): String {
        val encodedMc = URLEncoder.encode(mcVersion.trim(), "UTF-8").replace("+", "%20")
        val encodedLoader = URLEncoder.encode(loaderVersion.trim(), "UTF-8").replace("+", "%20")
        return "$FABRIC_LOADERS_URL/$encodedMc/$encodedLoader/profile/json"
    }

    suspend fun fetchRawJson(url: String): String =
        fetchRawText(url = url, accept = "application/json")

    private suspend fun fetchRawText(
        url: String,
        accept: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("HTTP $code from $url")
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseReleaseDateParts(rawReleaseTime: String): Triple<Int, Int, Int>? {
        val trimmed = rawReleaseTime.trim()
        if (trimmed.isEmpty()) return null

        ISO_DATE_PREFIX_REGEX.find(trimmed)?.let { match ->
            val year = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
                return Triple(year, month, day)
            }
        }

        MAVEN_DATE_REGEX.matchEntire(trimmed)?.let { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = MONTH_INDEX_BY_ABBR[match.groupValues[2].lowercase(Locale.US)]
            val year = match.groupValues[3].toIntOrNull()
            if (year != null && month != null && day != null && day in 1..31) {
                return Triple(year, month, day)
            }
        }

        return null
    }

    /**
     * Parses a Mojang ISO-8601 `releaseTime` (e.g. `2026-11-10T14:20:05+00:00`), normalized
     * `YYYY-MM-DD`, or Fabric Maven `DD-MMM-YYYY` date string into `MMM d, yyyy`.
     */
    fun formatReleaseDate(rawReleaseTime: String): String {
        val (year, month, day) = parseReleaseDateParts(rawReleaseTime) ?: return ""
        return "${MONTH_ABBREVIATIONS[month - 1]} $day, $year"
    }

    internal fun parseFabricLoaderReleaseDates(html: String): Map<String, String> {
        val dates = LinkedHashMap<String, String>()
        for (match in FABRIC_INDEX_ROW_REGEX.findAll(html)) {
            val version = match.groupValues[1].trim()
            val parts = parseReleaseDateParts(match.groupValues[2])
            if (version.isNotEmpty() && parts != null) {
                val (year, month, day) = parts
                dates[version] = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            }
        }
        return dates
    }

    private suspend fun fetchFabricLoaderReleaseTimes(forceRefresh: Boolean = false): Map<String, String> {
        if (!forceRefresh) {
            cacheMutex.withLock { cachedFabricLoaderReleaseTimes }?.let { return it }
        }
        val html = fetchRawText(
            url = FABRIC_LOADER_INDEX_URL,
            accept = "text/html,application/xhtml+xml,*/*",
            connectTimeoutMs = 8_000,
            readTimeoutMs = 10_000,
        )
        val parsed = parseFabricLoaderReleaseDates(html)
        if (parsed.isNotEmpty()) {
            cacheMutex.withLock { cachedFabricLoaderReleaseTimes = parsed }
        }
        return parsed
    }

    fun parseVersionPackageJson(mcVersion: String, jsonStr: String): ParsedVersionPackage {
        val root = JSONObject(jsonStr)
        val id = root.optString("id", mcVersion)
        val type = root.optString("type", "release")
        val mainClass = root.optString("mainClass", "net.minecraft.client.main.Main")

        val downloads = root.getJSONObject("downloads")
        val clientObj = downloads.getJSONObject("client")
        val clientUrl = clientObj.getString("url")
        val clientSha1 = clientObj.optString("sha1").takeIf { it.isNotEmpty() }
        val clientSize = clientObj.optLong("size", 0L)
        val clientArtifact = DownloadArtifact(
            relativePath = "versions/$id/$id.jar",
            displayName = "$id.jar",
            url = clientUrl,
            sha1 = clientSha1,
            sizeBytes = clientSize,
            category = DownloadCategory.CLIENT_JAR,
        )

        val assetIndexObj = root.getJSONObject("assetIndex")
        val assetIndex = AssetIndexMetadata(
            id = assetIndexObj.getString("id"),
            url = assetIndexObj.getString("url"),
            sha1 = assetIndexObj.optString("sha1", ""),
            sizeBytes = assetIndexObj.optLong("size", 0L),
            totalSizeBytes = assetIndexObj.optLong("totalSize", 0L),
        )

        val librariesArray = root.optJSONArray("libraries") ?: JSONArray()
        val librariesByPath = LinkedHashMap<String, DownloadArtifact>(librariesArray.length())
        for (i in 0 until librariesArray.length()) {
            val libObj = librariesArray.getJSONObject(i)
            if (!isLibraryAllowed(libObj)) continue

            val libDownloads = libObj.optJSONObject("downloads") ?: continue
            val artifact = libDownloads.optJSONObject("artifact") ?: continue
            val url = artifact.optString("url").trim()
            if (url.isEmpty()) continue

            val path = artifact.optString("path").trim().ifEmpty {
                val coord = libObj.optString("name").trim()
                if (coord.isNotEmpty()) mavenCoordinateToPath(coord) else ""
            }
            if (path.isEmpty()) continue

            val relativePath = "libraries/$path"
            if (!librariesByPath.containsKey(relativePath)) {
                librariesByPath[relativePath] = DownloadArtifact(
                    relativePath = relativePath,
                    displayName = path.substringAfterLast('/'),
                    url = url,
                    sha1 = artifact.optString("sha1").takeIf { it.isNotEmpty() },
                    sizeBytes = artifact.optLong("size", 0L),
                    category = DownloadCategory.LIBRARIES,
                )
            }
        }

        return ParsedVersionPackage(
            id = id,
            type = type,
            mainClass = mainClass,
            clientJar = clientArtifact,
            assetIndex = assetIndex,
            libraries = librariesByPath.values.toList(),
        )
    }

    fun parseFabricProfileJson(jsonStr: String): ParsedFabricProfile {
        val root = JSONObject(jsonStr)
        val id = root.getString("id")
        val inheritsFrom = root.optString("inheritsFrom", "")
        val mainClass = root.optString("mainClass", "net.fabricmc.loader.impl.launch.knot.KnotClient")

        val librariesArray = root.optJSONArray("libraries") ?: JSONArray()
        val librariesByPath = LinkedHashMap<String, DownloadArtifact>(librariesArray.length())
        for (i in 0 until librariesArray.length()) {
            val libObj = librariesArray.getJSONObject(i)
            val coordinate = libObj.optString("name").trim()
            if (coordinate.isEmpty()) continue

            val mavenPath = mavenCoordinateToPath(coordinate)
            val baseUrl = libObj.optString("url", FABRIC_MAVEN_BASE_URL)
                .ifBlank { FABRIC_MAVEN_BASE_URL }
                .let { if (it.endsWith("/")) it else "$it/" }
            val fullUrl = "$baseUrl$mavenPath"
            val relativePath = "libraries/$mavenPath"

            if (!librariesByPath.containsKey(relativePath)) {
                librariesByPath[relativePath] = DownloadArtifact(
                    relativePath = relativePath,
                    displayName = mavenPath.substringAfterLast('/'),
                    url = fullUrl,
                    sha1 = libObj.optString("sha1").takeIf { it.isNotEmpty() },
                    sizeBytes = libObj.optLong("size", 0L),
                    category = DownloadCategory.LIBRARIES,
                )
            }
        }

        return ParsedFabricProfile(
            id = id,
            inheritsFrom = inheritsFrom,
            mainClass = mainClass,
            libraries = librariesByPath.values.toList(),
        )
    }

    fun parseAssetIndexJson(jsonStr: String): List<DownloadArtifact> {
        val root = JSONObject(jsonStr)
        val objects = root.optJSONObject("objects") ?: return emptyList()
        val artifactsByHash = LinkedHashMap<String, DownloadArtifact>(objects.length())

        val keys = objects.keys()
        while (keys.hasNext()) {
            val assetName = keys.next()
            val entry = objects.optJSONObject(assetName) ?: continue
            val hash = entry.optString("hash").trim()
            if (hash.length < 2) continue

            if (!artifactsByHash.containsKey(hash)) {
                val prefix = hash.substring(0, 2)
                val relPath = "assets/objects/$prefix/$hash"
                artifactsByHash[hash] = DownloadArtifact(
                    relativePath = relPath,
                    displayName = assetName.substringAfterLast('/'),
                    url = "$RESOURCES_BASE_URL$prefix/$hash",
                    sha1 = hash,
                    sizeBytes = entry.optLong("size", 0L),
                    category = DownloadCategory.ASSETS,
                )
            }
        }

        return artifactsByHash.values.toList()
    }

    /**
     * Evaluates Mojang `rules` and rejects desktop-only Windows/macOS native classifiers.
     */
    fun isLibraryAllowed(libJson: JSONObject): Boolean {
        val name = libJson.optString("name", "")
        if (name.contains(":natives-windows", ignoreCase = true) ||
            name.contains(":natives-macos", ignoreCase = true) ||
            name.contains(":natives-osx", ignoreCase = true)
        ) {
            return false
        }

        val rules = libJson.optJSONArray("rules") ?: return true
        if (rules.length() == 0) return true

        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val action = rule.optString("action")
            val osObj = rule.optJSONObject("os")
            val osName = osObj?.optString("name")
            if (osName.isNullOrEmpty() || osName == "linux") {
                allowed = (action == "allow")
            }
        }
        return allowed
    }

    /**
     * Converts Maven coordinate `group:artifact:version[:classifier]` to repository path.
     */
    fun mavenCoordinateToPath(coordinate: String): String {
        val parts = coordinate.split(":")
        require(parts.size >= 3) { "Invalid Maven coordinate: $coordinate" }
        val groupPath = parts[0].replace('.', '/')
        val artifact = parts[1]
        val versionPart = parts[2].substringBefore('@')
        val ext = parts.last().substringAfter('@', "jar")
        val classifier = if (parts.size >= 4) parts[3].substringBefore('@') else null

        val fileName = if (!classifier.isNullOrEmpty()) {
            "$artifact-$versionPart-$classifier.$ext"
        } else {
            "$artifact-$versionPart.$ext"
        }
        return "$groupPath/$artifact/$versionPart/$fileName"
    }

    private fun parseFabricLoadersArray(
        jsonStr: String,
        releaseTimes: Map<String, String> = emptyMap(),
    ): List<FabricLoaderVersionEntry> {
        val array = JSONArray(jsonStr)
        val result = ArrayList<FabricLoaderVersionEntry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val loaderObj = item.optJSONObject("loader") ?: item
            val version = loaderObj.optString("version").trim()
            if (version.isNotEmpty()) {
                result.add(
                    FabricLoaderVersionEntry(
                        version = version,
                        maven = loaderObj.optString("maven", ""),
                        stable = loaderObj.optBoolean("stable", false),
                        releaseTime = releaseTimes[version].orEmpty(),
                    ),
                )
            }
        }
        return result
    }
}
