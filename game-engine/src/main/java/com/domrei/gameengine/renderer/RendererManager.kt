package com.domrei.gameengine.renderer

import android.content.Context
import com.domrei.gameengine.runtime.RuntimeAbi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Manages MobileGlues (`libmobileglues.so`) and Krypton Wrapper (`libng_gl4es.so`)
 * native renderer binaries, configuration files, environment variables, and JVM flags.
 */
class RendererManager(
    val externalRenderersDir: File,
    private val internalExecRenderersDir: File = externalRenderersDir,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        externalRenderersDir = File(context.filesDir, "renderers"),
        internalExecRenderersDir = File(context.filesDir, "renderers"),
        appContext = context.applicationContext
    )

    private val preferenceFile: File
        get() = File(externalRenderersDir, "selected_renderer.txt")

    fun getSelectedRenderer(): RendererType {
        val savedId = runCatching {
            if (preferenceFile.isFile) preferenceFile.readText().trim() else null
        }.getOrNull()
        return RendererType.fromId(savedId)
    }

    fun setSelectedRenderer(type: RendererType) {
        externalRenderersDir.mkdirs()
        preferenceFile.writeText(type.id)
    }

    fun isRendererInstalled(
        type: RendererType,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()
    ): Boolean {
        val extLib = File(getRendererAbiDir(externalRenderersDir, type, abi), type.nativeLibFileName)
        val execLib = File(getRendererAbiDir(internalExecRenderersDir, type, abi), type.nativeLibFileName)
        return (extLib.isFile && extLib.length() > 0L) || (execLib.isFile && execLib.length() > 0L)
    }

    fun getRendererNativeFile(
        type: RendererType,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()
    ): File {
        val execFile = File(getRendererAbiDir(internalExecRenderersDir, type, abi), type.nativeLibFileName)
        if (execFile.isFile && execFile.length() > 0L) {
            return execFile
        }
        return File(getRendererAbiDir(externalRenderersDir, type, abi), type.nativeLibFileName)
    }

    fun buildRendererAarUrl(type: RendererType): String {
        return "$DOMREI_LIBS_BASE_URL/${type.aarFileName}"
    }

    /**
     * Returns the ordered list of Android LWJGL 3.3.3 + Domrei GLFW bridge jars when prepared.
     */
    fun getAndroidLwjglJars(): List<File> {
        val jarsDir = File(internalExecRenderersDir, "lwjgl/3.3.3/jars")
        if (!jarsDir.isDirectory) return emptyList()
        val files = jarsDir.listFiles { file -> file.isFile && file.name.endsWith(".jar") && file.length() > 0L }
            ?: return emptyList()
        if (files.none { it.name == "lwjgl.jar" }) return emptyList()
        return files.sortedWith(
            compareBy<File> { file ->
                when (file.name) {
                    "00-domrei-glfw-bridge.jar" -> 0
                    "lwjgl.jar" -> 1
                    "lwjgl-3.3.3-merged-modules.jar" -> 2
                    else -> 3
                }
            }.thenBy { it.name }
        )
    }

    /**
     * Downloads and extracts the renderer's native `.so` library (`libmobileglues.so` or `libng_gl4es.so`)
     * for the target ABI from ZalithLauncher2's AAR artifacts, and prepares its runtime environment.
     */
    suspend fun prepareRenderer(
        type: RendererType = getSelectedRenderer(),
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        mobileGluesConfig: MobileGluesConfig = MobileGluesConfig(),
        kryptonConfig: KryptonWrapperConfig = KryptonWrapperConfig(),
        forceReinstall: Boolean = false,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<PreparedRenderer> = withContext(Dispatchers.IO) {
        runCatching {
            val extAbiDir = getRendererAbiDir(externalRenderersDir, type, abi)
            val execAbiDir = getRendererAbiDir(internalExecRenderersDir, type, abi)
            extAbiDir.mkdirs()
            execAbiDir.mkdirs()

            val extLibFile = File(extAbiDir, type.nativeLibFileName)
            val execLibFile = File(execAbiDir, type.nativeLibFileName)

            if (forceReinstall || !extLibFile.isFile || extLibFile.length() == 0L) {
                extLibFile.setWritable(true, true)
                val cacheDir = File(externalRenderersDir, ".cache")
                cacheDir.mkdirs()
                val aarFile = File(cacheDir, type.aarFileName)

                onProgress("Downloading ${type.displayName} renderer...", 0.1f)
                downloadFile(buildRendererAarUrl(type), aarFile) { fraction ->
                    onProgress(
                        "Downloading ${type.displayName} (${(fraction * 100).toInt()}%)",
                        0.1f + fraction * 0.7f
                    )
                }

                onProgress("Extracting ${type.nativeLibFileName} (${abi.androidAbi})...", 0.85f)
                extractSoFromAar(
                    aarFile = aarFile,
                    entryPath = "jni/${abi.androidAbi}/${type.nativeLibFileName}",
                    targetFile = extLibFile
                )
                aarFile.delete()
            }

            if (execLibFile.absolutePath != extLibFile.absolutePath &&
                (forceReinstall || !execLibFile.isFile || execLibFile.length() != extLibFile.length())
            ) {
                execLibFile.setWritable(true, true)
                extLibFile.copyTo(execLibFile, overwrite = true)
            }
            extLibFile.setReadable(true, false)
            extLibFile.setExecutable(true, false)
            extLibFile.setWritable(false, false)
            execLibFile.setReadable(true, false)
            execLibFile.setExecutable(true, false)
            execLibFile.setWritable(false, false)

            val activeLibFile = if (execLibFile.isFile) execLibFile else extLibFile
            val activeLibDir = activeLibFile.parentFile ?: execAbiDir

            // Ensure Android LWJGL 3.3.3 & OpenAL native .so libraries are extracted into activeLibDir
            runCatching {
                ensureAndroidLwjglAndOpenAlNatives(
                    abi = abi,
                    targetDir = activeLibDir,
                    forceReinstall = forceReinstall,
                    onProgress = onProgress
                )
            }

            val configFile = when (type) {
                RendererType.MOBILE_GLUES -> {
                    val mgDir = File(externalRenderersDir, "mobileglues")
                    mgDir.mkdirs()
                    val jsonStr = mobileGluesConfig.toJsonString()
                    File(mgDir, "mg_config.json").writeText(jsonStr)
                    File(mgDir, "config.json").apply {
                        writeText(jsonStr)
                    }
                }
                RendererType.KRYPTON_WRAPPER -> null
            }

            val envVars = buildRendererEnvironment(
                type = type,
                nativeLibFile = activeLibFile,
                configFile = configFile,
                mobileGluesConfig = mobileGluesConfig,
                kryptonConfig = kryptonConfig
            )

            val jvmProps = buildRendererJvmProperties(
                type = type,
                nativeLibFile = activeLibFile
            )

            onProgress("${type.displayName} ready", 1f)

            PreparedRenderer(
                type = type,
                nativeLibFile = activeLibFile,
                nativeLibDir = activeLibDir,
                configFile = configFile,
                environmentVars = envVars,
                jvmSystemProperties = jvmProps
            )
        }
    }

    /**
     * Builds the environment variables required by MobileGlues or Krypton Wrapper (NG-GL4ES).
     */
    fun buildRendererEnvironment(
        type: RendererType,
        nativeLibFile: File,
        configFile: File? = null,
        mobileGluesConfig: MobileGluesConfig = MobileGluesConfig(),
        kryptonConfig: KryptonWrapperConfig = KryptonWrapperConfig()
    ): Map<String, String> {
        val env = linkedMapOf<String, String>()
        when (type) {
            RendererType.MOBILE_GLUES -> {
                val mgConfigDir = configFile?.parentFile ?: File(externalRenderersDir, "mobileglues").apply { mkdirs() }
                env["POJAV_RENDERER"] = RendererType.MOBILE_GLUES.id
                env["DOMREI_RENDERER"] = RendererType.MOBILE_GLUES.id
                env["LIBGL_ES"] = "3"
                env["LIBGL_EGL"] = type.eglLibFileName
                env["MG_DIR_PATH"] = mgConfigDir.absolutePath
                env["MG_PLUGIN_PATH"] = nativeLibFile.absolutePath
                env["MG_MAX_GLSL_CACHE_SIZE"] = mobileGluesConfig.maxGlslCacheSize.toString()
                env["MG_ENABLE_ANGLE"] = mobileGluesConfig.enableANGLE.toString()
                env["MG_ENABLE_NO_ERROR"] = mobileGluesConfig.enableNoError.toString()
                env["MG_MULTIDRAW_MODE"] = mobileGluesConfig.multidrawMode.toString()
            }
            RendererType.KRYPTON_WRAPPER -> {
                env["POJAV_RENDERER"] = "opengles3"
                env["DOMREI_RENDERER"] = RendererType.KRYPTON_WRAPPER.id
                env["LIBGL_GL"] = kryptonConfig.glVersion.toString()
                env["LIBGL_ES"] = kryptonConfig.glesVersion.toString()
                env["LIBGL_EGL"] = type.eglLibFileName
                env["LIBGL_USE_MC_COLOR"] = if (kryptonConfig.useMcColor) "1" else "0"
                env["LIBGL_NORMALIZE"] = if (kryptonConfig.normalizeNormals) "1" else "0"
                env["LIBGL_NOERROR"] = if (kryptonConfig.noError) "1" else "0"
                env["LIBGL_MIPMAP"] = kryptonConfig.mipmapMode.toString()
                env["LIBGL_VBO"] = if (kryptonConfig.vboEnable) "1" else "0"
                env["KRYPTON_LIB_PATH"] = nativeLibFile.absolutePath
            }
        }
        return env
    }

    /**
     * Builds the LWJGL/OpenGL JVM system properties (`-Dkey=value`) for the selected renderer.
     */
    fun buildRendererJvmProperties(
        type: RendererType,
        nativeLibFile: File
    ): Map<String, String> {
        val libDir = nativeLibFile.parentFile
        val props = linkedMapOf(
            "org.lwjgl.opengl.libname" to nativeLibFile.name,
            "org.lwjgl.egl.libname" to type.eglLibFileName,
            "domrei.renderer.id" to type.id,
            "domrei.renderer.lib" to nativeLibFile.absolutePath
        )
        if (libDir != null) {
            val freetypeSo = File(libDir, "libfreetype.so")
            if (freetypeSo.isFile) {
                props["org.lwjgl.freetype.libname"] = freetypeSo.absolutePath
            }
            val openalSo = File(libDir, "libopenal.so")
            if (openalSo.isFile) {
                props["org.lwjgl.openal.libname"] = openalSo.absolutePath
            }
        }
        return props
    }

    private fun ensureAndroidLwjglAndOpenAlNatives(
        abi: RuntimeAbi,
        targetDir: File,
        forceReinstall: Boolean,
        onProgress: (String, Float) -> Unit
    ) {
        val sharedNativesDir = File(internalExecRenderersDir, "natives/${abi.androidAbi}").apply { mkdirs() }
        val cacheDir = File(externalRenderersDir, ".cache").apply { mkdirs() }

        val lwjglMarker = File(sharedNativesDir, "liblwjgl.so")
        if (forceReinstall || !lwjglMarker.isFile || lwjglMarker.length() == 0L) {
            val lwjglAar = File(cacheDir, LWJGL_NATIVES_AAR)
            onProgress("Downloading Android LWJGL 3.3.3 natives...", 0.88f)
            downloadFile("$DOMREI_LIBS_BASE_URL/$LWJGL_NATIVES_AAR", lwjglAar) { fraction ->
                onProgress("Downloading LWJGL natives (${(fraction * 100).toInt()}%)", 0.88f + fraction * 0.06f)
            }
            val prefix = "assets/app_runtime/lwjgl/3.3.3/natives/${abi.androidAbi}/"
            ZipFile(lwjglAar).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.startsWith(prefix) && entry.name.endsWith(".so")) {
                        val fileName = entry.name.removePrefix(prefix)
                        val outFile = File(sharedNativesDir, fileName)
                        outFile.setWritable(true, true)
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output, bufferSize = 32 * 1024)
                            }
                        }
                        outFile.setReadable(true, false)
                        outFile.setExecutable(true, false)
                        outFile.setWritable(false, false)
                    }
                }
            }
            lwjglAar.delete()
        }

        val openAlMarker = File(sharedNativesDir, "libopenal.so")
        if (forceReinstall || !openAlMarker.isFile || openAlMarker.length() == 0L) {
            val openAlAar = File(cacheDir, OPENAL_SOFT_AAR)
            onProgress("Downloading OpenAL Soft audio engine...", 0.95f)
            downloadFile("$DOMREI_LIBS_BASE_URL/$OPENAL_SOFT_AAR", openAlAar) { fraction ->
                onProgress("Downloading OpenAL Soft (${(fraction * 100).toInt()}%)", 0.95f + fraction * 0.04f)
            }
            openAlMarker.setWritable(true, true)
            extractSoFromAar(
                aarFile = openAlAar,
                entryPath = "jni/${abi.androidAbi}/libopenal.so",
                targetFile = openAlMarker
            )
            openAlMarker.setWritable(false, false)
            openAlAar.delete()
        }

        val jnaMarker = File(sharedNativesDir, "libjnidispatch.so")
        if (forceReinstall || !jnaMarker.isFile || jnaMarker.length() == 0L) {
            onProgress("Downloading Android JNA native library...", 0.97f)
            jnaMarker.setWritable(true, true)
            downloadFile(
                url = "$DOMREI_ASSETS_BASE_URL/runtimes/jna/5.17.0/natives/${abi.androidAbi}/libjnidispatch.so",
                targetFile = jnaMarker
            ) { _ -> }
            jnaMarker.setReadable(true, false)
            jnaMarker.setExecutable(true, false)
            jnaMarker.setWritable(false, false)
        }

        // Ensure Android LWJGL 3.3.3 jars & Domrei GLFW bridge jar are extracted
        val lwjglJarsDir = File(internalExecRenderersDir, "lwjgl/3.3.3/jars").apply { mkdirs() }
        val bridgeJarFile = File(lwjglJarsDir, "00-domrei-glfw-bridge.jar")
        runCatching {
            appContext?.assets?.open("domrei-glfw-bridge.jar")?.use { input ->
                bridgeJarFile.setWritable(true, true)
                FileOutputStream(bridgeJarFile).use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            }
        }

        for ((index, jarName) in ANDROID_LWJGL_333_JARS.withIndex()) {
            val jarFile = File(lwjglJarsDir, jarName)
            if (forceReinstall || !jarFile.isFile || jarFile.length() == 0L) {
                onProgress("Downloading Android LWJGL ($jarName)...", 0.97f + (index.toFloat() / ANDROID_LWJGL_333_JARS.size) * 0.02f)
                downloadFile(
                    url = "$DOMREI_ASSETS_BASE_URL/app_runtime/lwjgl/3.3.3/$jarName",
                    targetFile = jarFile
                ) { _ -> }
            }
        }

        // Sync shared natives into the active renderer ABI directory
        sharedNativesDir.listFiles()?.forEach { soFile ->
            if (soFile.isFile && soFile.name.endsWith(".so")) {
                val dest = File(targetDir, soFile.name)
                if (!dest.isFile || dest.length() != soFile.length()) {
                    dest.setWritable(true, true)
                    soFile.copyTo(dest, overwrite = true)
                    dest.setReadable(true, false)
                    dest.setExecutable(true, false)
                    dest.setWritable(false, false)
                }
            }
        }
    }

    internal fun extractSoFromAar(aarFile: File, entryPath: String, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        ZipFile(aarFile).use { zip ->
            val entry = zip.getEntry(entryPath)
                ?: throw IOException("Native library entry '$entryPath' not found inside ${aarFile.name}")
            zip.getInputStream(entry).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                }
            }
        }
        targetFile.setReadable(true, false)
        targetFile.setExecutable(true, false)
    }

    private fun getRendererAbiDir(baseDir: File, type: RendererType, abi: RuntimeAbi): File {
        return File(baseDir, "${type.id}/${abi.androidAbi}")
    }

    private fun downloadFile(
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
            val total = connection.contentLengthLong
            var readTotal = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        if (total > 0L) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
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
        const val DOMREI_LIBS_BASE_URL =
            "https://raw.githubusercontent.com/STMCGaming/Domrei-Runtimes/main/libs"
        const val DOMREI_ASSETS_BASE_URL =
            "https://raw.githubusercontent.com/STMCGaming/Domrei-Runtimes/main"
        private const val LWJGL_NATIVES_AAR = "lwjgl-3.3.3-natives-release.aar"
        private const val OPENAL_SOFT_AAR = "openal-soft-release.aar"
        private val ANDROID_LWJGL_333_JARS = listOf(
            "lwjgl.jar",
            "lwjgl-3.3.3-merged-modules.jar",
            "lwjgl-freetype.jar",
            "lwjgl-nanovg.jar",
            "lwjgl-openal.jar",
            "lwjgl-shaderc.jar",
            "lwjgl-spvc.jar",
            "lwjgl-stb.jar",
            "lwjgl-tinyfd.jar",
            "lwjgl-vma.jar"
        )
        private const val USER_AGENT = "DomreiLauncher-GameEngine/1.0"
    }
}
