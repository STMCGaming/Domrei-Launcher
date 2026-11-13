package com.domrei.gameengine.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.domrei.gameengine.MinecraftLaunchSpec
import com.domrei.gameengine.renderer.PreparedRenderer
import com.domrei.gameengine.renderer.RendererType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Dedicated `:jvm` process Service that hosts the OpenJDK VM via [DomreiJliRunner]
 * (`dlopen` + `JLI_Launch`) so that native environment changes (`setenv`, `chdir`, `dup2`)
 * and `System.exit()` are completely isolated from the main launcher UI process.
 */
class DomreiJvmService : Service() {

    @Volatile
    private var activeExitFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val specPath = intent?.getStringExtra(EXTRA_SPEC_FILE_PATH)
        if (specPath.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val specFile = File(specPath)
        if (!specFile.isFile) {
            stopSelf()
            return START_NOT_STICKY
        }

        val spec = runCatching { deserializeLaunchSpec(JSONObject(specFile.readText())) }
            .getOrElse { err ->
                val fallbackDir = specFile.parentFile ?: filesDir
                appendLine(File(fallbackDir, LOG_FILE_NAME), "[DomreiJvmService] Failed to parse launch spec: ${err.message}")
                File(fallbackDir, EXIT_FILE_NAME).writeText("-1")
                stopSelf()
                return START_NOT_STICKY
            }

        val logFilePath = intent.getStringExtra(EXTRA_LOG_FILE_PATH)
        val logFile = if (!logFilePath.isNullOrBlank()) {
            File(logFilePath)
        } else {
            File(spec.gameDir, LOG_FILE_NAME)
        }
        val exitFile = File(spec.gameDir, EXIT_FILE_NAME)
        activeExitFile = exitFile

        val jvmThread = Thread(
            null,
            {
                var exitCode = -1
                try {
                    appendLine(
                        logFile,
                        "[DomreiJvmService] Starting ${spec.runtime.version.displayName} via JLI_Launch in :jvm process (PID ${Process.myPid()})"
                    )
                    exitCode = DomreiJliRunner.launchInCurrentProcess(
                        spec = spec,
                        logFile = logFile
                    ) { msg ->
                        appendLine(logFile, msg)
                    }
                    appendLine(logFile, "[DomreiJvmService] JLI_Launch returned exit code $exitCode")
                } catch (t: Throwable) {
                    appendLine(
                        logFile,
                        "[DomreiJvmService] JVM launch error: ${t.javaClass.simpleName}: ${t.message}"
                    )
                } finally {
                    runCatching { exitFile.writeText(exitCode.toString()) }
                    stopSelf()
                }
            },
            "Domrei-JVM-Main",
            8L * 1024L * 1024L
        )
        jvmThread.isDaemon = false
        jvmThread.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        activeExitFile?.let { file ->
            if (!file.exists()) {
                runCatching { file.writeText("0") }
            }
        }
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun appendLine(file: File, line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).bufferedWriter().use { writer ->
                writer.write(line)
                writer.newLine()
            }
        }
    }

    companion object {
        const val EXTRA_SPEC_FILE_PATH = "com.domrei.gameengine.EXTRA_SPEC_FILE_PATH"
        const val EXTRA_LOG_FILE_PATH = "com.domrei.gameengine.EXTRA_LOG_FILE_PATH"
        const val SPEC_FILE_NAME = ".domrei_launch_spec.json"
        const val LOG_FILE_NAME = "latest.log"
        const val EXIT_FILE_NAME = ".domrei_jvm_exit.txt"

        fun serializeLaunchSpec(spec: MinecraftLaunchSpec): JSONObject {
            return JSONObject().apply {
                put("instanceName", spec.instanceName)
                put("versionId", spec.versionId)
                put("minecraftVersion", spec.minecraftVersion)
                put("mainClass", spec.mainClass)
                put("assetIndexId", spec.assetIndexId)
                put("runtimeVersion", spec.runtime.version.name)
                put("runtimeAbi", spec.runtime.abi.name)
                put("runtimeHomeDir", spec.runtime.homeDir.absolutePath)
                put("runtimeExecHomeDir", spec.runtime.execHomeDir.absolutePath)
                put("runtimeJavaExecutable", spec.runtime.javaExecutable.absolutePath)
                put("runtimeLibJliSo", spec.runtime.libJliSo?.absolutePath ?: "")
                put("runtimeLibJvmSo", spec.runtime.libJvmSo?.absolutePath ?: "")
                put("runtimeLibraryDirs", JSONArray(spec.runtime.libraryDirs.map { it.absolutePath }))
                put("runtimeReleaseTag", spec.runtime.releaseVersionTag)
                put("rendererType", spec.renderer.type.name)
                put("rendererNativeLibFile", spec.renderer.nativeLibFile.absolutePath)
                put("rendererNativeLibDir", spec.renderer.nativeLibDir.absolutePath)
                put("rendererConfigFile", spec.renderer.configFile?.absolutePath ?: "")
                put("rendererEnv", JSONObject(spec.renderer.environmentVars))
                put("rendererJvmProps", JSONObject(spec.renderer.jvmSystemProperties))
                put("gameDir", spec.gameDir.absolutePath)
                put("assetsDir", spec.assetsDir.absolutePath)
                put("nativesDir", spec.nativesDir.absolutePath)
                put("classpathEntries", JSONArray(spec.classpathEntries.map { it.absolutePath }))
                put("jvmArguments", JSONArray(spec.jvmArguments))
                put("gameArguments", JSONArray(spec.gameArguments))
                put("environment", JSONObject(spec.environment))
            }
        }

        fun deserializeLaunchSpec(json: JSONObject): MinecraftLaunchSpec {
            val libDirsArr = json.optJSONArray("runtimeLibraryDirs") ?: JSONArray()
            val libDirs = (0 until libDirsArr.length()).map { File(libDirsArr.getString(it)) }

            val runtime = InstalledJavaRuntime(
                version = JavaRuntimeVersion.valueOf(json.getString("runtimeVersion")),
                abi = RuntimeAbi.valueOf(json.getString("runtimeAbi")),
                homeDir = File(json.getString("runtimeHomeDir")),
                execHomeDir = File(json.getString("runtimeExecHomeDir")),
                javaExecutable = File(json.getString("runtimeJavaExecutable")),
                libJliSo = json.optString("runtimeLibJliSo").takeIf { it.isNotBlank() }?.let(::File),
                libJvmSo = json.optString("runtimeLibJvmSo").takeIf { it.isNotBlank() }?.let(::File),
                libraryDirs = libDirs,
                releaseVersionTag = json.optString("runtimeReleaseTag", "2")
            )

            val rendererEnvObj = json.optJSONObject("rendererEnv") ?: JSONObject()
            val rendererEnv = rendererEnvObj.keys().asSequence().associateWith { rendererEnvObj.getString(it) }

            val rendererPropsObj = json.optJSONObject("rendererJvmProps") ?: JSONObject()
            val rendererProps = rendererPropsObj.keys().asSequence().associateWith { rendererPropsObj.getString(it) }

            val renderer = PreparedRenderer(
                type = RendererType.valueOf(json.getString("rendererType")),
                nativeLibFile = File(json.getString("rendererNativeLibFile")),
                nativeLibDir = File(json.getString("rendererNativeLibDir")),
                configFile = json.optString("rendererConfigFile").takeIf { it.isNotBlank() }?.let(::File),
                environmentVars = rendererEnv,
                jvmSystemProperties = rendererProps
            )

            val cpArr = json.optJSONArray("classpathEntries") ?: JSONArray()
            val cpEntries = (0 until cpArr.length()).map { File(cpArr.getString(it)) }

            val jvmArr = json.optJSONArray("jvmArguments") ?: JSONArray()
            val jvmArgs = (0 until jvmArr.length()).map { jvmArr.getString(it) }

            val gameArr = json.optJSONArray("gameArguments") ?: JSONArray()
            val gameArgs = (0 until gameArr.length()).map { gameArr.getString(it) }

            val envObj = json.optJSONObject("environment") ?: JSONObject()
            val env = envObj.keys().asSequence().associateWith { envObj.getString(it) }

            return MinecraftLaunchSpec(
                instanceName = json.getString("instanceName"),
                versionId = json.getString("versionId"),
                minecraftVersion = json.getString("minecraftVersion"),
                mainClass = json.getString("mainClass"),
                assetIndexId = json.getString("assetIndexId"),
                runtime = runtime,
                renderer = renderer,
                gameDir = File(json.getString("gameDir")),
                assetsDir = File(json.getString("assetsDir")),
                nativesDir = File(json.getString("nativesDir")),
                classpathEntries = cpEntries,
                jvmArguments = jvmArgs,
                gameArguments = gameArgs,
                environment = env
            )
        }
    }
}
