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
    val sourcePackage: String? = null,
    val sourceFile: File? = null,
    val isInstalledApp: Boolean = false,
    val version: String = "1.0",
    val minMcVer: String? = null,
    val maxMcVer: String? = null
)

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

            return PluginConfigData(
                id = rawId,
                displayName = rawName,
                glLibFileName = glPath,
                eglLibFileName = eglPath,
                dlopenLibPaths = dlopenLibs,
                environment = envMap,
                jvmProperties = jvmProps,
                minMcVer = (json.optString("minMcVer").ifBlank { json.optString("minMCVer") }).takeIf { it.isNotBlank() },
                maxMcVer = (json.optString("maxMcVer").ifBlank { json.optString("maxMCVer") }).takeIf { it.isNotBlank() }
            )
        }
    }
}
