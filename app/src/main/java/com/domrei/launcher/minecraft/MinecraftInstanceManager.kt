package com.domrei.launcher.minecraft

import android.content.Context
import com.domrei.launcher.InstanceLoaderType
import com.domrei.launcher.LauncherInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

enum class DownloadStageStatus {
    WAITING,
    DOWNLOADING,
    DONE,
    ERROR,
}

data class DownloadFileItem(
    val fileName: String,
    val sizeMb: Float,
    val progress: Float = 0f,
    val status: DownloadStageStatus = DownloadStageStatus.WAITING,
)

data class InstallProgressSnapshot(
    val progress: Float,
    val currentFileName: String,
    val downloadedMb: Float,
    val totalMb: Float,
    val speedMbPerSec: Float,
    val files: List<DownloadFileItem>,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Manages real persisted Minecraft instances on disk and downloads game files
 * (version JSON, client JAR, libraries, asset index, asset objects, and Fabric loader profile)
 * from the official Mojang and Fabric APIs.
 */
class MinecraftInstanceManager(
    val rootDir: File,
) {
    constructor(context: Context) : this(resolveDefaultRootDir(context))

    private val instancesDir: File get() = File(rootDir, "instances")
    private val instancesIndexFile: File get() = File(instancesDir, "instances.json")

    init {
        ensureBaseDirectories()
    }

    private fun ensureBaseDirectories() {
        listOf(
            rootDir,
            instancesDir,
            File(rootDir, "versions"),
            File(rootDir, "libraries"),
            File(rootDir, "assets/indexes"),
            File(rootDir, "assets/objects"),
        ).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    fun gameDirFor(instanceId: String): File = File(File(instancesDir, instanceId), "gamedir")

    fun loadInstances(): List<LauncherInstance> {
        val loaded = LinkedHashMap<String, LauncherInstance>()

        if (instancesIndexFile.isFile) {
            runCatching {
                val arr = JSONArray(instancesIndexFile.readText(Charsets.UTF_8))
                for (i in 0 until arr.length()) {
                    parseInstanceJson(arr.getJSONObject(i))?.let { inst ->
                        loaded[inst.id] = inst
                    }
                }
            }
        }

        if (loaded.isEmpty() && instancesDir.isDirectory) {
            instancesDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val file = File(dir, "instance.json")
                    if (file.isFile) {
                        runCatching {
                            parseInstanceJson(JSONObject(file.readText(Charsets.UTF_8)))?.let { inst ->
                                loaded[inst.id] = inst
                            }
                        }
                    }
                }
            }
        }

        val bogusIds = loaded.keys.filter { id ->
            id.startsWith("launch_") || id.startsWith("jre_") || id.startsWith("renderer_")
        }
        if (bogusIds.isNotEmpty()) {
            bogusIds.forEach { id ->
                loaded.remove(id)
                File(instancesDir, id).deleteRecursively()
            }
            writeInstancesIndex(loaded.values.sortedByDescending { it.createdAt })
        }

        return loaded.values.sortedByDescending { it.createdAt }
    }

    fun saveInstance(instance: LauncherInstance) {
        val instDir = File(instancesDir, instance.id)
        if (!instDir.exists()) instDir.mkdirs()
        val gamedir = gameDirFor(instance.id)
        if (!gamedir.exists()) gamedir.mkdirs()

        val json = instanceToJson(instance)
        writeTextAtomic(File(instDir, "instance.json"), json.toString(2))

        val current = loadInstances().filterNot { it.id == instance.id }
        val updated = listOf(instance) + current
        writeInstancesIndex(updated)
    }

    fun removeInstance(instanceId: String) {
        val instDir = File(instancesDir, instanceId)
        if (instDir.exists()) {
            instDir.deleteRecursively()
        }
        val remaining = loadInstances().filterNot { it.id == instanceId }
        writeInstancesIndex(remaining)
    }

    private fun writeInstancesIndex(instances: List<LauncherInstance>) {
        if (!instancesDir.exists()) instancesDir.mkdirs()
        val arr = JSONArray()
        instances.forEach { arr.put(instanceToJson(it)) }
        writeTextAtomic(instancesIndexFile, arr.toString(2))
    }

    private fun instanceToJson(instance: LauncherInstance): JSONObject = JSONObject().apply {
        put("id", instance.id)
        put("name", instance.name)
        put("mcVersion", instance.mcVersion)
        put("loaderType", instance.loaderType.name)
        if (instance.loaderVersion != null) {
            put("loaderVersion", instance.loaderVersion)
        } else {
            put("loaderVersion", JSONObject.NULL)
        }
        put("createdAt", instance.createdAt)
        put("versionJsonPath", instance.versionJsonPath)
        put("clientJarPath", instance.clientJarPath)
        put("assetIndexId", instance.assetIndexId)
        put("mainClass", instance.mainClass)
    }

    private fun parseInstanceJson(json: JSONObject): LauncherInstance? {
        val id = json.optString("id").trim()
        val name = json.optString("name").trim()
        val mcVersion = json.optString("mcVersion").trim()
        if (id.isEmpty() || name.isEmpty() || mcVersion.isEmpty()) return null

        val loaderName = json.optString("loaderType", InstanceLoaderType.VANILLA.name)
        val loaderType = runCatching { InstanceLoaderType.valueOf(loaderName) }
            .getOrDefault(InstanceLoaderType.VANILLA)
        val rawLoaderVer = json.opt("loaderVersion")
        val loaderVersion = if (rawLoaderVer == null || rawLoaderVer == JSONObject.NULL) {
            null
        } else {
            rawLoaderVer.toString().takeIf { it.isNotBlank() }
        }

        return LauncherInstance(
            id = id,
            name = name,
            mcVersion = mcVersion,
            loaderType = loaderType,
            loaderVersion = loaderVersion,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            versionJsonPath = json.optString("versionJsonPath", ""),
            clientJarPath = json.optString("clientJarPath", ""),
            assetIndexId = json.optString("assetIndexId", ""),
            mainClass = json.optString("mainClass", ""),
        )
    }

    /**
     * Downloads all official Minecraft (and optional Fabric) files for the requested instance
     * and persists it on disk.
     */
    suspend fun installInstance(
        taskId: String,
        instanceName: String,
        loaderType: InstanceLoaderType,
        mcVersion: String,
        loaderVersion: String?,
        includeAssets: Boolean = true,
        onProgress: (InstallProgressSnapshot) -> Unit,
    ): Result<LauncherInstance> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        val stages = mutableListOf(
            DownloadFileItem("versions/$mcVersion/$mcVersion.json", 0.2f, 0f, DownloadStageStatus.DOWNLOADING),
            DownloadFileItem("versions/$mcVersion/$mcVersion.jar", 25.0f, 0f, DownloadStageStatus.WAITING),
            DownloadFileItem("libraries/ (resolving…)", 20.0f, 0f, DownloadStageStatus.WAITING),
            DownloadFileItem("assets/indexes/ (resolving…)", 0.5f, 0f, DownloadStageStatus.WAITING),
        )
        if (includeAssets) {
            stages.add(DownloadFileItem("assets/objects/ (resolving…)", 200.0f, 0f, DownloadStageStatus.WAITING))
        }
        val isFabric = loaderType == InstanceLoaderType.FABRIC && !loaderVersion.isNullOrBlank()
        if (isFabric) {
            stages.add(
                DownloadFileItem(
                    "fabric-loader-$loaderVersion-$mcVersion",
                    2.0f,
                    0f,
                    DownloadStageStatus.WAITING,
                ),
            )
        }

        val versionJsonIdx = 0
        val clientJarIdx = 1
        val librariesIdx = 2
        val assetIndexIdx = 3
        val assetsObjectsIdx = if (includeAssets) 4 else -1
        val fabricIdx = if (isFabric) (if (includeAssets) 5 else 4) else -1

        var totalBytesPlanned = 1L
        val downloadedBytesAtomic = AtomicLong(0L)
        var lastEmitTimeMs = 0L

        fun emitSnapshot(
            currentFile: String,
            force: Boolean = false,
            isComplete: Boolean = false,
            errorMsg: String? = null,
        ) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmitTimeMs < 50L) return
            lastEmitTimeMs = now

            val downloaded = downloadedBytesAtomic.get().coerceAtMost(totalBytesPlanned)
            val overallProgress = if (isComplete) {
                1f
            } else {
                (downloaded.toDouble() / totalBytesPlanned.coerceAtLeast(1L).toDouble())
                    .toFloat()
                    .coerceIn(0f, 0.999f)
            }
            val elapsedSec = ((now - startTimeMs).coerceAtLeast(100L)) / 1000f
            val downloadedMb = downloaded / (1024f * 1024f)
            val totalMb = totalBytesPlanned / (1024f * 1024f)
            val speedMbPerSec = (downloadedMb / elapsedSec).coerceAtLeast(0f)

            onProgress(
                InstallProgressSnapshot(
                    progress = overallProgress,
                    currentFileName = currentFile,
                    downloadedMb = downloadedMb,
                    totalMb = totalMb.coerceAtLeast(downloadedMb),
                    speedMbPerSec = speedMbPerSec,
                    files = stages.toList(),
                    isComplete = isComplete,
                    errorMessage = errorMsg,
                ),
            )
        }

        val result = runCatching {
            emitSnapshot("versions/$mcVersion/$mcVersion.json", force = true)

            // 1. Resolve version entry from official version_manifest_v2.json
            val manifest = OfficialMinecraftApi.fetchVersionManifest().getOrThrow()
            val versionEntry = manifest.versions.firstOrNull { it.id == mcVersion }
                ?: error("Minecraft version $mcVersion not found in official manifest")

            // 2. Download & verify <mcVersion>.json
            val versionJsonRelPath = "versions/$mcVersion/$mcVersion.json"
            val versionJsonFile = File(rootDir, versionJsonRelPath)
            val versionJsonBytes = ensureJsonFile(
                targetFile = versionJsonFile,
                url = versionEntry.url,
                expectedSha1 = versionEntry.sha1.takeIf { it.isNotEmpty() },
            )
            val versionJsonStr = versionJsonFile.readText(Charsets.UTF_8)
            val parsedVersion = OfficialMinecraftApi.parseVersionPackageJson(mcVersion, versionJsonStr)

            stages[versionJsonIdx] = DownloadFileItem(
                fileName = versionJsonRelPath,
                sizeMb = bytesToMb(versionJsonBytes),
                progress = 1f,
                status = DownloadStageStatus.DONE,
            )

            // 3. If Fabric, download & parse Fabric profile JSON
            var parsedFabric: ParsedFabricProfile? = null
            var fabricProfileBytes = 0L
            if (isFabric && fabricIdx >= 0) {
                val fabricId = "fabric-loader-$loaderVersion-$mcVersion"
                val fabricProfileFile = File(rootDir, "versions/$fabricId/$fabricId.json")
                val fabricUrl = OfficialMinecraftApi.fabricProfileJsonUrl(mcVersion, loaderVersion!!)
                fabricProfileBytes = ensureJsonFile(
                    targetFile = fabricProfileFile,
                    url = fabricUrl,
                    expectedSha1 = null,
                )
                parsedFabric = OfficialMinecraftApi.parseFabricProfileJson(
                    fabricProfileFile.readText(Charsets.UTF_8),
                )
            }

            // 4. Download & verify assetIndex JSON
            val assetIndexRelPath = "assets/indexes/${parsedVersion.assetIndex.id}.json"
            val assetIndexFile = File(rootDir, assetIndexRelPath)
            stages[assetIndexIdx] = DownloadFileItem(
                fileName = assetIndexRelPath,
                sizeMb = bytesToMb(parsedVersion.assetIndex.sizeBytes.coerceAtLeast(1L)),
                progress = 0.2f,
                status = DownloadStageStatus.DOWNLOADING,
            )
            emitSnapshot(assetIndexRelPath, force = true)

            val assetIndexBytes = ensureJsonFile(
                targetFile = assetIndexFile,
                url = parsedVersion.assetIndex.url,
                expectedSha1 = parsedVersion.assetIndex.sha1.takeIf { it.isNotEmpty() },
            )
            val assetObjects = if (includeAssets) {
                OfficialMinecraftApi.parseAssetIndexJson(assetIndexFile.readText(Charsets.UTF_8))
            } else {
                emptyList()
            }

            stages[assetIndexIdx] = DownloadFileItem(
                fileName = assetIndexRelPath,
                sizeMb = bytesToMb(assetIndexBytes),
                progress = 1f,
                status = DownloadStageStatus.DONE,
            )

            // 5. Compute exact planned byte sizes across all stages
            val clientBytesTotal = parsedVersion.clientJar.sizeBytes.coerceAtLeast(1L)
            val mojangLibs = parsedVersion.libraries
            val mojangLibsBytesTotal = mojangLibs.sumOf { it.sizeBytes.coerceAtLeast(16_384L) }
            val fabricLibs = parsedFabric?.libraries.orEmpty()
            val fabricLibsBytesTotal = fabricProfileBytes + fabricLibs.sumOf { it.sizeBytes.coerceAtLeast(16_384L) }
            val assetsBytesTotal = assetObjects.sumOf { it.sizeBytes.coerceAtLeast(1_024L) }

            totalBytesPlanned = versionJsonBytes +
                assetIndexBytes +
                clientBytesTotal +
                mojangLibsBytesTotal +
                fabricLibsBytesTotal +
                assetsBytesTotal

            downloadedBytesAtomic.set(versionJsonBytes + assetIndexBytes + fabricProfileBytes)

            stages[clientJarIdx] = DownloadFileItem(
                fileName = parsedVersion.clientJar.relativePath,
                sizeMb = bytesToMb(clientBytesTotal),
                progress = 0f,
                status = DownloadStageStatus.WAITING,
            )
            stages[librariesIdx] = DownloadFileItem(
                fileName = "libraries/ (${mojangLibs.size} files)",
                sizeMb = bytesToMb(mojangLibsBytesTotal),
                progress = 0f,
                status = DownloadStageStatus.WAITING,
            )
            if (assetsObjectsIdx >= 0) {
                stages[assetsObjectsIdx] = DownloadFileItem(
                    fileName = "assets/objects/ (${assetObjects.size} files)",
                    sizeMb = bytesToMb(assetsBytesTotal),
                    progress = 0f,
                    status = DownloadStageStatus.WAITING,
                )
            }
            if (fabricIdx >= 0) {
                stages[fabricIdx] = DownloadFileItem(
                    fileName = "Fabric $loaderVersion (${fabricLibs.size} libs)",
                    sizeMb = bytesToMb(fabricLibsBytesTotal),
                    progress = 0f,
                    status = DownloadStageStatus.WAITING,
                )
            }
            emitSnapshot(parsedVersion.clientJar.relativePath, force = true)

            // 6. Download client JAR with live streaming progress + SHA-1 check
            stages[clientJarIdx] = stages[clientJarIdx].copy(status = DownloadStageStatus.DOWNLOADING)
            val clientTarget = File(rootDir, parsedVersion.clientJar.relativePath)
            var clientDownloaded = 0L
            downloadArtifactWithRetry(
                artifact = parsedVersion.clientJar,
                targetFile = clientTarget,
                onBytesDelta = { delta ->
                    clientDownloaded += delta
                    downloadedBytesAtomic.addAndGet(delta)
                    val stageProg = (clientDownloaded.toDouble() / clientBytesTotal.toDouble())
                        .toFloat()
                        .coerceIn(0f, 1f)
                    stages[clientJarIdx] = stages[clientJarIdx].copy(
                        progress = stageProg,
                        status = DownloadStageStatus.DOWNLOADING,
                    )
                    emitSnapshot(parsedVersion.clientJar.relativePath)
                },
            )
            stages[clientJarIdx] = stages[clientJarIdx].copy(
                progress = 1f,
                status = DownloadStageStatus.DONE,
            )
            emitSnapshot(parsedVersion.clientJar.relativePath, force = true)

            // 7. Download Fabric libraries concurrently (if Fabric)
            if (fabricIdx >= 0 && parsedFabric != null) {
                stages[fabricIdx] = stages[fabricIdx].copy(status = DownloadStageStatus.DOWNLOADING)
                val fabricCompletedBytes = AtomicLong(fabricProfileBytes)
                downloadBatchConcurrently(
                    artifacts = fabricLibs,
                    concurrency = 8,
                    onFileProgress = { artifact, delta ->
                        val done = fabricCompletedBytes.addAndGet(delta)
                        downloadedBytesAtomic.addAndGet(delta)
                        val stageProg = (done.toDouble() / fabricLibsBytesTotal.coerceAtLeast(1L).toDouble())
                            .toFloat()
                            .coerceIn(0f, 1f)
                        stages[fabricIdx] = stages[fabricIdx].copy(
                            progress = stageProg,
                            status = DownloadStageStatus.DOWNLOADING,
                        )
                        emitSnapshot(artifact.displayName)
                    },
                )
                stages[fabricIdx] = stages[fabricIdx].copy(
                    progress = 1f,
                    status = DownloadStageStatus.DONE,
                )
                emitSnapshot(stages[fabricIdx].fileName, force = true)
            }

            // 8. Download Mojang libraries concurrently
            stages[librariesIdx] = stages[librariesIdx].copy(status = DownloadStageStatus.DOWNLOADING)
            val libsCompletedBytes = AtomicLong(0L)
            downloadBatchConcurrently(
                artifacts = mojangLibs,
                concurrency = 8,
                onFileProgress = { artifact, delta ->
                    val done = libsCompletedBytes.addAndGet(delta)
                    downloadedBytesAtomic.addAndGet(delta)
                    val stageProg = (done.toDouble() / mojangLibsBytesTotal.coerceAtLeast(1L).toDouble())
                        .toFloat()
                        .coerceIn(0f, 1f)
                    stages[librariesIdx] = stages[librariesIdx].copy(
                        progress = stageProg,
                        status = DownloadStageStatus.DOWNLOADING,
                    )
                    emitSnapshot(artifact.displayName)
                },
            )
            stages[librariesIdx] = stages[librariesIdx].copy(
                progress = 1f,
                status = DownloadStageStatus.DONE,
            )
            emitSnapshot(stages[librariesIdx].fileName, force = true)

            // 9. Download asset objects concurrently (32 workers, throttled UI callbacks)
            if (includeAssets && assetsObjectsIdx >= 0) {
                stages[assetsObjectsIdx] = stages[assetsObjectsIdx].copy(status = DownloadStageStatus.DOWNLOADING)
                val assetsCompletedBytes = AtomicLong(0L)
                downloadBatchConcurrently(
                    artifacts = assetObjects,
                    concurrency = 32,
                    onFileProgress = { artifact, delta ->
                        val done = assetsCompletedBytes.addAndGet(delta)
                        downloadedBytesAtomic.addAndGet(delta)
                        val stageProg = (done.toDouble() / assetsBytesTotal.coerceAtLeast(1L).toDouble())
                            .toFloat()
                            .coerceIn(0f, 1f)
                        stages[assetsObjectsIdx] = stages[assetsObjectsIdx].copy(
                            progress = stageProg,
                            status = DownloadStageStatus.DOWNLOADING,
                        )
                        emitSnapshot(artifact.displayName)
                    },
                )
                stages[assetsObjectsIdx] = stages[assetsObjectsIdx].copy(
                    progress = 1f,
                    status = DownloadStageStatus.DONE,
                )
            }

            // 10. Persist LauncherInstance to disk
            val mainClass = parsedFabric?.mainClass ?: parsedVersion.mainClass
            val instance = LauncherInstance(
                id = taskId,
                name = instanceName,
                mcVersion = mcVersion,
                loaderType = loaderType,
                loaderVersion = loaderVersion,
                createdAt = System.currentTimeMillis(),
                versionJsonPath = versionJsonRelPath,
                clientJarPath = parsedVersion.clientJar.relativePath,
                assetIndexId = parsedVersion.assetIndex.id,
                mainClass = mainClass,
            )
            saveInstance(instance)
            emitSnapshot(instanceName, force = true, isComplete = true)
            instance
        }

        result.onFailure { err ->
            val currentIdx = stages.indexOfFirst { it.status == DownloadStageStatus.DOWNLOADING }
            if (currentIdx >= 0) {
                stages[currentIdx] = stages[currentIdx].copy(status = DownloadStageStatus.ERROR)
            }
            emitSnapshot(
                currentFile = err.message ?: "Download failed",
                force = true,
                isComplete = false,
                errorMsg = err.message ?: "Download failed",
            )
        }

        result
    }

    private suspend fun downloadBatchConcurrently(
        artifacts: List<DownloadArtifact>,
        concurrency: Int,
        onFileProgress: (artifact: DownloadArtifact, deltaBytes: Long) -> Unit,
    ) = coroutineScope {
        val semaphore = Semaphore(concurrency)
        artifacts.map { artifact ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val targetFile = File(rootDir, artifact.relativePath)
                    downloadArtifactWithRetry(
                        artifact = artifact,
                        targetFile = targetFile,
                        onBytesDelta = { delta -> onFileProgress(artifact, delta) },
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun downloadArtifactWithRetry(
        artifact: DownloadArtifact,
        targetFile: File,
        onBytesDelta: (Long) -> Unit,
    ) {
        // Skip already-valid files on disk
        if (isValidCachedFile(targetFile, artifact.sha1, artifact.sizeBytes)) {
            val existingSize = targetFile.length().coerceAtLeast(artifact.sizeBytes)
            if (existingSize > 0L) onBytesDelta(existingSize)
            return
        }

        var lastError: Throwable? = null
        for (attempt in 1..3) {
            var attemptBytesReported = 0L
            try {
                val downloaded = streamDownloadToFile(
                    urlString = artifact.url,
                    targetFile = targetFile,
                    expectedSha1 = artifact.sha1,
                    onBytesDelta = { delta ->
                        attemptBytesReported += delta
                        onBytesDelta(delta)
                    },
                )
                if (downloaded == 0L && artifact.sizeBytes > 0L) {
                    onBytesDelta(artifact.sizeBytes)
                }
                return
            } catch (t: Throwable) {
                lastError = t
                if (attemptBytesReported > 0L) {
                    onBytesDelta(-attemptBytesReported)
                }
                if (attempt < 3) {
                    delay(250L * attempt)
                }
            }
        }
        throw lastError ?: IllegalStateException("Failed to download ${artifact.url}")
    }

    private fun ensureJsonFile(
        targetFile: File,
        url: String,
        expectedSha1: String?,
    ): Long {
        if (isValidCachedFile(targetFile, expectedSha1, expectedSize = 0L)) {
            return targetFile.length()
        }
        return streamDownloadToFile(
            urlString = url,
            targetFile = targetFile,
            expectedSha1 = expectedSha1,
            onBytesDelta = {},
        )
    }

    private fun isValidCachedFile(
        file: File,
        expectedSha1: String?,
        expectedSize: Long,
    ): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        if (expectedSize > 0L && file.length() != expectedSize) return false
        if (!expectedSha1.isNullOrBlank()) {
            return computeSha1(file).equals(expectedSha1, ignoreCase = true)
        }
        return true
    }

    private fun streamDownloadToFile(
        urlString: String,
        targetFile: File,
        expectedSha1: String?,
        onBytesDelta: (Long) -> Unit,
    ): Long {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        val tempFile = File(parent, "${targetFile.name}.${java.util.UUID.randomUUID()}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DomreiLauncher/1.0")
        }

        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                error("HTTP $code downloading $urlString")
            }

            val digest = if (!expectedSha1.isNullOrBlank()) MessageDigest.getInstance("SHA-1") else null
            var totalRead = 0L
            val buffer = ByteArray(16 * 1024)

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        totalRead += read
                        onBytesDelta(read.toLong())
                    }
                    output.flush()
                }
            }

            if (digest != null && !expectedSha1.isNullOrBlank()) {
                val actualSha1 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                    tempFile.delete()
                    error("SHA-1 mismatch for ${targetFile.name}: expected $expectedSha1, got $actualSha1")
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return totalRead
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }

    private fun writeTextAtomic(targetFile: File, text: String) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(parent, "${targetFile.name}.tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (targetFile.exists()) targetFile.delete()
        if (!temp.renameTo(targetFile)) {
            temp.copyTo(targetFile, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        fun resolveDefaultRootDir(context: Context): File {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val targetDir = File(baseDir, "minecraft")
            val legacyDir = File(context.filesDir, "minecraft")
            if (legacyDir != targetDir && legacyDir.isDirectory) {
                val targetInstances = File(targetDir, "instances")
                val targetIsEmpty = !targetInstances.exists() || targetInstances.listFiles().isNullOrEmpty()
                if (targetIsEmpty && !legacyDir.listFiles().isNullOrEmpty()) {
                    runCatching {
                        legacyDir.copyRecursively(targetDir, overwrite = false)
                    }
                }
            }
            return targetDir
        }

        fun computeSha1(file: File): String {
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(16 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun bytesToMb(bytes: Long): Float =
            (bytes / (1024f * 1024f)).coerceAtLeast(0.01f)
    }
}
