package com.domrei.gameengine

import com.domrei.gameengine.renderer.PluginConfigData
import com.domrei.gameengine.renderer.RendererDescriptor
import com.domrei.gameengine.renderer.RendererManager
import com.domrei.gameengine.renderer.RendererPlugin
import com.domrei.gameengine.renderer.RendererType
import com.domrei.gameengine.runtime.RuntimeAbi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RendererPluginTest {

    @Test
    fun `PluginConfigData parses ZalithLauncher2 fclPlugin_V2 JSON correctly`() {
        val fclJson = """
            {
                "rendererId": "virgl_custom",
                "displayName": "VirGL Renderer 2.0",
                "rendererGLPath": "libGL_virgl.so",
                "rendererEGLPath": "libEGL_virgl.so",
                "dlopenLibPaths": ["libvirglrenderer.so"],
                "env": {
                    "normal": [
                        {"key": "VIRGL_DEBUG", "value": "all"},
                        {"key": "GALLIUM_DRIVER", "value": "virpipe"}
                    ]
                },
                "jvmArgs": [
                    "-Dvirgl.debug=true",
                    "-Dvirgl.threads=4"
                ],
                "minMcVer": "1.12.2",
                "maxMcVer": "1.20.4"
            }
        """.trimIndent()

        val parsed = PluginConfigData.parse(fclJson, fallbackId = "fallback", fallbackName = "Fallback")
        assertEquals("virgl_custom", parsed.id)
        assertEquals("VirGL Renderer 2.0", parsed.displayName)
        assertEquals("libGL_virgl.so", parsed.glLibFileName)
        assertEquals("libEGL_virgl.so", parsed.eglLibFileName)
        assertEquals(listOf("libvirglrenderer.so"), parsed.dlopenLibPaths)
        assertEquals("all", parsed.environment["VIRGL_DEBUG"])
        assertEquals("virpipe", parsed.environment["GALLIUM_DRIVER"])
        assertEquals("true", parsed.jvmProperties["virgl.debug"])
        assertEquals("4", parsed.jvmProperties["virgl.threads"])
        assertEquals("1.12.2", parsed.minMcVer)
        assertEquals("1.20.4", parsed.maxMcVer)
    }

    @Test
    fun `RendererDescriptor correctly handles built-in and plugin renderers`() {
        val builtInDesc = RendererDescriptor.fromBuiltIn(RendererType.MOBILE_GLUES)
        assertFalse(builtInDesc.isPlugin)
        assertEquals(RendererType.MOBILE_GLUES.id, builtInDesc.id)
        assertEquals(RendererType.MOBILE_GLUES.displayName, builtInDesc.displayName)
        assertEquals(RendererType.MOBILE_GLUES, builtInDesc.effectiveType)

        val tempDir = Files.createTempDirectory("plugin_test").toFile()
        val dummyLib = File(tempDir, "libGL.so").apply { writeText("fake so") }
        val plugin = RendererPlugin(
            id = "zink_plugin",
            displayName = "Zink Vulkan Plugin",
            glLibFileName = "libGL.so",
            eglLibFileName = "libEGL.so",
            nativeLibDir = tempDir,
            primaryLibFile = dummyLib,
            environment = mapOf("MESA_LOADER_DRIVER_OVERRIDE" to "zink"),
            jvmProperties = mapOf("domrei.zink" to "1")
        )

        val pluginDesc = RendererDescriptor.fromPlugin(plugin)
        assertTrue(pluginDesc.isPlugin)
        assertEquals("plugin:zink_plugin", pluginDesc.id)
        assertEquals("Zink Vulkan Plugin", pluginDesc.displayName)
        assertEquals(RendererType.CUSTOM_PLUGIN, pluginDesc.effectiveType)
        assertEquals(plugin, pluginDesc.pluginInfo)
    }

    @Test
    fun `RendererManager imports, selects, prepares, and deletes plugin archives`() = runBlocking {
        val tempRoot = Files.createTempDirectory("renderer_mgr_test").toFile()
        val externalDir = File(tempRoot, "renderers").apply { mkdirs() }
        val manager = RendererManager(
            externalRenderersDir = externalDir,
            appContext = null
        )

        // Create a dummy plugin zip with lib/arm64-v8a/libGL.so and plugin.json
        val zipFile = File(tempRoot, "virgl_plugin.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { out ->
            out.putNextEntry(ZipEntry("lib/arm64-v8a/libGL.so"))
            out.write("binary data".toByteArray())
            out.closeEntry()

            out.putNextEntry(ZipEntry("lib/arm64-v8a/libEGL.so"))
            out.write("binary data 2".toByteArray())
            out.closeEntry()

            out.putNextEntry(ZipEntry("plugin.json"))
            val config = """
                {
                    "rendererId": "virgl",
                    "displayName": "VirGL Fast",
                    "rendererGLPath": "libGL.so",
                    "rendererEGLPath": "libEGL.so",
                    "env": {
                        "GALLIUM_DRIVER": "virpipe"
                    }
                }
            """.trimIndent()
            out.write(config.toByteArray())
            out.closeEntry()
        }

        val importResult = manager.importPlugin(zipFile, originalName = "virgl_plugin.zip")
        assertTrue(importResult.isSuccess)
        val imported = importResult.getOrThrow()
        assertEquals("virgl", imported.id)
        assertEquals("VirGL Fast", imported.displayName)
        assertEquals("libGL.so", imported.glLibFileName)
        assertEquals("virpipe", imported.environment["GALLIUM_DRIVER"])

        val available = manager.getAvailableRenderers()
        assertTrue(available.any { it.id == "plugin:virgl" })

        val pluginDesc = available.first { it.id == "plugin:virgl" }
        manager.setSelectedRenderer(pluginDesc)

        assertEquals("virgl", manager.getSelectedRendererDescriptor().pluginInfo?.id)
        assertEquals("VirGL Fast", manager.getSelectedRendererDisplayName())

        // Prepare the plugin renderer
        val prepared = manager.prepareRenderer(
            descriptor = pluginDesc,
            abi = RuntimeAbi.ARM64
        ).getOrThrow()
        assertNotNull(prepared)
        assertEquals(RendererType.CUSTOM_PLUGIN, prepared.type)
        assertEquals("VirGL Fast", prepared.customDisplayName)
        assertEquals("virpipe", prepared.environmentVars["GALLIUM_DRIVER"])
        assertEquals("virgl", prepared.environmentVars["DOMREI_RENDERER"])

        // Delete the plugin
        val deleted = manager.deletePlugin("virgl")
        assertTrue(deleted)
        val afterDelete = manager.getAvailableRenderers()
        assertFalse(afterDelete.any { it.id == "plugin:virgl" })
        assertEquals(RendererType.MOBILE_GLUES.displayName, manager.getSelectedRendererDisplayName())
    }
}
