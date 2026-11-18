package com.domrei.gameengine.renderer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.domrei.gameengine.runtime.RuntimeAbi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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

    val pluginsDir: File
        get() = File(externalRenderersDir, "plugins").apply { mkdirs() }

    fun getSelectedRenderer(): RendererType {
        return getSelectedRendererDescriptor().effectiveType
    }

    fun getSelectedRendererDisplayName(): String {
        return getSelectedRendererDescriptor().displayName
    }

    fun getSelectedRendererDescriptor(): RendererDescriptor {
        val savedId = runCatching {
            if (preferenceFile.isFile) preferenceFile.readText().trim() else null
        }.getOrNull()
        if (savedId.isNullOrBlank()) {
            return RendererDescriptor.fromBuiltIn(RendererType.KRYPTON_WRAPPER)
        }
        val all = getAvailableRenderers()
        return all.firstOrNull { it.id.equals(savedId, ignoreCase = true) }
            ?: all.firstOrNull { it.id.substringAfter("plugin:").equals(savedId.removePrefix("plugin:"), ignoreCase = true) }
            ?: RendererDescriptor.fromBuiltIn(RendererType.fromId(savedId))
    }

    fun setSelectedRenderer(type: RendererType) {
        externalRenderersDir.mkdirs()
        preferenceFile.writeText(type.id)
    }

    fun setSelectedRenderer(descriptor: RendererDescriptor) {
        externalRenderersDir.mkdirs()
        preferenceFile.writeText(descriptor.id)
    }

    fun setSelectedRenderer(id: String) {
        externalRenderersDir.mkdirs()
        preferenceFile.writeText(id)
    }

    fun getRendererConfigSpec(descriptor: RendererDescriptor): RendererConfigSpec {
        if (descriptor.isPlugin && descriptor.pluginInfo != null) {
            val plugin = descriptor.pluginInfo
            if (plugin.configSpec.isNotEmpty) {
                return plugin.configSpec
            }
            val isMobileGl = plugin.id.contains("mobilegl", ignoreCase = true) ||
                plugin.displayName.contains("mobilegl", ignoreCase = true) ||
                plugin.displayName.contains("mobile gl", ignoreCase = true) ||
                plugin.displayName.contains("mobileglues", ignoreCase = true) ||
                plugin.id.contains("mobileglues", ignoreCase = true)
            if (isMobileGl) {
                return RendererConfigSpec.createMobileGlSpec()
            }
            return RendererConfigSpec()
        }

        val isMobileGl = descriptor.id.contains("mobilegl", ignoreCase = true) ||
            descriptor.displayName.contains("mobilegl", ignoreCase = true) ||
            descriptor.displayName.contains("mobile gl", ignoreCase = true) ||
            descriptor.builtInType == RendererType.MOBILE_GLUES
        if (isMobileGl) {
            return RendererConfigSpec.createMobileGlSpec()
        }

        return RendererConfigSpec()
    }

    private fun getConfigStateFileForRenderer(rendererId: String): File {
        val safeId = rendererId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(externalRenderersDir, "config_state_${safeId}.json")
    }

    fun getRendererConfigState(descriptor: RendererDescriptor): RendererConfigState {
        val spec = getRendererConfigSpec(descriptor)
        val file = getConfigStateFileForRenderer(descriptor.id)
        val savedState = runCatching {
            if (file.isFile) {
                val json = JSONObject(file.readText())
                val selObj = json.optJSONObject("selectedValues") ?: JSONObject()
                val selMap = mutableMapOf<String, String>()
                val sKeys = selObj.keys()
                while (sKeys.hasNext()) {
                    val k = sKeys.next()
                    selMap[k] = selObj.optString(k)
                }

                val togObj = json.optJSONObject("toggledKeys") ?: JSONObject()
                val togMap = mutableMapOf<String, Boolean>()
                val tKeys = togObj.keys()
                while (tKeys.hasNext()) {
                    val k = tKeys.next()
                    togMap[k] = togObj.optBoolean(k)
                }

                RendererConfigState(selectedValues = selMap, toggledKeys = togMap)
            } else null
        }.getOrNull()

        val selMap = spec.selectables.associate { it.key to (savedState?.selectedValues?.get(it.key) ?: it.defaultValue) }.toMutableMap()
        val togMap = spec.toggleables.associate { it.key to (savedState?.toggledKeys?.get(it.key) ?: it.defaultEnabled) }.toMutableMap()
        return RendererConfigState(selectedValues = selMap, toggledKeys = togMap)
    }

    fun saveRendererConfigState(descriptor: RendererDescriptor, state: RendererConfigState) {
        val file = getConfigStateFileForRenderer(descriptor.id)
        runCatching {
            externalRenderersDir.mkdirs()
            val json = JSONObject()
            val selObj = JSONObject()
            state.selectedValues.forEach { (k, v) -> selObj.put(k, v) }
            json.put("selectedValues", selObj)

            val togObj = JSONObject()
            state.toggledKeys.forEach { (k, v) -> togObj.put(k, v) }
            json.put("toggledKeys", togObj)

            file.writeText(json.toString(2))
        }
    }

    fun getRendererConfigSummary(descriptor: RendererDescriptor, state: RendererConfigState = getRendererConfigState(descriptor)): String {
        val spec = getRendererConfigSpec(descriptor)
        val primarySel = spec.selectables.firstOrNull()?.let {
            state.selectedValues[it.key] ?: it.defaultValue
        }
        val activeToggles = spec.toggleables.count {
            state.toggledKeys[it.key] ?: it.defaultEnabled
        }
        return when {
            primarySel != null && activeToggles > 0 -> "$primarySel ($activeToggles active)"
            primarySel != null -> primarySel
            activeToggles > 0 -> "$activeToggles active"
            else -> "Standard"
        }
    }

    fun getEffectiveEnvironment(descriptor: RendererDescriptor, state: RendererConfigState? = null): Map<String, String> {
        val spec = getRendererConfigSpec(descriptor)
        val currentState = state ?: getRendererConfigState(descriptor)
        val env = linkedMapOf<String, String>()

        for (sel in spec.selectables) {
            val v = currentState.selectedValues[sel.key] ?: sel.defaultValue
            if (v.isNotBlank()) {
                env[sel.key] = v
            }
        }

        for (tog in spec.toggleables) {
            val enabled = currentState.toggledKeys[tog.key] ?: tog.defaultEnabled
            if (enabled) {
                env[tog.key] = tog.value
                if (tog.key == "MOBILEGL_USE_ANGLE") {
                    env["MG_ENABLE_ANGLE"] = "1"
                }
                if (tog.key == "MG_ENABLE_EXT_COMPUTE") {
                    env["MG_ENABLE_EXT_GL43"] = "1"
                }
            } else {
                if (tog.key == "MOBILEGL_USE_ANGLE") {
                    env["MOBILEGL_USE_ANGLE"] = "0"
                    env["MG_ENABLE_ANGLE"] = "0"
                }
                if (tog.key == "MG_ENABLE_NO_ERROR") {
                    env["MG_ENABLE_NO_ERROR"] = "0"
                }
            }
        }

        return env
    }

    fun getAvailableRendererConfigs(descriptor: RendererDescriptor): List<RendererConfigOption> {
        if (descriptor.isPlugin && descriptor.pluginInfo != null) {
            return descriptor.pluginInfo.configurations
        }
        return emptyList()
    }

    private fun getConfigFileForRenderer(rendererId: String): File {
        val safeId = rendererId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(externalRenderersDir, "config_${safeId}.txt")
    }

    fun getSelectedRendererConfig(descriptor: RendererDescriptor): RendererConfigOption {
        val options = getAvailableRendererConfigs(descriptor)
        val file = getConfigFileForRenderer(descriptor.id)
        val savedId = runCatching {
            if (file.isFile) file.readText().trim() else null
        }.getOrNull()
        return options.firstOrNull { it.id == savedId }
            ?: options.firstOrNull()
            ?: RendererConfigOption("default", "Default")
    }

    fun setSelectedRendererConfig(descriptor: RendererDescriptor, configOption: RendererConfigOption) {
        val file = getConfigFileForRenderer(descriptor.id)
        runCatching {
            externalRenderersDir.mkdirs()
            file.writeText(configOption.id)
        }
    }

    fun getAvailableRenderers(): List<RendererDescriptor> {
        val list = mutableListOf<RendererDescriptor>()
        list.add(RendererDescriptor.fromBuiltIn(RendererType.KRYPTON_WRAPPER))
        val plugins = getInstalledPlugins()
        for (plugin in plugins) {
            list.add(RendererDescriptor.fromPlugin(plugin))
        }
        return list
    }

    fun getInstalledPlugins(abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()): List<RendererPlugin> {
        val result = mutableListOf<RendererPlugin>()
        val seenIds = mutableSetOf<String>()

        // 1. Scan directory plugins in externalRenderersDir/plugins
        val subdirs = pluginsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (dir in subdirs) {
            val jsonFile = File(dir, "plugin.json")
            if (jsonFile.isFile) {
                runCatching {
                    val json = JSONObject(jsonFile.readText())
                    val id = json.getString("id")
                    val displayName = json.getString("displayName")
                    val glLib = json.optString("glLibFileName", "libGL.so")
                    val eglLib = json.optString("eglLibFileName", "libEGL.so")
                    val sourceFile = json.optString("sourceFileName").takeIf { it.isNotBlank() }?.let { File(dir, it) }

                    val libDir = File(dir, "libs/${abi.androidAbi}").takeIf { it.isDirectory }
                        ?: File(dir, "libs").takeIf { it.isDirectory }
                        ?: dir
                    val primaryLib = File(libDir, glLib)

                    val envMap = linkedMapOf<String, String>()
                    val envObj = json.optJSONObject("environment")
                    if (envObj != null) {
                        val keys = envObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            envMap[k] = envObj.getString(k)
                        }
                    }

                    val jvmMap = linkedMapOf<String, String>()
                    val jvmObj = json.optJSONObject("jvmProperties")
                    if (jvmObj != null) {
                        val keys = jvmObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            jvmMap[k] = jvmObj.getString(k)
                        }
                    }

                    val dlopenLibs = mutableListOf<String>()
                    val dlopenArr = json.optJSONArray("dlopenLibPaths")
                    if (dlopenArr != null) {
                        for (i in 0 until dlopenArr.length()) {
                            dlopenLibs.add(dlopenArr.getString(i))
                        }
                    }

                    if (seenIds.add(id)) {
                        result.add(
                            RendererPlugin(
                                id = id,
                                displayName = displayName,
                                glLibFileName = glLib,
                                eglLibFileName = eglLib,
                                nativeLibDir = libDir,
                                primaryLibFile = primaryLib,
                                dlopenLibPaths = dlopenLibs,
                                environment = envMap,
                                jvmProperties = jvmMap,
                                sourceFile = sourceFile,
                                isInstalledApp = false,
                                minMcVer = json.optString("minMcVer").takeIf { it.isNotBlank() },
                                maxMcVer = json.optString("maxMcVer").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }
        }

        // 2. Scan installed Android packages matching ZalithLauncher2 / FCL plugin metadata
        val systemPlugins = scanInstalledPluginPackages()
        for (plugin in systemPlugins) {
            if (seenIds.add(plugin.id)) {
                result.add(plugin)
            }
        }

        return result
    }

    fun scanInstalledPluginPackages(): List<RendererPlugin> {
        val context = appContext ?: return emptyList()
        val pm = context.packageManager
        val result = mutableListOf<RendererPlugin>()
        val seenPackages = mutableSetOf<String>()

        val candidateAppInfos = mutableListOf<ApplicationInfo>()

        // 1. Query activities with ACTION_MAIN + CATEGORY_LAUNCHER (standard for plugin APKs)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        runCatching {
            val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA)
            for (info in resolveInfos) {
                val pkgName = info.activityInfo?.packageName ?: continue
                if (pkgName == context.packageName || !seenPackages.add(pkgName)) continue
                val appInfo = info.activityInfo?.applicationInfo
                    ?: runCatching { pm.getApplicationInfo(pkgName, PackageManager.GET_META_DATA) }.getOrNull()
                if (appInfo != null) {
                    candidateAppInfos.add(appInfo)
                }
            }
        }

        // 2. Also check getInstalledPackages for any packages without launcher activity
        runCatching {
            val installed = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in installed) {
                val pkgName = pkg.packageName
                if (pkgName == context.packageName || !seenPackages.add(pkgName)) continue
                val appInfo = pkg.applicationInfo ?: continue
                candidateAppInfos.add(appInfo)
            }
        }

        for (rawAppInfo in candidateAppInfos) {
            val appInfo = if (rawAppInfo.metaData == null) {
                runCatching { pm.getApplicationInfo(rawAppInfo.packageName, PackageManager.GET_META_DATA) }.getOrNull() ?: rawAppInfo
            } else {
                rawAppInfo
            }
            val metaData = appInfo.metaData ?: continue

            val hasV2 = metaData.containsKey("fclPlugin_V2")
            val hasV1 = metaData.containsKey("fclPlugin")
            val hasBackendTitle = metaData.containsKey("title_backend_type")
            val isRendererPkg = appInfo.packageName.contains("renderer", ignoreCase = true) ||
                appInfo.packageName.contains("fcl", ignoreCase = true) ||
                appInfo.packageName.contains("mobilegl", ignoreCase = true) ||
                appInfo.packageName.contains("mobileglues", ignoreCase = true) ||
                appInfo.packageName.contains("driver", ignoreCase = true)

            if (hasV2 || hasV1 || hasBackendTitle || isRendererPkg) {
                var config: PluginConfigData? = null
                val labelName = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull().orEmpty()
                val fallbackName = labelName.ifBlank { appInfo.packageName }

                if (hasV2) {
                    val resId = metaData.getInt("fclPlugin_V2", 0)
                    val jsonStr = runCatching {
                        if (resId != 0) {
                            pm.getResourcesForApplication(appInfo).getString(resId)
                        } else {
                            metaData.getString("fclPlugin_V2")
                        }
                    }.getOrNull()
                    if (!jsonStr.isNullOrBlank()) {
                        config = PluginConfigData.parse(
                            jsonStr = jsonStr,
                            fallbackId = appInfo.packageName,
                            fallbackName = fallbackName
                        )
                    }
                }

                // If V2 was not present or didn't yield a config, check legacy fclPlugin or title_backend_type
                if (config == null && (hasV1 || hasBackendTitle)) {
                    val titleResId = metaData.getInt("title_backend_type", 0)
                    val backendTitle = runCatching {
                        if (titleResId != 0) {
                            pm.getResourcesForApplication(appInfo).getString(titleResId)
                        } else {
                            metaData.getString("title_backend_type")
                        }
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackName

                    val glLib = metaData.getString("renderer")
                        ?: metaData.getString("glPath")
                        ?: "libGL.so"
                    val eglLib = metaData.getString("eglPath") ?: "libEGL.so"

                    val dlopenStr = metaData.getString("dlopenLibs")
                    val dlopenLibs = if (!dlopenStr.isNullOrBlank()) {
                        dlopenStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }

                    config = PluginConfigData(
                        id = appInfo.packageName,
                        displayName = backendTitle,
                        glLibFileName = glLib,
                        eglLibFileName = eglLib,
                        dlopenLibPaths = dlopenLibs
                    )
                }

                // Also check if config.json or fclPlugin.json is inside assets/ of the plugin APK
                if (config == null) {
                    val apkPath = appInfo.sourceDir ?: appInfo.publicSourceDir
                    if (!apkPath.isNullOrBlank() && File(apkPath).isFile) {
                        runCatching {
                            ZipFile(File(apkPath)).use { zip ->
                                val entry = zip.getEntry("assets/config.json")
                                    ?: zip.getEntry("assets/renderer.json")
                                    ?: zip.getEntry("assets/fclPlugin.json")
                                    ?: zip.getEntry("config.json")
                                if (entry != null) {
                                    zip.getInputStream(entry).bufferedReader().use { reader ->
                                        val text = reader.readText()
                                        if (text.isNotBlank()) {
                                            config = PluginConfigData.parse(
                                                jsonStr = text,
                                                fallbackId = appInfo.packageName,
                                                fallbackName = fallbackName
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val finalConfig = config ?: PluginConfigData(
                    id = appInfo.packageName,
                    displayName = fallbackName,
                    glLibFileName = "libGL.so",
                    eglLibFileName = "libEGL.so"
                )

                val nativeDir = File(appInfo.nativeLibraryDir)
                val primaryLib = File(nativeDir, finalConfig.glLibFileName)

                result.add(
                    RendererPlugin(
                        id = finalConfig.id,
                        displayName = finalConfig.displayName,
                        glLibFileName = finalConfig.glLibFileName,
                        eglLibFileName = finalConfig.eglLibFileName,
                        nativeLibDir = nativeDir,
                        primaryLibFile = primaryLib,
                        dlopenLibPaths = finalConfig.dlopenLibPaths,
                        environment = finalConfig.environment,
                        jvmProperties = finalConfig.jvmProperties,
                        configSpec = finalConfig.configSpec,
                        configurations = finalConfig.configurations,
                        sourcePackage = appInfo.packageName,
                        isInstalledApp = true,
                        minMcVer = finalConfig.minMcVer,
                        maxMcVer = finalConfig.maxMcVer
                    )
                )
            }
        }
        return result
    }

    fun importPlugin(file: File, originalName: String? = null): Result<RendererPlugin> = runCatching {
        pluginsDir.mkdirs()
        val nameToUse = originalName ?: file.name
        val safeId = nameToUse.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        val pluginId = if (safeId.isBlank()) "plugin_${System.currentTimeMillis()}" else safeId
        val targetDir = File(pluginsDir, pluginId).apply { mkdirs() }

        val targetArchive = File(targetDir, nameToUse)
        if (file.absolutePath != targetArchive.absolutePath) {
            file.copyTo(targetArchive, overwrite = true)
        }

        val config = if (nameToUse.endsWith(".so", ignoreCase = true)) {
            val abi = RuntimeAbi.detectDeviceAbi()
            val libDir = File(targetDir, "libs/${abi.androidAbi}").apply { mkdirs() }
            val soFile = File(libDir, nameToUse)
            targetArchive.copyTo(soFile, overwrite = true)
            soFile.setReadable(true, false)
            soFile.setExecutable(true, false)
            PluginConfigData(
                id = pluginId,
                displayName = nameToUse.removeSuffix(".so").replace('_', ' ').replace('-', ' ').trim(),
                glLibFileName = nameToUse,
                eglLibFileName = "libEGL.so"
            )
        } else {
            extractPluginFromArchive(targetArchive, targetDir)
        }

        val finalTargetDir = if (config.id != pluginId && !File(pluginsDir, config.id).exists()) {
            val renamed = File(pluginsDir, config.id)
            if (targetDir.renameTo(renamed)) renamed else targetDir
        } else {
            targetDir
        }

        val pluginJson = JSONObject().apply {
            put("id", config.id)
            put("displayName", config.displayName)
            put("glLibFileName", config.glLibFileName)
            put("eglLibFileName", config.eglLibFileName)
            put("dlopenLibPaths", JSONArray(config.dlopenLibPaths))
            put("environment", JSONObject(config.environment))
            put("jvmProperties", JSONObject(config.jvmProperties))
            put("sourceFileName", targetArchive.name)
            put("minMcVer", config.minMcVer ?: "")
            put("maxMcVer", config.maxMcVer ?: "")
        }
        File(finalTargetDir, "plugin.json").writeText(pluginJson.toString(2))

        val abi = RuntimeAbi.detectDeviceAbi()
        val libDir = File(finalTargetDir, "libs/${abi.androidAbi}").takeIf { it.isDirectory }
            ?: File(finalTargetDir, "libs").takeIf { it.isDirectory }
            ?: finalTargetDir
        val primaryLib = File(libDir, config.glLibFileName)

        val plugin = RendererPlugin(
            id = config.id,
            displayName = config.displayName,
            glLibFileName = config.glLibFileName,
            eglLibFileName = config.eglLibFileName,
            nativeLibDir = libDir,
            primaryLibFile = primaryLib,
            dlopenLibPaths = config.dlopenLibPaths,
            environment = config.environment,
            jvmProperties = config.jvmProperties,
            configSpec = config.configSpec,
            configurations = config.configurations,
            sourceFile = targetArchive,
            isInstalledApp = false,
            minMcVer = config.minMcVer,
            maxMcVer = config.maxMcVer
        )

        setSelectedRenderer(RendererDescriptor.fromPlugin(plugin))
        plugin
    }

    fun importPluginFromStream(stream: InputStream, originalName: String): Result<RendererPlugin> = runCatching {
        val tempFile = File.createTempFile("import_renderer_", "_$originalName")
        try {
            FileOutputStream(tempFile).use { output ->
                stream.copyTo(output)
            }
            importPlugin(tempFile, originalName).getOrThrow()
        } finally {
            tempFile.delete()
        }
    }

    fun deletePlugin(pluginId: String): Boolean {
        val safeId = pluginId.removePrefix("plugin:")
        var targetDir = File(pluginsDir, safeId)
        if (!targetDir.isDirectory) {
            val subdirs = pluginsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
            val matching = subdirs.firstOrNull { d ->
                val jf = File(d, "plugin.json")
                if (jf.isFile) {
                    runCatching { JSONObject(jf.readText()).optString("id") == safeId }.getOrDefault(false)
                } else false
            }
            if (matching != null) {
                targetDir = matching
            }
        }
        val deleted = if (targetDir.isDirectory) {
            targetDir.deleteRecursively()
        } else {
            false
        }
        val savedId = runCatching {
            if (preferenceFile.isFile) preferenceFile.readText().trim() else ""
        }.getOrDefault("")
        if (savedId.contains(safeId, ignoreCase = true)) {
            setSelectedRenderer(RendererType.KRYPTON_WRAPPER)
        }
        return deleted
    }

    private fun extractPluginFromArchive(
        archiveFile: File,
        targetDir: File,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi()
    ): PluginConfigData {
        targetDir.mkdirs()
        var detectedConfig: PluginConfigData? = null
        val detectedSos = mutableListOf<String>()

        val fallbackId = archiveFile.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
        val fallbackName = archiveFile.nameWithoutExtension.replace('_', ' ').replace('-', ' ').trim()

        runCatching {
            ZipFile(archiveFile).use { zip ->
                val configEntryNames = listOf(
                    "assets/config.json",
                    "assets/renderer.json",
                    "assets/fclPlugin.json",
                    "assets/plugin.json",
                    "config.json",
                    "renderer.json",
                    "plugin.json"
                )
                for (entryName in configEntryNames) {
                    val entry = zip.getEntry(entryName)
                    if (entry != null) {
                        runCatching {
                            zip.getInputStream(entry).bufferedReader().use { reader ->
                                val text = reader.readText()
                                if (text.isNotBlank()) {
                                    detectedConfig = PluginConfigData.parse(text, fallbackId, fallbackName)
                                }
                            }
                        }
                        if (detectedConfig != null) break
                    }
                }

                if (detectedConfig == null) {
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && (name.endsWith("config.json") || name.endsWith("plugin.json") || name.endsWith("renderer.json"))) {
                            runCatching {
                                zip.getInputStream(entry).bufferedReader().use { reader ->
                                    val text = reader.readText()
                                    if (text.isNotBlank()) {
                                        detectedConfig = PluginConfigData.parse(text, fallbackId, fallbackName)
                                    }
                                }
                            }
                            if (detectedConfig != null) break
                        }
                    }
                }

                val abiPrefix = "lib/${abi.androidAbi}/"
                val jniPrefix = "jni/${abi.androidAbi}/"
                val genericLibPrefix = "lib/"
                val genericJniPrefix = "jni/"

                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val entry = zipEntries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (!name.endsWith(".so", ignoreCase = true)) continue

                    val soFileName = name.substringAfterLast('/')
                    val destDir = when {
                        name.startsWith(abiPrefix) || name.startsWith(jniPrefix) -> {
                            File(targetDir, "libs/${abi.androidAbi}").apply { mkdirs() }
                        }
                        name.startsWith(genericLibPrefix) || name.startsWith(genericJniPrefix) -> {
                            val entryAbi = name.removePrefix(genericLibPrefix).removePrefix(genericJniPrefix).substringBefore('/')
                            File(targetDir, "libs/$entryAbi").apply { mkdirs() }
                        }
                        else -> {
                            File(targetDir, "libs/${abi.androidAbi}").apply { mkdirs() }
                        }
                    }

                    val outFile = File(destDir, soFileName)
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outFile.setReadable(true, false)
                    outFile.setExecutable(true, false)

                    if (destDir.name == abi.androidAbi || destDir.parentFile?.name != "libs") {
                        detectedSos.add(soFileName)
                    }
                }
            }
        }

        if (detectedConfig == null && appContext != null) {
            val pm = appContext.packageManager
            runCatching {
                val archiveInfo = pm.getPackageArchiveInfo(archiveFile.absolutePath, PackageManager.GET_META_DATA)
                val appInfo = archiveInfo?.applicationInfo
                val metaData = appInfo?.metaData
                if (appInfo != null && metaData != null && metaData.containsKey("fclPlugin_V2")) {
                    appInfo.sourceDir = archiveFile.absolutePath
                    appInfo.publicSourceDir = archiveFile.absolutePath
                    val resId = metaData.getInt("fclPlugin_V2")
                    val jsonStr = if (resId != 0) {
                        pm.getResourcesForApplication(appInfo).getString(resId)
                    } else {
                        metaData.getString("fclPlugin_V2")
                    }
                    if (!jsonStr.isNullOrBlank()) {
                        detectedConfig = PluginConfigData.parse(jsonStr, fallbackId, fallbackName)
                    }
                }
            }
        }

        val primaryGl = detectedConfig?.glLibFileName?.takeIf { it.isNotBlank() }
            ?: detectedSos.firstOrNull { it.contains("gl", ignoreCase = true) }
            ?: detectedSos.firstOrNull()
            ?: "libGL.so"

        val eglLib = detectedConfig?.eglLibFileName?.takeIf { it.isNotBlank() }
            ?: detectedSos.firstOrNull { it.contains("egl", ignoreCase = true) }
            ?: "libEGL.so"

        return detectedConfig?.copy(
            glLibFileName = primaryGl,
            eglLibFileName = eglLib
        ) ?: PluginConfigData(
            id = fallbackId,
            displayName = fallbackName,
            glLibFileName = primaryGl,
            eglLibFileName = eglLib,
            configurations = emptyList()
        )
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

    suspend fun prepareRenderer(
        descriptor: RendererDescriptor = getSelectedRendererDescriptor(),
        configOption: RendererConfigOption? = null,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        mobileGluesConfig: MobileGluesConfig = MobileGluesConfig(),
        kryptonConfig: KryptonWrapperConfig = KryptonWrapperConfig(),
        forceReinstall: Boolean = false,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<PreparedRenderer> = withContext(Dispatchers.IO) {
        val effectiveConfig = configOption ?: getSelectedRendererConfig(descriptor)
        if (descriptor.isPlugin) {
            val plugin = descriptor.pluginInfo ?: getInstalledPlugins(abi).firstOrNull { "plugin:${it.id}" == descriptor.id }
            if (plugin != null) {
                return@withContext preparePluginRenderer(
                    plugin = plugin,
                    configOption = effectiveConfig,
                    abi = abi,
                    forceReinstall = forceReinstall,
                    onProgress = onProgress
                )
            }
        }
        val type = descriptor.builtInType ?: RendererType.KRYPTON_WRAPPER
        return@withContext prepareRenderer(
            type = type,
            configOption = effectiveConfig,
            abi = abi,
            mobileGluesConfig = mobileGluesConfig,
            kryptonConfig = kryptonConfig,
            forceReinstall = forceReinstall,
            onProgress = onProgress
        )
    }

    suspend fun preparePluginRenderer(
        plugin: RendererPlugin,
        configOption: RendererConfigOption? = null,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        forceReinstall: Boolean = false,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<PreparedRenderer> = withContext(Dispatchers.IO) {
        val activeConfig = configOption ?: getSelectedRendererConfig(RendererDescriptor.fromPlugin(plugin))
        runCatching {
            onProgress("Preparing ${plugin.displayName} plugin...", 0.1f)

            val activeLibDir = when {
                plugin.isInstalledApp -> {
                    val cacheDir = File(internalExecRenderersDir, "plugins/${plugin.id}/${abi.androidAbi}").apply { mkdirs() }
                    if (plugin.nativeLibDir.isDirectory) {
                        plugin.nativeLibDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.forEach { src ->
                            val dest = File(cacheDir, src.name)
                            if (!dest.isFile || dest.length() != src.length() || forceReinstall) {
                                runCatching {
                                    src.copyTo(dest, overwrite = true)
                                    dest.setReadable(true, false)
                                    dest.setExecutable(true, false)
                                }
                            }
                        }
                    }
                    if (cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }.isNullOrEmpty() && plugin.sourcePackage != null) {
                        val pm = appContext?.packageManager
                        val appInfo = runCatching { pm?.getApplicationInfo(plugin.sourcePackage, 0) }.getOrNull()
                        val apkPath = appInfo?.sourceDir ?: appInfo?.publicSourceDir
                        if (!apkPath.isNullOrBlank() && File(apkPath).isFile) {
                            runCatching {
                                extractPluginFromArchive(File(apkPath), cacheDir, abi)
                            }
                        }
                    }
                    cacheDir
                }
                File(plugin.nativeLibDir, abi.androidAbi).isDirectory -> File(plugin.nativeLibDir, abi.androidAbi)
                else -> plugin.nativeLibDir
            }.apply { mkdirs() }

            // Ensure Android LWJGL 3.3.3 & OpenAL native .so libraries are in activeLibDir
            runCatching {
                ensureAndroidLwjglAndOpenAlNatives(
                    abi = abi,
                    targetDir = activeLibDir,
                    forceReinstall = forceReinstall,
                    onProgress = onProgress
                )
            }.onFailure { if (it is CancellationException) throw it }

            var primaryGlFile = File(activeLibDir, plugin.glLibFileName)
            if (!primaryGlFile.isFile || primaryGlFile.length() == 0L) {
                val fallbackSo = activeLibDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }
                    ?.firstOrNull { it.name.contains("gl", ignoreCase = true) }
                    ?: activeLibDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.firstOrNull()
                if (fallbackSo != null) {
                    primaryGlFile = fallbackSo
                }
            }

            primaryGlFile.setReadable(true, false)
            primaryGlFile.setExecutable(true, false)

            val envVars = linkedMapOf<String, String>()
            envVars["POJAV_RENDERER"] = plugin.id
            envVars["DOMREI_RENDERER"] = plugin.id
            envVars["LIBGL_ES"] = "3"
            envVars["LIBGL_EGL"] = plugin.eglLibFileName
            envVars.putAll(plugin.environment)
            envVars.putAll(activeConfig.environment)
            envVars.putAll(getEffectiveEnvironment(RendererDescriptor.fromPlugin(plugin)))

            val jvmProps = linkedMapOf<String, String>()
            jvmProps["org.lwjgl.opengl.libname"] = primaryGlFile.name
            jvmProps["org.lwjgl.egl.libname"] = plugin.eglLibFileName
            jvmProps["domrei.renderer.id"] = plugin.id
            jvmProps["domrei.renderer.lib"] = primaryGlFile.absolutePath
            jvmProps.putAll(plugin.jvmProperties)
            jvmProps.putAll(activeConfig.jvmProperties)

            val freetypeSo = File(activeLibDir, "libfreetype.so")
            if (freetypeSo.isFile) {
                jvmProps["org.lwjgl.freetype.libname"] = freetypeSo.absolutePath
            }
            val openalSo = File(activeLibDir, "libopenal.so")
            if (openalSo.isFile) {
                jvmProps["org.lwjgl.openal.libname"] = openalSo.absolutePath
            }

            onProgress("${plugin.displayName} ready", 1f)

            PreparedRenderer(
                type = RendererType.CUSTOM_PLUGIN,
                nativeLibFile = primaryGlFile,
                nativeLibDir = activeLibDir,
                configFile = null,
                environmentVars = envVars,
                jvmSystemProperties = jvmProps,
                customDisplayName = plugin.displayName
            )
        }.onFailure { if (it is CancellationException) throw it }
    }

    /**
     * Downloads and extracts the renderer's native `.so` library (`libmobileglues.so` or `libng_gl4es.so`)
     * for the target ABI from ZalithLauncher2's AAR artifacts, and prepares its runtime environment.
     */
    suspend fun prepareRenderer(
        type: RendererType = getSelectedRenderer(),
        configOption: RendererConfigOption? = null,
        abi: RuntimeAbi = RuntimeAbi.detectDeviceAbi(),
        mobileGluesConfig: MobileGluesConfig = MobileGluesConfig(),
        kryptonConfig: KryptonWrapperConfig = KryptonWrapperConfig(),
        forceReinstall: Boolean = false,
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<PreparedRenderer> = withContext(Dispatchers.IO) {
        val activeConfig = configOption ?: getSelectedRendererConfig(RendererDescriptor.fromBuiltIn(type))
        val configStateEnv = getEffectiveEnvironment(RendererDescriptor.fromBuiltIn(type))
        val combinedEnv = activeConfig.environment.toMutableMap().apply { putAll(configStateEnv) }
        var effectiveMgConfig = mobileGluesConfig
        if (combinedEnv["MG_ENABLE_ANGLE"] == "1" || combinedEnv["MOBILEGL_USE_ANGLE"] == "1") {
            effectiveMgConfig = effectiveMgConfig.copy(enableANGLE = 1)
        } else if (combinedEnv["MOBILEGL_USE_ANGLE"] == "0") {
            effectiveMgConfig = effectiveMgConfig.copy(enableANGLE = 0)
        }
        if (combinedEnv["MG_ENABLE_NO_ERROR"] == "1") {
            effectiveMgConfig = effectiveMgConfig.copy(enableNoError = 1)
        } else if (combinedEnv["MG_ENABLE_NO_ERROR"] == "0") {
            effectiveMgConfig = effectiveMgConfig.copy(enableNoError = 0)
        }
        combinedEnv["MG_MULTIDRAW_MODE"]?.toIntOrNull()?.let {
            effectiveMgConfig = effectiveMgConfig.copy(multidrawMode = it)
        }
        combinedEnv["MG_MAX_GLSL_CACHE_SIZE"]?.toIntOrNull()?.let {
            effectiveMgConfig = effectiveMgConfig.copy(maxGlslCacheSize = it)
        }
        if (combinedEnv["MG_ENABLE_EXT_COMPUTE"] == "1") {
            effectiveMgConfig = effectiveMgConfig.copy(enableExtComputeShader = 1)
        }
        if (combinedEnv["MG_ENABLE_EXT_GL43"] == "1") {
            effectiveMgConfig = effectiveMgConfig.copy(enableExtGL43 = 1)
        }
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
            }.onFailure { if (it is CancellationException) throw it }

            val configFile = when (type) {
                RendererType.MOBILE_GLUES -> {
                    val mgDir = File(externalRenderersDir, "mobileglues")
                    mgDir.mkdirs()
                    val jsonStr = effectiveMgConfig.toJsonString()
                    File(mgDir, "mg_config.json").writeText(jsonStr)
                    File(mgDir, "config.json").apply {
                        writeText(jsonStr)
                    }
                }
                RendererType.KRYPTON_WRAPPER,
                RendererType.CUSTOM_PLUGIN -> null
            }

            val envVars = buildRendererEnvironment(
                type = type,
                nativeLibFile = activeLibFile,
                configFile = configFile,
                mobileGluesConfig = effectiveMgConfig,
                kryptonConfig = kryptonConfig
            ).toMutableMap()
            envVars.putAll(combinedEnv)

            val jvmProps = buildRendererJvmProperties(
                type = type,
                nativeLibFile = activeLibFile
            ).toMutableMap()
            jvmProps.putAll(activeConfig.jvmProperties)

            onProgress("${type.displayName} ready", 1f)

            PreparedRenderer(
                type = type,
                nativeLibFile = activeLibFile,
                nativeLibDir = activeLibDir,
                configFile = configFile,
                environmentVars = envVars,
                jvmSystemProperties = jvmProps
            )
        }.onFailure { if (it is CancellationException) throw it }
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
            RendererType.CUSTOM_PLUGIN -> {
                env["POJAV_RENDERER"] = RendererType.CUSTOM_PLUGIN.id
                env["DOMREI_RENDERER"] = RendererType.CUSTOM_PLUGIN.id
                env["LIBGL_ES"] = "3"
                env["LIBGL_EGL"] = type.eglLibFileName
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

    private suspend fun ensureAndroidLwjglAndOpenAlNatives(
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

    private suspend fun downloadFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
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
                        currentCoroutineContext().ensureActive()
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
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
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
