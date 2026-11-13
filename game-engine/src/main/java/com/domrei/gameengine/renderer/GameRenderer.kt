package com.domrei.gameengine.renderer

import org.json.JSONObject
import java.io.File

/**
 * Supported OpenGL-on-GLES/Vulkan renderers for Domrei Game Engine.
 */
enum class RendererType(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val glVersionLabel: String,
    val glslVersionLabel: String,
    val nativeLibFileName: String,
    val eglLibFileName: String,
    val aarFileName: String
) {
    MOBILE_GLUES(
        id = "opengles3_mg",
        displayName = "MobileGlues",
        subtitle = "OpenGL 4.x / 3.2 over GLES 3.2 & ANGLE (Best for 1.17+ & Shaders)",
        glVersionLabel = "OpenGL 4.0 / 3.2",
        glslVersionLabel = "GLSL 400 / 320 es",
        nativeLibFileName = "libmobileglues.so",
        eglLibFileName = "libmobileglues.so",
        aarFileName = "MobileGlues-release.aar"
    ),
    KRYPTON_WRAPPER(
        id = "opengles3_krypton",
        displayName = "Krypton Wrapper",
        subtitle = "NG-GL4ES OpenGL 3.1 wrapper over GLES 3.0 (High compatibility)",
        glVersionLabel = "OpenGL 3.1",
        glslVersionLabel = "GLSL 140 / 300 es",
        nativeLibFileName = "libng_gl4es.so",
        eglLibFileName = "libEGL.so",
        aarFileName = "NG-GL4ES-release.aar"
    );

    companion object {
        fun fromId(id: String?): RendererType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOBILE_GLUES
        }
    }
}

/**
 * Configuration model for MobileGlues (`mg_config.json`).
 */
data class MobileGluesConfig(
    val enableANGLE: Int = 0,
    val enableNoError: Int = 1,
    val enableExtGL43: Int = 0,
    val enableExtComputeShader: Int = 0,
    val enableExtTimerQuery: Int = 1,
    val enableExtDirectStateAccess: Int = 1,
    val maxGlslCacheSize: Int = 64,
    val multidrawMode: Int = 5,
    val angleDepthClearFixMode: Int = 0,
    val customGlVersion: Int = 40,
    val fsr1Setting: Int = 0
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("enableANGLE", enableANGLE)
        json.put("enableNoError", enableNoError)
        json.put("enableExtGL43", enableExtGL43)
        json.put("enableExtComputeShader", enableExtComputeShader)
        json.put("enableExtTimerQuery", enableExtTimerQuery)
        json.put("enableExtDirectStateAccess", enableExtDirectStateAccess)
        json.put("maxGlslCacheSize", maxGlslCacheSize)
        json.put("multidrawMode", multidrawMode)
        json.put("angleDepthClearFixMode", angleDepthClearFixMode)
        json.put("customGlVersion", customGlVersion)
        json.put("fsr1Setting", fsr1Setting)
        return json.toString(2)
    }
}

/**
 * Configuration model for Krypton Wrapper (NG-GL4ES).
 */
data class KryptonWrapperConfig(
    val glVersion: Int = 31,
    val glesVersion: Int = 3,
    val useMcColor: Boolean = true,
    val normalizeNormals: Boolean = true,
    val noError: Boolean = true,
    val mipmapMode: Int = 3,
    val vboEnable: Boolean = true
)

/**
 * Prepared renderer artifact & runtime environment ready to be injected into a game launch.
 */
data class PreparedRenderer(
    val type: RendererType,
    val nativeLibFile: File,
    val nativeLibDir: File,
    val configFile: File?,
    val environmentVars: Map<String, String>,
    val jvmSystemProperties: Map<String, String>
)
