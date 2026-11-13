package com.domrei.gameengine.runtime

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

/**
 * Supported OpenJDK runtime versions provided by ZalithLauncher2 for Android.
 */
enum class JavaRuntimeVersion(
    val major: Int,
    val dirName: String,
    val displayName: String,
    val supportedMinecraftRange: String
) {
    JRE_8(
        major = 8,
        dirName = "jre-8",
        displayName = "OpenJDK 8",
        supportedMinecraftRange = "Minecraft ≤ 1.16.5"
    ),
    JRE_17(
        major = 17,
        dirName = "jre-17",
        displayName = "OpenJDK 17",
        supportedMinecraftRange = "Minecraft 1.17 – 1.20.4"
    ),
    JRE_21(
        major = 21,
        dirName = "jre-21",
        displayName = "OpenJDK 21",
        supportedMinecraftRange = "Minecraft 1.20.5 – 1.21.x"
    ),
    JRE_25(
        major = 25,
        dirName = "jre-25",
        displayName = "OpenJDK 25",
        supportedMinecraftRange = "Minecraft 26.x+"
    );

    companion object {
        fun fromMajor(major: Int): JavaRuntimeVersion {
            return when {
                major >= 25 -> JRE_25
                major >= 21 -> JRE_21
                major >= 16 -> JRE_17
                else -> JRE_8
            }
        }
    }
}

/**
 * Supported Android device ABIs and their corresponding ZalithLauncher2 archive suffixes.
 */
enum class RuntimeAbi(
    val androidAbi: String,
    val zalithArchiveSuffix: String,
    val jre8ArchDir: String
) {
    ARM64(
        androidAbi = "arm64-v8a",
        zalithArchiveSuffix = "arm64",
        jre8ArchDir = "aarch64"
    ),
    ARM32(
        androidAbi = "armeabi-v7a",
        zalithArchiveSuffix = "arm",
        jre8ArchDir = "arm"
    ),
    X86_64(
        androidAbi = "x86_64",
        zalithArchiveSuffix = "x86_64",
        jre8ArchDir = "amd64"
    ),
    X86(
        androidAbi = "x86",
        zalithArchiveSuffix = "x86",
        jre8ArchDir = "i386"
    );

    val binArchiveFileName: String
        get() = "bin-$zalithArchiveSuffix.tar.xz"

    companion object {
        fun fromAndroidAbi(abi: String): RuntimeAbi? {
            return entries.firstOrNull { it.androidAbi.equals(abi, ignoreCase = true) }
        }

        fun detectDeviceAbi(): RuntimeAbi {
            val supportedAbis = try {
                Build.SUPPORTED_ABIS?.toList().orEmpty()
            } catch (_: Throwable) {
                emptyList()
            }
            for (abi in supportedAbis) {
                fromAndroidAbi(abi)?.let { return it }
            }
            return ARM64
        }
    }
}

data class RuntimeDownloadProgress(
    val runtimeVersion: JavaRuntimeVersion,
    val stage: String,
    val progressFraction: Float
)

data class InstalledJavaRuntime(
    val version: JavaRuntimeVersion,
    val abi: RuntimeAbi,
    val homeDir: File,
    val execHomeDir: File,
    val javaExecutable: File,
    val libJliSo: File?,
    val libJvmSo: File?,
    val libraryDirs: List<File>,
    val releaseVersionTag: String
) {
    val ldLibraryPath: String
        get() = libraryDirs.joinToString(File.pathSeparator) { it.absolutePath }
}

/**
 * Clean-room OpenJDK Runtime Manager that downloads, extracts, and verifies
 * Android OpenJDK builds (JRE 8, 17, 21, 25) from ZalithLauncher2's repository
 * directly into `/data/data/com.domrei.launcher/files/runtimes/` for maximum speed.
 */
class JdkManager(
    val externalRootDir: File,
    private val internalExecRootDir: File = externalRootDir
) {
    constructor(context: Context) : this(
        externalRootDir = File(context.filesDir, "runtimes"),
        internalExecRootDir = File(context.filesDir, "runtimes")
    )

    val runtimesDir: File
        get() = externalRootDir

    fun getRuntimeStorageDir(version: JavaRuntimeVersion): File {
        return File(externalRootDir, version.dirName)
    }

    fun getRuntimeExecDir(version: JavaRuntimeVersion): File {
        return File(internalExecRootDir, version.dirName)
    }

    fun buildUniversalArchiveUrl(version: JavaRuntimeVersion): String {
        return "$DOMREI_RUNTIMES_BASE_URL/${version.dirName}/$UNIVERSAL_ARCHIVE_NAME"
    }

    fun buildBinArchiveUrl(version: JavaRuntimeVersion, abi: RuntimeAbi): String {
        val resolvedAbi = if (version == JavaRuntimeVersion.JRE_25 && abi == RuntimeAbi.X86) {
            RuntimeAbi.X86_64
        } else {
            abi
        }
        return "$DOMREI_RUNTIMES_BASE_URL/${version.dirName}/${resolvedAbi.binArchiveFileName}"
    }

    fun buildVersionUrl(version: JavaRuntimeVersion): String {
        return "$DOMREI_RUNTIMES_BASE_URL/${version.dirName}/version"
    }

    /**
     * Checks whether the specified OpenJDK runtime is already extracted and valid on disk.
     */
    fun getInstalledRuntime(
        version: JavaRuntimeVersion,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()
    ): InstalledJavaRuntime? {
        val storageDir = getRuntimeStorageDir(version)
        val execDir = getRuntimeExecDir(version)
        val activeHome = when {
            File(execDir, "bin/java").isFile -> execDir
            File(storageDir, "bin/java").isFile -> storageDir
            else -> return null
        }
        val markerFile = File(activeHome, INSTALLED_MARKER_FILE).takeIf { it.isFile }
            ?: File(storageDir, INSTALLED_MARKER_FILE).takeIf { it.isFile }
            ?: return null

        val javaBin = File(activeHome, "bin/java")
        if (!javaBin.isFile) return null

        applyExecutablePermissions(activeHome)

        val libDirs = resolveLibraryDirectories(activeHome, abi)
        val libJvm = libDirs.map { File(it, "libjvm.so") }.firstOrNull { it.isFile }
        val libJli = libDirs.map { File(it, "libjli.so") }.firstOrNull { it.isFile }
        if (libJvm == null) return null

        val versionTag = runCatching { markerFile.readText().trim() }.getOrDefault("1")
        return InstalledJavaRuntime(
            version = version,
            abi = abi,
            homeDir = activeHome,
            execHomeDir = activeHome,
            javaExecutable = javaBin,
            libJliSo = libJli,
            libJvmSo = libJvm,
            libraryDirs = libDirs,
            releaseVersionTag = versionTag
        )
    }

    fun listInstalledRuntimes(
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()
    ): Map<JavaRuntimeVersion, InstalledJavaRuntime?> {
        return JavaRuntimeVersion.entries.associateWith { getInstalledRuntime(it, abi) }
    }

    /**
     * Ensures the specified OpenJDK version is downloaded into `/data/data/.../files/runtimes/`
     * and extracted for the target ABI.
     */
    suspend fun ensureRuntimeInstalled(
        version: JavaRuntimeVersion,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        forceReinstall: Boolean = false,
        onProgress: (RuntimeDownloadProgress) -> Unit = {}
    ): Result<InstalledJavaRuntime> = withContext(Dispatchers.IO) {
        runCatching {
            if (!forceReinstall) {
                getInstalledRuntime(version, abi)?.let { existing ->
                    onProgress(
                        RuntimeDownloadProgress(
                            runtimeVersion = version,
                            stage = "${version.displayName} ready",
                            progressFraction = 1f
                        )
                    )
                    return@runCatching existing
                }
            }

            val storageDir = getRuntimeStorageDir(version)
            val execDir = getRuntimeExecDir(version)
            val cacheDir = File(externalRootDir, ".cache/${version.dirName}")
            cacheDir.mkdirs()

            if (forceReinstall) {
                DomreiJliRunner.restoreWritablePermissions(storageDir)
                storageDir.deleteRecursively()
                if (execDir.absolutePath != storageDir.absolutePath) {
                    DomreiJliRunner.restoreWritablePermissions(execDir)
                    execDir.deleteRecursively()
                }
            }
            storageDir.mkdirs()
            execDir.mkdirs()

            val universalArchive = File(cacheDir, UNIVERSAL_ARCHIVE_NAME)
            val binArchive = File(cacheDir, abi.binArchiveFileName)

            onProgress(
                RuntimeDownloadProgress(
                    runtimeVersion = version,
                    stage = "Downloading ${version.displayName} universal package...",
                    progressFraction = 0.03f
                )
            )
            downloadFileWithProgress(
                url = buildUniversalArchiveUrl(version),
                targetFile = universalArchive
            ) { fraction ->
                onProgress(
                    RuntimeDownloadProgress(
                        runtimeVersion = version,
                        stage = "Downloading ${version.displayName} universal (${(fraction * 100).toInt()}%)",
                        progressFraction = 0.03f + fraction * 0.42f
                    )
                )
            }

            coroutineContext.ensureActive()

            onProgress(
                RuntimeDownloadProgress(
                    runtimeVersion = version,
                    stage = "Downloading ${version.displayName} (${abi.androidAbi}) binaries...",
                    progressFraction = 0.45f
                )
            )
            downloadFileWithProgress(
                url = buildBinArchiveUrl(version, abi),
                targetFile = binArchive
            ) { fraction ->
                onProgress(
                    RuntimeDownloadProgress(
                        runtimeVersion = version,
                        stage = "Downloading ${version.displayName} ${abi.androidAbi} (${(fraction * 100).toInt()}%)",
                        progressFraction = 0.45f + fraction * 0.25f
                    )
                )
            }

            val remoteVersionTag = runCatching {
                downloadString(buildVersionUrl(version)).trim()
            }.getOrDefault("2")

            coroutineContext.ensureActive()

            onProgress(
                RuntimeDownloadProgress(
                    runtimeVersion = version,
                    stage = "Extracting ${version.displayName} universal files...",
                    progressFraction = 0.72f
                )
            )
            extractTarXzArchive(universalArchive, storageDir)

            onProgress(
                RuntimeDownloadProgress(
                    runtimeVersion = version,
                    stage = "Extracting ${version.displayName} (${abi.androidAbi}) binaries...",
                    progressFraction = 0.85f
                )
            )
            extractTarXzArchive(binArchive, storageDir)

            if (execDir.absolutePath != storageDir.absolutePath) {
                onProgress(
                    RuntimeDownloadProgress(
                        runtimeVersion = version,
                        stage = "Preparing ${version.displayName} executable runtime...",
                        progressFraction = 0.93f
                    )
                )
                extractTarXzArchive(universalArchive, execDir)
                extractTarXzArchive(binArchive, execDir)
                applyExecutablePermissions(execDir)
            }
            applyExecutablePermissions(storageDir)

            universalArchive.delete()
            binArchive.delete()

            File(storageDir, INSTALLED_MARKER_FILE).writeText(remoteVersionTag)
            if (execDir.absolutePath != storageDir.absolutePath) {
                File(execDir, INSTALLED_MARKER_FILE).writeText(remoteVersionTag)
            }

            val installed = getInstalledRuntime(version, abi)
                ?: throw IOException("Failed to verify extracted ${version.displayName} in ${storageDir.absolutePath}")

            onProgress(
                RuntimeDownloadProgress(
                    runtimeVersion = version,
                    stage = "${version.displayName} installed",
                    progressFraction = 1f
                )
            )
            installed
        }
    }

    /**
     * Extracts a `.tar.xz` archive into `destinationDir`, preserving symbolic links and permissions.
     */
    fun extractTarXzArchive(archiveFile: File, destinationDir: File) {
        destinationDir.mkdirs()
        val canonicalDestPath = destinationDir.canonicalPath + File.separator
        val pendingSymlinks = mutableListOf<Pair<File, String>>()

        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis, 64 * 1024).use { bis ->
                XZInputStream(bis).use { xzis ->
                    TarArchiveInputStream(xzis).use { tarIn ->
                        var entry = tarIn.nextEntry
                        val buffer = ByteArray(32 * 1024)
                        while (entry != null) {
                            val cleanName = entry.name.removePrefix("./").removePrefix("/")
                            if (cleanName.isNotEmpty()) {
                                val outFile = File(destinationDir, cleanName)
                                val canonicalOutPath = outFile.canonicalPath
                                if (!canonicalOutPath.startsWith(canonicalDestPath) &&
                                    canonicalOutPath != destinationDir.canonicalPath
                                ) {
                                    throw IOException("Blocked zip-slip tar entry: ${entry.name}")
                                }

                                when {
                                    entry.isDirectory -> {
                                        outFile.mkdirs()
                                    }
                                    entry.isSymbolicLink -> {
                                        pendingSymlinks += outFile to entry.linkName
                                    }
                                    else -> {
                                        outFile.parentFile?.mkdirs()
                                        if (outFile.exists()) {
                                            outFile.setWritable(true, true)
                                            outFile.delete()
                                        }
                                        FileOutputStream(outFile).use { fos ->
                                            while (true) {
                                                val read = tarIn.read(buffer)
                                                if (read <= 0) break
                                                fos.write(buffer, 0, read)
                                            }
                                        }
                                        if ((entry.mode and 0b001_001_001) != 0 ||
                                            cleanName.startsWith("bin/") ||
                                            cleanName.endsWith(".so") ||
                                            cleanName.endsWith("jexec") ||
                                            cleanName.endsWith("jspawnhelper")
                                        ) {
                                            outFile.setExecutable(true, false)
                                        }
                                    }
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }

        for ((linkFile, targetPath) in pendingSymlinks) {
            linkFile.parentFile?.mkdirs()
            runCatching {
                if (linkFile.exists() || Files.isSymbolicLink(linkFile.toPath())) {
                    Files.deleteIfExists(linkFile.toPath())
                }
                Files.createSymbolicLink(linkFile.toPath(), File(targetPath).toPath())
            }.onFailure {
                val resolvedTarget = File(linkFile.parentFile, targetPath)
                if (resolvedTarget.isFile) {
                    resolvedTarget.copyTo(linkFile, overwrite = true)
                    linkFile.setExecutable(true, false)
                }
            }
        }
    }

    private fun applyExecutablePermissions(homeDir: File) {
        File(homeDir, "bin").listFiles()?.forEach { file ->
            file.setReadable(true, false)
            file.setExecutable(true, false)
            file.setWritable(false, false)
        }
        File(homeDir, "lib").walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".so") || file.name == "jexec" || file.name == "jspawnhelper")) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
                file.setWritable(false, false)
            }
        }
    }

    private fun resolveLibraryDirectories(homeDir: File, abi: RuntimeAbi): List<File> {
        val candidates = listOf(
            File(homeDir, "lib"),
            File(homeDir, "lib/server"),
            File(homeDir, "lib/jli"),
            File(homeDir, "lib/${abi.jre8ArchDir}"),
            File(homeDir, "lib/${abi.jre8ArchDir}/server"),
            File(homeDir, "lib/${abi.jre8ArchDir}/jli"),
            File(homeDir, "jre/lib/${abi.jre8ArchDir}"),
            File(homeDir, "jre/lib/${abi.jre8ArchDir}/server"),
            File(homeDir, "jre/lib/${abi.jre8ArchDir}/jli")
        )
        return candidates.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    private fun downloadString(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code from $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFileWithProgress(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code downloading $url")
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DOMREI_RUNTIMES_BASE_URL =
            "https://raw.githubusercontent.com/STMCGaming/Domrei-Runtimes/main/runtimes"
        const val UNIVERSAL_ARCHIVE_NAME = "universal.tar.xz"
        private const val INSTALLED_MARKER_FILE = ".domrei_runtime_version"
        private const val USER_AGENT = "DomreiLauncher-GameEngine/1.0"

        /**
         * Selects the appropriate OpenJDK runtime version for a given Minecraft version
         * and optional Mojang JSON `javaVersion.majorVersion`.
         */
        fun selectRuntimeForMinecraft(
            mcVersion: String,
            requiredJavaMajor: Int? = null
        ): JavaRuntimeVersion {
            if (requiredJavaMajor != null && requiredJavaMajor > 0) {
                return JavaRuntimeVersion.fromMajor(requiredJavaMajor)
            }

            val trimmed = mcVersion.trim().lowercase()

            // Snapshots like "26w14a", "25w02a", "24w33a", "21w19a"
            val snapshotMatch = Regex("""^(\d{2})w(\d{2})[a-z]$""").find(trimmed)
            if (snapshotMatch != null) {
                val year = snapshotMatch.groupValues[1].toIntOrNull() ?: 0
                val week = snapshotMatch.groupValues[2].toIntOrNull() ?: 0
                return when {
                    year >= 26 -> JavaRuntimeVersion.JRE_25
                    year >= 24 || (year == 23 && week >= 51) -> JavaRuntimeVersion.JRE_21
                    year >= 21 -> JavaRuntimeVersion.JRE_17
                    else -> JavaRuntimeVersion.JRE_8
                }
            }

            // Legacy Beta / Alpha / Classic / Infdev / Pre-classic versions
            if (trimmed.startsWith("b1.") ||
                trimmed.startsWith("a1.") ||
                trimmed.startsWith("c0.") ||
                trimmed.startsWith("inf-") ||
                trimmed.startsWith("in-") ||
                trimmed.startsWith("rd-")
            ) {
                return JavaRuntimeVersion.JRE_8
            }

            // Standard semantic or year-based releases: e.g. "26.1", "1.21.4", "1.20.5", "1.18.2", "1.16.5"
            val numericParts = trimmed
                .substringBefore('-')
                .split('.')
                .mapNotNull { it.toIntOrNull() }

            if (numericParts.isNotEmpty()) {
                val first = numericParts[0]
                val second = numericParts.getOrElse(1) { 0 }
                val third = numericParts.getOrElse(2) { 0 }

                if (first >= 26) {
                    return JavaRuntimeVersion.JRE_25
                }
                if (first == 1) {
                    return when {
                        second >= 21 -> JavaRuntimeVersion.JRE_21
                        second == 20 && third >= 5 -> JavaRuntimeVersion.JRE_21
                        second >= 17 -> JavaRuntimeVersion.JRE_17
                        else -> JavaRuntimeVersion.JRE_8
                    }
                }
            }

            return JavaRuntimeVersion.JRE_21
        }
    }
}
