package com.domrei.gameengine

import android.content.Context
import android.content.Intent
import android.os.Build
import com.domrei.gameengine.log.EngineLogManager
import com.domrei.gameengine.renderer.KryptonWrapperConfig
import com.domrei.gameengine.renderer.MobileGluesConfig
import com.domrei.gameengine.renderer.PreparedRenderer
import com.domrei.gameengine.renderer.RendererManager
import com.domrei.gameengine.renderer.RendererType
import com.domrei.gameengine.runtime.DomreiJliRunner
import com.domrei.gameengine.runtime.DomreiJvmService
import com.domrei.gameengine.runtime.InstalledJavaRuntime
import com.domrei.gameengine.runtime.JavaRuntimeVersion
import com.domrei.gameengine.runtime.JdkManager
import com.domrei.gameengine.runtime.RuntimeAbi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

enum class GameEngineState {
    IDLE,
    PREPARING_RUNTIME,
    PREPARING_RENDERER,
    BUILDING_SPEC,
    RUNNING,
    EXITED,
    FAILED
}

data class GameEngineStatus(
    val state: GameEngineState = GameEngineState.IDLE,
    val stageMessage: String = "Idle",
    val progress: Float = 0f,
    val activeInstanceName: String? = null,
    val activeRuntime: JavaRuntimeVersion? = null,
    val activeRenderer: RendererType? = null,
    val exitCode: Int? = null,
    val errorMessage: String? = null
)

/**
 * Domrei-Launcher's clean-room Minecraft: Java Edition Game Engine for Android.
 *
 * Responsibilities:
 * 1. Auto-selects and downloads OpenJDK (JRE 8 / 17 / 21 / 25) from ZalithLauncher2.
 * 2. Prepares and configures the selected renderer (MobileGlues or Krypton Wrapper / NG-GL4ES).
 * 3. Resolves Mojang + Fabric version JSONs, deduplicating Maven libraries and constructing
 *    the complete JVM & game command line.
 * 4. Launches and monitors the game process with real-time log streaming.
 */
class DomreiGameEngine(
    val jdkManager: JdkManager,
    val rendererManager: RendererManager,
    val logManager: EngineLogManager = EngineLogManager(File(jdkManager.runtimesDir.parentFile ?: jdkManager.runtimesDir, "logs")),
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        jdkManager = JdkManager(context),
        rendererManager = RendererManager(context),
        logManager = EngineLogManager(context),
        appContext = context.applicationContext
    )

    private val _status = MutableStateFlow(GameEngineStatus())
    val status: StateFlow<GameEngineStatus> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    @Volatile
    private var activeProcess: Process? = null
    private var monitorJob: Job? = null

    /**
     * Ensures the required OpenJDK runtime and selected renderer (MobileGlues or Krypton Wrapper)
     * are installed, then builds a validated [MinecraftLaunchSpec].
     */
    suspend fun prepareAndBuildLaunchSpec(
        request: GameLaunchRequest,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        mobileGluesConfig: MobileGluesConfig = MobileGluesConfig(),
        kryptonConfig: KryptonWrapperConfig = KryptonWrapperConfig(),
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<MinecraftLaunchSpec> = withContext(Dispatchers.IO) {
        runCatching {
            appendLog("[DomreiEngine] Preparing launch for '${request.instanceName}' (MC ${request.minecraftVersion}, ${request.loaderType})")

            val baseVersionJsonFile = File(
                request.minecraftRootDir,
                "versions/${request.minecraftVersion}/${request.minecraftVersion}.json"
            )
            if (!baseVersionJsonFile.isFile) {
                throw IOException("Minecraft version JSON not found: ${baseVersionJsonFile.absolutePath}")
            }
            val baseJson = JSONObject(baseVersionJsonFile.readText())

            val fabricJson: JSONObject? = if (
                request.loaderType.equals("Fabric", ignoreCase = true) &&
                !request.loaderVersion.isNullOrBlank()
            ) {
                val fabricId = "fabric-loader-${request.loaderVersion}-${request.minecraftVersion}"
                val fabricFile = File(request.minecraftRootDir, "versions/$fabricId/$fabricId.json")
                if (fabricFile.isFile) JSONObject(fabricFile.readText()) else null
            } else {
                null
            }

            val jsonJavaMajor = baseJson.optJSONObject("javaVersion")?.optInt("majorVersion", 0)
                ?.takeIf { it > 0 }
            val targetRuntimeVersion = request.preferredRuntime
                ?: JdkManager.selectRuntimeForMinecraft(request.minecraftVersion, jsonJavaMajor)
            val targetRendererType = request.preferredRenderer ?: rendererManager.getSelectedRenderer()

            _status.value = GameEngineStatus(
                state = GameEngineState.PREPARING_RUNTIME,
                stageMessage = "Preparing ${targetRuntimeVersion.displayName}...",
                progress = 0.05f,
                activeInstanceName = request.instanceName,
                activeRuntime = targetRuntimeVersion,
                activeRenderer = targetRendererType
            )
            onProgress("Preparing ${targetRuntimeVersion.displayName}...", 0.05f)

            val installedRuntime = jdkManager.ensureRuntimeInstalled(
                version = targetRuntimeVersion,
                abi = abi
            ) { rtProgress ->
                val overall = 0.05f + rtProgress.progressFraction * 0.55f
                _status.value = _status.value.copy(
                    stageMessage = rtProgress.stage,
                    progress = overall
                )
                onProgress(rtProgress.stage, overall)
            }.getOrThrow()

            appendLog("[DomreiEngine] Runtime ready: ${installedRuntime.version.displayName} at ${installedRuntime.execHomeDir.absolutePath}")

            _status.value = _status.value.copy(
                state = GameEngineState.PREPARING_RENDERER,
                stageMessage = "Preparing ${targetRendererType.displayName} renderer...",
                progress = 0.62f
            )

            val preparedRenderer = rendererManager.prepareRenderer(
                type = targetRendererType,
                abi = abi,
                mobileGluesConfig = mobileGluesConfig,
                kryptonConfig = kryptonConfig
            ) { stage, fraction ->
                val overall = 0.62f + fraction * 0.25f
                _status.value = _status.value.copy(
                    stageMessage = stage,
                    progress = overall
                )
                onProgress(stage, overall)
            }.getOrThrow()

            appendLog("[DomreiEngine] Renderer ready: ${preparedRenderer.type.displayName} (${preparedRenderer.nativeLibFile.name})")

            _status.value = _status.value.copy(
                state = GameEngineState.BUILDING_SPEC,
                stageMessage = "Building launch command & classpath...",
                progress = 0.90f
            )
            onProgress("Building launch command & classpath...", 0.90f)

            val spec = buildLaunchSpecFromJson(
                request = request,
                baseJson = baseJson,
                fabricJson = fabricJson,
                runtime = installedRuntime,
                renderer = preparedRenderer
            )

            appendLog("[DomreiEngine] Resolved ${spec.classpathEntries.size} classpath jars, mainClass=${spec.mainClass}")
            onProgress("Ready to launch ${request.instanceName}", 1.0f)
            spec
        }.onFailure { error ->
            appendLog("[DomreiEngine] Preparation failed: ${error.message}")
            _status.value = _status.value.copy(
                state = GameEngineState.FAILED,
                stageMessage = error.message ?: "Failed to prepare game engine",
                errorMessage = error.message
            )
        }
    }

    /**
     * Constructs the [MinecraftLaunchSpec] directly from parsed Mojang & Fabric JSON objects,
     * an [InstalledJavaRuntime], and a [PreparedRenderer].
     */
    fun buildLaunchSpecFromJson(
        request: GameLaunchRequest,
        baseJson: JSONObject,
        fabricJson: JSONObject?,
        runtime: InstalledJavaRuntime,
        renderer: PreparedRenderer
    ): MinecraftLaunchSpec {
        val librariesDir = File(request.minecraftRootDir, "libraries")
        val assetsDir = File(request.minecraftRootDir, "assets")
        val internalBaseDir = jdkManager.runtimesDir.parentFile ?: jdkManager.runtimesDir
        val nativesDir = File(internalBaseDir, "natives/${runtime.abi.androidAbi}").apply { mkdirs() }
        val tmpDir = File(internalBaseDir, "tmp").apply { mkdirs() }
        request.instanceGameDir.mkdirs()

        val launchedVersionId = fabricJson?.optString("id")?.takeIf { it.isNotBlank() }
            ?: baseJson.optString("id").ifBlank { request.minecraftVersion }

        val mainClass = fabricJson?.optString("mainClass")?.takeIf { it.isNotBlank() }
            ?: baseJson.optString("mainClass").ifBlank { "net.minecraft.client.main.Main" }

        val assetIndexId = baseJson.optJSONObject("assetIndex")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: baseJson.optString("assets").ifBlank { request.minecraftVersion }

        val androidLwjglJars = rendererManager.getAndroidLwjglJars()
        val skipDesktopLwjgl = androidLwjglJars.isNotEmpty()

        // Resolve and deduplicate Maven libraries (Fabric libraries take precedence over Vanilla)
        val resolvedLibraryMap = linkedMapOf<String, File>()

        if (fabricJson != null) {
            collectLibrariesFromJson(fabricJson.optJSONArray("libraries"), librariesDir, resolvedLibraryMap, skipDesktopLwjgl)
        }
        collectLibrariesFromJson(baseJson.optJSONArray("libraries"), librariesDir, resolvedLibraryMap, skipDesktopLwjgl)

        val clientJar = File(
            request.minecraftRootDir,
            "versions/${request.minecraftVersion}/${request.minecraftVersion}.jar"
        )
        val classpathEntries = buildList {
            addAll(androidLwjglJars)
            addAll(resolvedLibraryMap.values)
            add(clientJar)
        }

        applyAndroidCompatibilityPatches(classpathEntries)

        val combinedLibPath = buildList {
            runtime.libJvmSo?.parentFile?.let { add(it.absolutePath) }
            runtime.libraryDirs.forEach { add(it.absolutePath) }
            add(renderer.nativeLibDir.absolutePath)
            add(nativesDir.absolutePath)
            appContext?.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { add(it) }
            val sysLibDir = if (runtime.abi == RuntimeAbi.ARM64 || runtime.abi == RuntimeAbi.X86_64) "lib64" else "lib"
            add("/system/$sysLibDir")
            add("/vendor/$sysLibDir")
            add("/vendor/$sysLibDir/hw")
            add("/system_ext/$sysLibDir")
        }.distinct().joinToString(File.pathSeparator)

        val isOfflineToken = request.accessToken.isBlank() ||
            request.accessToken == "0" ||
            request.accessToken.equals("null", ignoreCase = true)

        val placeholders = mapOf(
            "auth_player_name" to request.username,
            "version_name" to launchedVersionId,
            "game_directory" to request.instanceGameDir.absolutePath,
            "assets_root" to assetsDir.absolutePath,
            "game_assets" to assetsDir.absolutePath,
            "assets_index_name" to assetIndexId,
            "auth_uuid" to request.uuid,
            "auth_access_token" to request.accessToken,
            "auth_session" to request.accessToken,
            "clientid" to "0",
            "auth_xuid" to "0",
            "user_type" to if (isOfflineToken) "legacy" else "msa",
            "user_properties" to "{}",
            "version_type" to "Domrei-Launcher",
            "natives_directory" to nativesDir.absolutePath,
            "launcher_name" to "Domrei-Launcher",
            "launcher_version" to "1.0",
            "library_directory" to librariesDir.absolutePath,
            "classpath_separator" to File.pathSeparator,
            "classpath" to classpathEntries.joinToString(File.pathSeparator) { it.absolutePath },
            "resolution_width" to request.windowWidth.toString(),
            "resolution_height" to request.windowHeight.toString()
        )

        val jvmArgs = mutableListOf<String>()
        jvmArgs += "-Xms${request.minMemoryMb}M"
        jvmArgs += "-Xmx${request.maxMemoryMb}M"
        jvmArgs += "-XX:+UseG1GC"
        jvmArgs += "-XX:+ParallelRefProcEnabled"
        jvmArgs += "-XX:MaxGCPauseMillis=200"
        jvmArgs += "-XX:+UnlockExperimentalVMOptions"
        jvmArgs += "-XX:+DisableExplicitGC"
        jvmArgs += "-Djava.library.path=$combinedLibPath"
        jvmArgs += "-Dorg.lwjgl.librarypath=${renderer.nativeLibDir.absolutePath}${File.pathSeparator}$combinedLibPath"
        jvmArgs += "-Djna.boot.library.path=${renderer.nativeLibDir.absolutePath}"
        jvmArgs += "-Djna.nosys=false"
        jvmArgs += "-Dorg.lwjgl.system.SharedLibraryExtractPath=${nativesDir.absolutePath}"
        jvmArgs += "-Dorg.lwjgl.system.allocator=system"
        jvmArgs += "-Dglfwstub.windowWidth=${request.windowWidth}"
        jvmArgs += "-Dglfwstub.windowHeight=${request.windowHeight}"
        jvmArgs += "-Dglfwstub.initEgl=false"
        jvmArgs += "-Djna.tmpdir=${tmpDir.absolutePath}"
        jvmArgs += "-Dio.netty.native.workdir=${tmpDir.absolutePath}"
        jvmArgs += "-Djava.io.tmpdir=${tmpDir.absolutePath}"
        jvmArgs += "-Duser.home=${request.instanceGameDir.absolutePath}"
        jvmArgs += "-Duser.dir=${request.instanceGameDir.absolutePath}"
        jvmArgs += "-Dos.name=Linux"
        jvmArgs += "-Dos.version=Android-${Build.VERSION.RELEASE ?: "14"}"
        jvmArgs += "-Djdk.lang.Process.launchMechanism=FORK"
        jvmArgs += "-Dminecraft.launcher.brand=Domrei-Launcher"
        jvmArgs += "-Dminecraft.launcher.version=1.0"
        jvmArgs += "-Dfml.earlyprogresswindow=false"

        renderer.jvmSystemProperties.forEach { (key, value) ->
            jvmArgs += "-D$key=$value"
        }

        val jsonJvmArgs = mutableListOf<String>()
        extractJsonArguments(baseJson.optJSONObject("arguments")?.optJSONArray("jvm"), placeholders, jsonJvmArgs)
        if (fabricJson != null) {
            extractJsonArguments(fabricJson.optJSONObject("arguments")?.optJSONArray("jvm"), placeholders, jsonJvmArgs)
        }

        // Filter out macOS/Windows-only flags and prevent Mojang's default -Djava.library.path from overwriting combinedLibPath
        val filteredJsonJvmArgs = jsonJvmArgs.filterNot { arg ->
            arg == "-XstartOnFirstThread" ||
                arg.startsWith("-Xdock:") ||
                arg.startsWith("-Djava.library.path=") ||
                arg.startsWith("-Djna.tmpdir=") ||
                arg.startsWith("-Dio.netty.native.workdir=") ||
                arg.startsWith("-Dorg.lwjgl.system.SharedLibraryExtractPath=") ||
                arg.contains("MojangTricksIntelDriversForPerformance")
        }
        jvmArgs += filteredJsonJvmArgs

        if (jvmArgs.none { it == "-cp" || it == "-classpath" }) {
            jvmArgs += "-cp"
            jvmArgs += placeholders.getValue("classpath")
        }

        jvmArgs += request.extraJvmArgs

        val gameArgs = mutableListOf<String>()
        val baseGameArray = baseJson.optJSONObject("arguments")?.optJSONArray("game")
        if (baseGameArray != null) {
            extractJsonArguments(baseGameArray, placeholders, gameArgs)
        } else {
            val legacyArgs = baseJson.optString("minecraftArguments", "")
            if (legacyArgs.isNotBlank()) {
                legacyArgs.split(" ")
                    .filter { it.isNotBlank() }
                    .forEach { token -> gameArgs += substitutePlaceholders(token, placeholders) }
            }
        }
        if (fabricJson != null) {
            extractJsonArguments(fabricJson.optJSONObject("arguments")?.optJSONArray("game"), placeholders, gameArgs)
        }

        if ("--width" !in gameArgs) {
            gameArgs += "--width"
            gameArgs += request.windowWidth.toString()
        }
        if ("--height" !in gameArgs) {
            gameArgs += "--height"
            gameArgs += request.windowHeight.toString()
        }
        gameArgs += request.extraGameArgs

        val environment = linkedMapOf<String, String>()
        environment["HOME"] = request.instanceGameDir.absolutePath
        environment["JAVA_HOME"] = runtime.execHomeDir.absolutePath
        environment["TMPDIR"] = tmpDir.absolutePath
        environment["LD_LIBRARY_PATH"] = combinedLibPath
        environment["PATH"] = "${File(runtime.execHomeDir, "bin").absolutePath}:${System.getenv("PATH") ?: "/system/bin"}"
        environment.putAll(renderer.environmentVars)

        return MinecraftLaunchSpec(
            instanceName = request.instanceName,
            versionId = launchedVersionId,
            minecraftVersion = request.minecraftVersion,
            mainClass = mainClass,
            assetIndexId = assetIndexId,
            runtime = runtime,
            renderer = renderer,
            gameDir = request.instanceGameDir,
            assetsDir = assetsDir,
            nativesDir = nativesDir,
            classpathEntries = classpathEntries,
            jvmArguments = jvmArgs,
            gameArguments = gameArgs,
            environment = environment
        )
    }

    /**
     * Prepares the runtime & renderer, builds the launch spec, and starts the Minecraft JVM
     * via [DomreiJvmService] (`dlopen` + `JLI_Launch` in `:jvm` process on Android).
     */
    suspend fun launchGame(
        request: GameLaunchRequest,
        scope: CoroutineScope,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<MinecraftLaunchSpec> {
        val latestLogFile = logManager.startNewSession(request.instanceName)
        appendLog("[DomreiEngine] Log file: ${latestLogFile.absolutePath}")

        val specResult = prepareAndBuildLaunchSpec(request = request, onProgress = onProgress)
        val spec = specResult.getOrElse { return Result.failure(it) }

        return withContext(Dispatchers.IO) {
            runCatching {
                stopActiveGame()

                DomreiJliRunner.enforceReadOnlyExecutablePermissions(spec.runtime.execHomeDir)
                DomreiJliRunner.enforceReadOnlyExecutablePermissions(spec.renderer.nativeLibDir)

                appendLog("[DomreiEngine] Launching OpenJDK via JLI_Launch: ${spec.runtime.libJliSo?.absolutePath ?: spec.runtime.javaExecutable.absolutePath}")
                appendLog("[DomreiEngine] Renderer: ${spec.renderer.type.displayName} (${spec.renderer.nativeLibFile.name})")
                appendLog("[DomreiEngine] Game Directory: ${spec.gameDir.absolutePath}")

                val context = appContext
                if (context != null) {
                    spec.gameDir.mkdirs()
                    val specFile = File(spec.gameDir, DomreiJvmService.SPEC_FILE_NAME)
                    val exitFile = File(spec.gameDir, DomreiJvmService.EXIT_FILE_NAME)

                    exitFile.delete()
                    specFile.writeText(DomreiJvmService.serializeLaunchSpec(spec).toString(2))

                    val initialLogPointer = if (latestLogFile.isFile) latestLogFile.length() else 0L

                    val gameActivityIntent = Intent(context, GameActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(DomreiJvmService.EXTRA_SPEC_FILE_PATH, specFile.absolutePath)
                        putExtra(DomreiJvmService.EXTRA_LOG_FILE_PATH, latestLogFile.absolutePath)
                    }
                    context.startActivity(gameActivityIntent)

                    _status.value = GameEngineStatus(
                        state = GameEngineState.RUNNING,
                        stageMessage = "Running ${spec.instanceName} (${spec.renderer.type.displayName})",
                        progress = 1.0f,
                        activeInstanceName = spec.instanceName,
                        activeRuntime = spec.runtime.version,
                        activeRenderer = spec.renderer.type
                    )

                    monitorJob = scope.launch(Dispatchers.IO) {
                        var filePointer = initialLogPointer
                        while (isActive) {
                            if (latestLogFile.isFile && latestLogFile.length() > filePointer) {
                                runCatching {
                                    RandomAccessFile(latestLogFile, "r").use { raf ->
                                        raf.seek(filePointer)
                                        var line = raf.readLine()
                                        while (line != null) {
                                            appendLog(line, writeToLatestFile = false)
                                            line = raf.readLine()
                                        }
                                        filePointer = raf.filePointer
                                    }
                                }
                            }
                            if (exitFile.isFile) {
                                val exitCode = runCatching { exitFile.readText().trim().toInt() }.getOrDefault(-1)
                                appendLog("[DomreiEngine] JVM exited with code $exitCode")
                                _status.value = _status.value.copy(
                                    state = if (exitCode == 0) GameEngineState.EXITED else GameEngineState.FAILED,
                                    stageMessage = if (exitCode == 0) {
                                        "${spec.instanceName} exited normally"
                                    } else {
                                        "${spec.instanceName} exited with code $exitCode"
                                    },
                                    exitCode = exitCode
                                )
                                break
                            }
                            delay(120L)
                        }
                    }
                } else {
                    val processBuilder = ProcessBuilder(spec.commandLine)
                        .directory(spec.gameDir)
                        .redirectErrorStream(true)

                    val procEnv = processBuilder.environment()
                    procEnv.putAll(spec.environment)

                    val process = processBuilder.start()
                    activeProcess = process

                    _status.value = GameEngineStatus(
                        state = GameEngineState.RUNNING,
                        stageMessage = "Running ${spec.instanceName} (${spec.renderer.type.displayName})",
                        progress = 1.0f,
                        activeInstanceName = spec.instanceName,
                        activeRuntime = spec.runtime.version,
                        activeRenderer = spec.renderer.type
                    )

                    monitorJob = scope.launch(Dispatchers.IO) {
                        runCatching {
                            process.inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    appendLog(line)
                                }
                            }
                        }
                        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
                        activeProcess = null
                        appendLog("[DomreiEngine] Process exited with code $exitCode")
                        _status.value = _status.value.copy(
                            state = if (exitCode == 0) GameEngineState.EXITED else GameEngineState.FAILED,
                            stageMessage = if (exitCode == 0) {
                                "${spec.instanceName} exited normally"
                            } else {
                                "${spec.instanceName} exited with code $exitCode"
                            },
                            exitCode = exitCode
                        )
                    }
                }

                spec
            }.onFailure { error ->
                appendLog("[DomreiEngine] Launch error: ${error.message}")
                _status.value = _status.value.copy(
                    state = GameEngineState.FAILED,
                    stageMessage = error.message ?: "Failed to start JVM process",
                    errorMessage = error.message
                )
            }
        }
    }

    fun stopActiveGame() {
        monitorJob?.cancel()
        monitorJob = null
        appContext?.let { ctx ->
            runCatching {
                ctx.stopService(Intent(ctx, DomreiJvmService::class.java))
            }
        }
        activeProcess?.let { proc ->
            runCatching {
                proc.destroy()
                if (proc.isAlive) {
                    proc.destroyForcibly()
                }
            }
        }
        activeProcess = null
        if (_status.value.state == GameEngineState.RUNNING) {
            _status.value = _status.value.copy(
                state = GameEngineState.EXITED,
                stageMessage = "Game stopped by user"
            )
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun appendLog(line: String, writeToLatestFile: Boolean = true) {
        logManager.appendLogLine(line, writeToLatest = writeToLatestFile)
        val current = _logs.value
        val updated = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + line
        } else {
            current + line
        }
        _logs.value = updated
    }

    private fun collectLibrariesFromJson(
        librariesArray: JSONArray?,
        librariesDir: File,
        outMap: MutableMap<String, File>,
        skipDesktopLwjgl: Boolean = false
    ) {
        if (librariesArray == null) return
        for (i in 0 until librariesArray.length()) {
            val libObj = librariesArray.optJSONObject(i) ?: continue
            if (!isLibraryAllowedOnAndroid(libObj.optJSONArray("rules"))) continue

            val name = libObj.optString("name", "")
            if (name.isBlank()) continue

            if (skipDesktopLwjgl && name.startsWith("org.lwjgl")) {
                continue
            }

            // Skip platform-specific desktop natives (windows, osx, linux-x86_64 GLFW, etc.)
            if (name.contains(":natives-windows") ||
                name.contains(":natives-macos") ||
                name.contains(":natives-osx") ||
                name.contains(":natives-linux")
            ) {
                continue
            }

            val key = mavenCoordinateKey(name)
            if (outMap.containsKey(key)) continue

            val artifactPath = libObj.optJSONObject("downloads")
                ?.optJSONObject("artifact")
                ?.optString("path")
                ?.takeIf { it.isNotBlank() }
                ?: mavenCoordsToRelativePath(name)

            if (artifactPath != null) {
                outMap[key] = File(librariesDir, artifactPath)
            }
        }
    }

    private fun extractJsonArguments(
        argsArray: JSONArray?,
        placeholders: Map<String, String>,
        outList: MutableList<String>
    ) {
        if (argsArray == null) return
        for (i in 0 until argsArray.length()) {
            when (val elem = argsArray.opt(i)) {
                is String -> {
                    val substituted = substitutePlaceholders(elem, placeholders)
                    if (!substituted.startsWith("\${")) {
                        outList += substituted
                    }
                }
                is JSONObject -> {
                    if (!isLibraryAllowedOnAndroid(elem.optJSONArray("rules"))) continue
                    val value = elem.opt("value")
                    when (value) {
                        is String -> {
                            val substituted = substitutePlaceholders(value, placeholders)
                            if (!substituted.startsWith("\${")) {
                                outList += substituted
                            }
                        }
                        is JSONArray -> {
                            for (j in 0 until value.length()) {
                                val str = value.optString(j, "")
                                if (str.isNotBlank()) {
                                    outList += substitutePlaceholders(str, placeholders)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun substitutePlaceholders(
        template: String,
        placeholders: Map<String, String>
    ): String {
        var result = template
        for ((key, value) in placeholders) {
            result = result.replace("\${$key}", value)
        }
        return result
    }

    private fun isLibraryAllowedOnAndroid(rulesArray: JSONArray?): Boolean {
        if (rulesArray == null || rulesArray.length() == 0) return true
        var allowed = false
        for (i in 0 until rulesArray.length()) {
            val rule = rulesArray.optJSONObject(i) ?: continue
            val action = rule.optString("action", "allow")
            val osObj = rule.optJSONObject("os")
            val featuresObj = rule.optJSONObject("features")
            if (featuresObj != null) {
                // Skip demo/custom quickPlay feature rules by default
                continue
            }
            val osMatches = if (osObj == null) {
                true
            } else {
                val osName = osObj.optString("name", "")
                osName.equals("linux", ignoreCase = true) || osName.equals("android", ignoreCase = true)
            }
            if (osMatches) {
                allowed = (action == "allow")
            }
        }
        return allowed
    }

    private fun mavenCoordinateKey(coords: String): String {
        val parts = coords.split(":")
        if (parts.size < 3) return coords
        val group = parts[0]
        val artifact = parts[1]
        val classifier = if (parts.size >= 4) ":${parts[3]}" else ""
        return "$group:$artifact$classifier"
    }

    private fun mavenCoordsToRelativePath(coords: String): String? {
        val parts = coords.split(":")
        if (parts.size < 3) return null
        val groupPath = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        val classifier = if (parts.size >= 4) "-${parts[3]}" else ""
        return "$groupPath/$artifact/$version/$artifact-$version$classifier.jar"
    }

    /**
     * Patches desktop-specific classes in LWJGL, OSHI, Text2Speech, and Authlib jars on the
     * resolved classpath using the Android-compatible override classes bundled in
     * `00-domrei-glfw-bridge.jar`. This ensures compatibility even when a modloader (e.g. Fabric Knot)
     * loads library classes from their original jar CodeSource.
     */
    internal fun applyAndroidCompatibilityPatches(classpathEntries: List<File>) {
        val bridgeJar = classpathEntries.firstOrNull {
            it.name.contains("domrei-glfw-bridge") && it.isFile
        } ?: return

        val overrideEntries = mutableMapOf<String, ByteArray>()
        runCatching {
            ZipFile(bridgeJar).use { zip ->
                val names = listOf(
                    "org/lwjgl/system/Library.class",
                    "oshi/hardware/platform/linux/LinuxCentralProcessor.class",
                    "com/mojang/text2speech/NarratorLinux.class",
                    "com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class",
                    "com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService$1.class"
                )
                for (name in names) {
                    val entry = zip.getEntry(name) ?: continue
                    zip.getInputStream(entry).use { input ->
                        overrideEntries[name] = input.readBytes()
                    }
                }
            }
        }
        if (overrideEntries.isEmpty()) return

        for (jarFile in classpathEntries) {
            if (!jarFile.isFile || jarFile == bridgeJar) continue
            val name = jarFile.name
            val patchesForJar: Map<String, ByteArray> = when {
                name == "lwjgl.jar" -> overrideEntries.filterKeys { it.startsWith("org/lwjgl/system/") }
                name.startsWith("oshi-core") -> overrideEntries.filterKeys { it.startsWith("oshi/") }
                name.startsWith("text2speech") -> overrideEntries.filterKeys { it.startsWith("com/mojang/text2speech/") }
                name.startsWith("authlib") -> overrideEntries.filterKeys { it.startsWith("com/mojang/authlib/") }
                else -> emptyMap()
            }
            if (patchesForJar.isEmpty()) continue

            val markerFile = File(jarFile.parentFile, "${jarFile.name}.$PATCH_MARKER_SUFFIX")
            if (markerFile.isFile) continue

            runCatching {
                val tempJar = File(jarFile.parentFile, "${jarFile.name}.patching")
                ZipFile(jarFile).use { srcZip ->
                    ZipOutputStream(FileOutputStream(tempJar)).use { zos ->
                        val writtenNames = HashSet<String>()
                        for ((entryName, bytes) in patchesForJar) {
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(bytes)
                            zos.closeEntry()
                            writtenNames.add(entryName)
                        }
                        val entries = srcZip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val entryName = entry.name
                            if (writtenNames.contains(entryName)) continue
                            if (entryName.startsWith("META-INF/") && (
                                    entryName.endsWith(".SF") ||
                                        entryName.endsWith(".RSA") ||
                                        entryName.endsWith(".DSA") ||
                                        entryName.endsWith(".EC")
                                )
                            ) {
                                continue
                            }
                            writtenNames.add(entryName)
                            val newEntry = ZipEntry(entryName)
                            zos.putNextEntry(newEntry)
                            if (!entry.isDirectory) {
                                srcZip.getInputStream(entry).use { input ->
                                    input.copyTo(zos, bufferSize = 16 * 1024)
                                }
                            }
                            zos.closeEntry()
                        }
                    }
                }
                jarFile.setWritable(true, true)
                if (jarFile.delete() && tempJar.renameTo(jarFile)) {
                    markerFile.writeText("1")
                } else {
                    tempJar.copyTo(jarFile, overwrite = true)
                    tempJar.delete()
                    markerFile.writeText("1")
                }
            }
        }
    }

    companion object {
        private const val MAX_LOG_LINES = 250
        private const val PATCH_MARKER_SUFFIX = "domrei_patch_v1"
    }
}
