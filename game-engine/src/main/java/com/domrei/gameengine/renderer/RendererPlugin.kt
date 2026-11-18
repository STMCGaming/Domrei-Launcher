package com.domrei.gameengine.renderer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Metadata and runtime specifications for an external renderer plugin,
 * following the ZalithLauncher2 / FoldCraftLauncher (FCL) plugin architecture (APKs / AARs).
 */
data class RendererPlugin(
    val id: String,
    val displayName: String,
    val glLibFileName: String = "libGL.so",
    val eglLibFileName: String = "libEGL.so",
    val nativeLibDir: File,
    val primaryLibFile: File,
    val dlopenLibPaths: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val jvmProperties: Map<String, String> = emptyMap(),
    val configSpec: RendererConfigSpec = RendererConfigSpec(),
    val configurations: List<RendererConfigOption> = emptyList(),
    val sourcePackage: String? = null,
    val sourceFile: File? = null,
    val isInstalledApp: Boolean = false,
    val version: String = "1.0",
    val minMcVer: String? = null,
    val maxMcVer: String? = null
)

/**
 * A selectable configuration item (dropdown selection) from ZalithLauncher2 / FCL DSL.
 */
data class SelectableConfigItem(
    val key: String,
    val title: String = key,
    val options: List<String>,
    val defaultValue: String = options.firstOrNull().orEmpty()
)

/**
 * A toggleable configuration item (Switch) from ZalithLauncher2 / FCL DSL.
 */
data class ToggleableConfigItem(
    val key: String,
    val title: String = key,
    val value: String = "1",
    val defaultEnabled: Boolean = false,
    val description: String? = null
)

/**
 * Complete specification of available configuration options for a renderer.
 */
data class RendererConfigSpec(
    val selectables: List<SelectableConfigItem> = emptyList(),
    val toggleables: List<ToggleableConfigItem> = emptyList()
) {
    val isEmpty: Boolean
        get() = selectables.isEmpty() && toggleables.isEmpty()

    val isNotEmpty: Boolean
        get() = !isEmpty

    companion object {
        fun createMobileGlSpec(): RendererConfigSpec = RendererConfigSpec(
            selectables = listOf(
                SelectableConfigItem(
                    key = "MOBILEGL_BACKEND_TYPE",
                    title = "Backend Type",
                    options = listOf("DirectGLES", "DirectVulkan"),
                    defaultValue = "DirectGLES"
                )
            ),
            toggleables = listOf(
                ToggleableConfigItem(
                    key = "MOBILEGL_USE_ANGLE",
                    title = "Use ANGLE (Vulkan)",
                    value = "1",
                    defaultEnabled = false,
                    description = "Run OpenGL ES over ANGLE Vulkan backend"
                ),
                ToggleableConfigItem(
                    key = "MOBILEGL_RELAXED_SEMANTICS",
                    title = "Relaxed Core Semantics",
                    value = "1",
                    defaultEnabled = false,
                    description = "Relax strict GL rules to improve mod compatibility"
                ),
                ToggleableConfigItem(
                    key = "MG_ENABLE_NO_ERROR",
                    title = "Ignore GL Errors",
                    value = "1",
                    defaultEnabled = true,
                    description = "Suppress non-fatal errors during gameplay"
                ),
                ToggleableConfigItem(
                    key = "MG_MULTIDRAW_MODE",
                    title = "MultiDraw Indirect",
                    value = "5",
                    defaultEnabled = true,
                    description = "Hardware multidraw acceleration for 1.17+ versions"
                ),
                ToggleableConfigItem(
                    key = "MG_ENABLE_EXT_COMPUTE",
                    title = "Compute Shaders & GL 4.3",
                    value = "1",
                    defaultEnabled = false,
                    description = "Enable compute shaders for modern shaders and Iris"
                )
            )
        )

        fun createKryptonSpec(): RendererConfigSpec = RendererConfigSpec(
            selectables = listOf(
                SelectableConfigItem(
                    key = "LIBGL_GL",
                    title = "OpenGL Profile",
                    options = listOf("3.1", "2.1"),
                    defaultValue = "3.1"
                )
            ),
            toggleables = listOf(
                ToggleableConfigItem(
                    key = "LIBGL_VBO",
                    title = "VBO Buffers",
                    value = "1",
                    defaultEnabled = true,
                    description = "Hardware vertex buffer acceleration"
                ),
                ToggleableConfigItem(
                    key = "LIBGL_NOERROR",
                    title = "Ignore Errors",
                    value = "1",
                    defaultEnabled = true,
                    description = "Suppress OpenGL error checking for higher FPS"
                )
            )
        )
    }
}

/**
 * Persisted state of a renderer's configuration (selected dropdown values & switch toggles).
 */
data class RendererConfigState(
    val selectedValues: Map<String, String> = emptyMap(),
    val toggledKeys: Map<String, Boolean> = emptyMap()
)

/**
 * Configuration option / preset for a renderer or renderer plugin
 * (e.g. MobileGL DirectGLES vs DirectVulkan vs ANGLE, or custom plugin configurations).
 */
data class RendererConfigOption(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val jvmProperties: Map<String, String> = emptyMap()
) {
    companion object {
        fun createMobileGlPresets(): List<RendererConfigOption> = listOf(
            RendererConfigOption(
                id = "direct_gles",
                displayName = "DirectGLES (Default)",
                description = "Standard OpenGL ES 3.2 backend",
                environment = mapOf(
                    "MOBILEGL_BACKEND_TYPE" to "DirectGLES",
                    "MOBILEGL_USE_ANGLE" to "0",
                    "MG_ENABLE_ANGLE" to "0"
                )
            ),
            RendererConfigOption(
                id = "direct_gles_angle",
                displayName = "DirectGLES + ANGLE",
                description = "OpenGL ES over ANGLE Vulkan",
                environment = mapOf(
                    "MOBILEGL_BACKEND_TYPE" to "DirectGLES",
                    "MOBILEGL_USE_ANGLE" to "1",
                    "MG_ENABLE_ANGLE" to "1"
                )
            ),
            RendererConfigOption(
                id = "direct_vulkan",
                displayName = "DirectVulkan (Magma)",
                description = "Direct Vulkan backend via Magma",
                environment = mapOf(
                    "MOBILEGL_BACKEND_TYPE" to "DirectVulkan",
                    "MOBILEGL_USE_ANGLE" to "0"
                )
            ),
            RendererConfigOption(
                id = "compatibility",
                displayName = "Compatibility (Relaxed Semantics)",
                description = "Relaxed core-profile rules & error suppression",
                environment = mapOf(
                    "MOBILEGL_BACKEND_TYPE" to "DirectGLES",
                    "MOBILEGL_RELAXED_SEMANTICS" to "1",
                    "MG_ENABLE_NO_ERROR" to "1",
                    "MOBILEGL_COHERENT_AS_FLUSH" to "1"
                )
            ),
            RendererConfigOption(
                id = "high_performance",
                displayName = "High Performance",
                description = "Max GLSL cache, compute shaders & multidraw",
                environment = mapOf(
                    "MOBILEGL_BACKEND_TYPE" to "DirectGLES",
                    "MG_ENABLE_ANGLE" to "0",
                    "MG_ENABLE_NO_ERROR" to "1",
                    "MG_MULTIDRAW_MODE" to "5",
                    "MG_MAX_GLSL_CACHE_SIZE" to "256",
                    "MG_ENABLE_EXT_COMPUTE" to "1",
                    "MG_ENABLE_EXT_GL43" to "1"
                )
            )
        )

        fun createKryptonPresets(): List<RendererConfigOption> = listOf(
            RendererConfigOption(
                id = "krypton_gl31_gles3",
                displayName = "OpenGL 3.1 / GLES 3 (Default)",
                description = "Standard modern desktop GL 3.1 emulation",
                environment = mapOf(
                    "LIBGL_GL" to "31",
                    "LIBGL_ES" to "3"
                )
            ),
            RendererConfigOption(
                id = "krypton_gl21_gles2",
                displayName = "OpenGL 2.1 / GLES 2",
                description = "Legacy compatibility for older Minecraft versions",
                environment = mapOf(
                    "LIBGL_GL" to "21",
                    "LIBGL_ES" to "2"
                )
            ),
            RendererConfigOption(
                id = "krypton_perf",
                displayName = "Performance (VBO + No Error)",
                description = "VBO buffers enabled with error checks suppressed",
                environment = mapOf(
                    "LIBGL_GL" to "31",
                    "LIBGL_ES" to "3",
                    "LIBGL_VBO" to "1",
                    "LIBGL_NOERROR" to "1"
                )
            )
        )
    }
}

/**
 * Unified descriptor for any selectable renderer (built-in or plugin).
 */
data class RendererDescriptor(
    val id: String,
    val displayName: String,
    val isPlugin: Boolean = false,
    val builtInType: RendererType? = null,
    val pluginInfo: RendererPlugin? = null
) {
    val effectiveType: RendererType
        get() = builtInType ?: RendererType.CUSTOM_PLUGIN

    companion object {
        fun fromBuiltIn(type: RendererType): RendererDescriptor {
            return RendererDescriptor(
                id = type.id,
                displayName = type.displayName,
                isPlugin = false,
                builtInType = type,
                pluginInfo = null
            )
        }

        fun fromPlugin(plugin: RendererPlugin): RendererDescriptor {
            return RendererDescriptor(
                id = "plugin:${plugin.id}",
                displayName = plugin.displayName,
                isPlugin = true,
                builtInType = RendererType.CUSTOM_PLUGIN,
                pluginInfo = plugin
            )
        }
    }
}

/**
 * Parsed configuration data from a ZalithLauncher2 / FCL plugin JSON or manifest.
 */
data class PluginConfigData(
    val id: String,
    val displayName: String,
    val glLibFileName: String,
    val eglLibFileName: String,
    val dlopenLibPaths: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val jvmProperties: Map<String, String> = emptyMap(),
    val configSpec: RendererConfigSpec = RendererConfigSpec(),
    val configurations: List<RendererConfigOption> = emptyList(),
    val minMcVer: String? = null,
    val maxMcVer: String? = null
) {
    companion object {
        /**
         * Parses a ZalithLauncher2 `fclPlugin_V2` or FCL renderer JSON configuration.
         */
        fun parse(jsonStr: String, fallbackId: String, fallbackName: String): PluginConfigData {
            val json = runCatching { JSONObject(jsonStr) }.getOrDefault(JSONObject())

            val rawId = json.optString("rendererId").ifBlank {
                json.optString("id").ifBlank { fallbackId }
            }
            val rawName = json.optString("displayName").ifBlank {
                json.optString("name").ifBlank { fallbackName }
            }

            val glPath = json.optString("rendererGLPath").ifBlank {
                json.optString("glPath").ifBlank {
                    json.optString("rendererPath").ifBlank { "libGL.so" }
                }
            }.substringAfterLast('/')

            val eglPath = json.optString("rendererEGLPath").ifBlank {
                json.optString("eglPath").ifBlank { "libEGL.so" }
            }.substringAfterLast('/')

            val dlopenLibs = mutableListOf<String>()
            val dlopenArr = json.optJSONArray("dlopenLibPaths") ?: json.optJSONArray("dlopenLibs")
            if (dlopenArr != null) {
                for (i in 0 until dlopenArr.length()) {
                    val lib = dlopenArr.optString(i).trim()
                    if (lib.isNotBlank()) {
                        dlopenLibs.add(lib.substringAfterLast('/'))
                    }
                }
            }

            val envMap = linkedMapOf<String, String>()

            fun parseEnvList(arr: JSONArray) {
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    if (item is JSONObject) {
                        val key = item.optString("name").ifBlank { item.optString("key") }
                        val value = item.optString("value")
                        if (key.isNotBlank()) {
                            envMap[key] = value
                        }
                    } else if (item is String && '=' in item) {
                        val parts = item.split('=', limit = 2)
                        envMap[parts[0].trim()] = parts[1].trim()
                    }
                }
            }

            fun parseEnvObject(obj: JSONObject) {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v is JSONArray) {
                        parseEnvList(v)
                    } else if (v is JSONObject) {
                        parseEnvObject(v)
                    } else if (v is String) {
                        envMap[k] = v
                    } else if (v != null) {
                        envMap[k] = v.toString()
                    }
                }
            }

            val envArr = json.optJSONArray("env") ?: json.optJSONArray("normal") ?: json.optJSONArray("environment")
            if (envArr != null) {
                parseEnvList(envArr)
            }
            val envObj = json.optJSONObject("env") ?: json.optJSONObject("normal") ?: json.optJSONObject("environment")
            if (envObj != null) {
                parseEnvObject(envObj)
            }

            val jvmProps = linkedMapOf<String, String>()
            val jvmArr = json.optJSONArray("jvmArgs") ?: json.optJSONArray("jvmProperties") ?: json.optJSONArray("properties")
            if (jvmArr != null) {
                for (i in 0 until jvmArr.length()) {
                    val item = jvmArr.opt(i)
                    if (item is JSONObject) {
                        val k = item.optString("name").ifBlank { item.optString("key") }
                        val v = item.optString("value")
                        if (k.isNotBlank()) {
                            jvmProps[k] = v
                        }
                    } else {
                        val str = item.toString().trim()
                        val def = str.removePrefix("-D").trim()
                        if ('=' in def) {
                            val parts = def.split('=', limit = 2)
                            jvmProps[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
            val jvmObj = json.optJSONObject("jvmArgs") ?: json.optJSONObject("jvmProperties") ?: json.optJSONObject("properties")
            if (jvmObj != null) {
                val keys = jvmObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    jvmProps[k] = jvmObj.optString(k)
                }
            }

            val parsedConfigs = mutableListOf<RendererConfigOption>()

            fun parseConfigOption(obj: JSONObject): RendererConfigOption? {
                val cfgId = obj.optString("id").ifBlank { obj.optString("key") }
                val cfgName = obj.optString("displayName").ifBlank {
                    obj.optString("name").ifBlank { obj.optString("title").ifBlank { cfgId } }
                }
                if (cfgId.isBlank() && cfgName.isBlank()) return null
                val desc = obj.optString("description").takeIf { it.isNotBlank() }
                val cEnv = linkedMapOf<String, String>()
                val cEnvObj = obj.optJSONObject("env") ?: obj.optJSONObject("environment")
                if (cEnvObj != null) {
                    val kIter = cEnvObj.keys()
                    while (kIter.hasNext()) {
                        val k = kIter.next()
                        cEnv[k] = cEnvObj.optString(k)
                    }
                }
                val cEnvArr = obj.optJSONArray("env") ?: obj.optJSONArray("environment")
                if (cEnvArr != null) {
                    for (i in 0 until cEnvArr.length()) {
                        val item = cEnvArr.opt(i)
                        if (item is JSONObject) {
                            val k = item.optString("name").ifBlank { item.optString("key") }
                            val v = item.optString("value")
                            if (k.isNotBlank()) cEnv[k] = v
                        }
                    }
                }
                val cJvm = linkedMapOf<String, String>()
                val cJvmArr = obj.optJSONArray("jvmArgs") ?: obj.optJSONArray("jvmProperties")
                if (cJvmArr != null) {
                    for (i in 0 until cJvmArr.length()) {
                        val item = cJvmArr.opt(i)
                        if (item is JSONObject) {
                            val k = item.optString("name").ifBlank { item.optString("key") }
                            val v = item.optString("value")
                            if (k.isNotBlank()) cJvm[k] = v
                        } else if (item is String) {
                            val s = item.removePrefix("-D")
                            if ('=' in s) {
                                val p = s.split('=', limit = 2)
                                cJvm[p[0].trim()] = p[1].trim()
                            }
                        }
                    }
                }
                return RendererConfigOption(
                    id = cfgId.ifBlank { cfgName.lowercase().replace(' ', '_') },
                    displayName = cfgName.ifBlank { cfgId },
                    description = desc,
                    environment = cEnv,
                    jvmProperties = cJvm
                )
            }

            val configsArr = json.optJSONArray("configurations") ?: json.optJSONArray("presets")
            if (configsArr != null) {
                for (i in 0 until configsArr.length()) {
                    val opt = configsArr.optJSONObject(i)?.let { parseConfigOption(it) }
                    if (opt != null) parsedConfigs.add(opt)
                }
            }

            val parsedSelectables = mutableListOf<SelectableConfigItem>()
            val parsedToggleables = mutableListOf<ToggleableConfigItem>()

            val selectableArr = json.optJSONObject("env")?.optJSONArray("selectable")
                ?: json.optJSONArray("selectable")
            if (selectableArr != null) {
                for (i in 0 until selectableArr.length()) {
                    val selObj = selectableArr.optJSONObject(i) ?: continue
                    val key = selObj.optString("key").ifBlank { selObj.optString("name") }
                    val title = selObj.optString("title").ifBlank { key }
                    val itemsArr = selObj.optJSONArray("items")
                        ?: selObj.optJSONArray("values")
                        ?: selObj.optJSONObject("items")?.optJSONArray("values")
                    val options = mutableListOf<String>()
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val v = itemsArr.optString(j)
                            if (v.isNotBlank()) options.add(v)
                        }
                    }
                    val defaultVal = selObj.optString("defaultValue").ifBlank { options.firstOrNull().orEmpty() }
                    if (key.isNotBlank() && options.isNotEmpty()) {
                        parsedSelectables.add(
                            SelectableConfigItem(
                                key = key,
                                title = title,
                                options = options,
                                defaultValue = defaultVal
                            )
                        )
                    }
                }
            }

            val toggleableArr = json.optJSONObject("env")?.optJSONArray("toggleable")
                ?: json.optJSONArray("toggleable")
            if (toggleableArr != null) {
                for (i in 0 until toggleableArr.length()) {
                    val togObj = toggleableArr.optJSONObject(i) ?: continue
                    val key = togObj.optString("key").ifBlank { togObj.optString("name") }
                    val title = togObj.optString("title").ifBlank { key }
                    val value = togObj.optString("value").ifBlank { "1" }
                    val toggle = togObj.optBoolean("toggle", togObj.optBoolean("default", false))
                    val desc = togObj.optString("description").takeIf { it.isNotBlank() }
                    if (key.isNotBlank()) {
                        parsedToggleables.add(
                            ToggleableConfigItem(
                                key = key,
                                title = title,
                                value = value,
                                defaultEnabled = toggle,
                                description = desc
                            )
                        )
                    }
                }
            }

            val isMobileGl = rawId.contains("mobilegl", ignoreCase = true) ||
                rawName.contains("mobilegl", ignoreCase = true) ||
                rawName.contains("mobile gl", ignoreCase = true)

            val finalSpec = if (parsedSelectables.isNotEmpty() || parsedToggleables.isNotEmpty()) {
                RendererConfigSpec(selectables = parsedSelectables, toggleables = parsedToggleables)
            } else if (isMobileGl) {
                RendererConfigSpec.createMobileGlSpec()
            } else {
                RendererConfigSpec()
            }

            if (parsedConfigs.isEmpty() && isMobileGl) {
                parsedConfigs.addAll(RendererConfigOption.createMobileGlPresets())
            }

            return PluginConfigData(
                id = rawId,
                displayName = rawName,
                glLibFileName = glPath,
                eglLibFileName = eglPath,
                dlopenLibPaths = dlopenLibs,
                environment = envMap,
                jvmProperties = jvmProps,
                configSpec = finalSpec,
                configurations = parsedConfigs,
                minMcVer = (json.optString("minMcVer").ifBlank { json.optString("minMCVer") }).takeIf { it.isNotBlank() },
                maxMcVer = (json.optString("maxMcVer").ifBlank { json.optString("maxMCVer") }).takeIf { it.isNotBlank() }
            )
        }
    }
}
