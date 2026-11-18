package com.domrei.gameengine

import com.domrei.gameengine.renderer.PluginConfigData
import com.domrei.gameengine.renderer.RendererConfigOption
import com.domrei.gameengine.renderer.RendererConfigSpec
import com.domrei.gameengine.renderer.RendererConfigState
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
        assertEquals(RendererType.KRYPTON_WRAPPER.displayName, manager.getSelectedRendererDisplayName())
    }

    @Test
    fun `RendererManager prepares installed app plugin without requiring permissions`() = runBlocking {
        val tempRoot = Files.createTempDirectory("installed_app_plugin_test").toFile()
        val externalDir = File(tempRoot, "renderers").apply { mkdirs() }
        val manager = RendererManager(
            externalRenderersDir = externalDir,
            appContext = null
        )

        val appNativeDir = File(tempRoot, "app_native_libs").apply { mkdirs() }
        val fakeGl = File(appNativeDir, "libGL.so").apply { writeText("gl binary") }
        val fakeEgl = File(appNativeDir, "libEGL.so").apply { writeText("egl binary") }

        val installedAppPlugin = RendererPlugin(
            id = "net.fcl.plugin.turnip",
            displayName = "Turnip Adreno Vulkan",
            glLibFileName = "libGL.so",
            eglLibFileName = "libEGL.so",
            nativeLibDir = appNativeDir,
            primaryLibFile = fakeGl,
            environment = mapOf("MESA_LOADER_DRIVER_OVERRIDE" to "zink", "TU_DEBUG" to "noconform"),
            jvmProperties = mapOf("turnip.present" to "true"),
            sourcePackage = "net.fcl.plugin.turnip",
            isInstalledApp = true
        )

        val desc = RendererDescriptor.fromPlugin(installedAppPlugin)
        manager.setSelectedRenderer(desc)

        val prepared = manager.prepareRenderer(
            descriptor = desc,
            abi = RuntimeAbi.ARM64
        ).getOrThrow()

        assertNotNull(prepared)
        assertEquals(RendererType.CUSTOM_PLUGIN, prepared.type)
        assertEquals("Turnip Adreno Vulkan", prepared.customDisplayName)
        assertEquals("zink", prepared.environmentVars["MESA_LOADER_DRIVER_OVERRIDE"])
        assertEquals("noconform", prepared.environmentVars["TU_DEBUG"])
        assertEquals("true", prepared.jvmSystemProperties["turnip.present"])
        assertTrue(prepared.nativeLibDir.exists())
        assertTrue(File(prepared.nativeLibDir, "libGL.so").exists())
    }

    @Test
    fun `RendererManager provides and applies custom plugin configurations`() = runBlocking {
        val tempRoot = Files.createTempDirectory("plugin_config_test").toFile()
        val externalDir = File(tempRoot, "renderers").apply { mkdirs() }
        val manager = RendererManager(
            externalRenderersDir = externalDir,
            appContext = null
        )

        // Built-in renderers (Krypton and MobileGlues) have no extra renderer configurations
        val kryptonDesc = RendererDescriptor.fromBuiltIn(RendererType.KRYPTON_WRAPPER)
        assertTrue(manager.getAvailableRendererConfigs(kryptonDesc).isEmpty())

        val mgDesc = RendererDescriptor.fromBuiltIn(RendererType.MOBILE_GLUES)
        assertTrue(manager.getAvailableRendererConfigs(mgDesc).isEmpty())

        // Test plugin with custom configurations
        val customConfigs = listOf(
            RendererConfigOption(id = "fast", displayName = "Fast Mode", environment = mapOf("FAST_GL" to "1")),
            RendererConfigOption(id = "fancy", displayName = "Fancy Mode", environment = mapOf("FANCY_GL" to "1"))
        )
        val dummyLibDir = File(tempRoot, "plugin_libs").apply { mkdirs() }
        val dummyGl = File(dummyLibDir, "libGL.so").apply { writeText("gl") }
        val plugin = RendererPlugin(
            id = "custom_renderer",
            displayName = "Custom Renderer",
            nativeLibDir = dummyLibDir,
            primaryLibFile = dummyGl,
            configurations = customConfigs
        )
        val pluginDesc = RendererDescriptor.fromPlugin(plugin)
        val pluginConfigs = manager.getAvailableRendererConfigs(pluginDesc)
        assertEquals(2, pluginConfigs.size)
        assertEquals("fast", pluginConfigs[0].id)
        assertEquals("fancy", pluginConfigs[1].id)

        manager.setSelectedRendererConfig(pluginDesc, customConfigs[1])
        val pluginSelected = manager.getSelectedRendererConfig(pluginDesc)
        assertEquals("fancy", pluginSelected.id)

        val preparedPlugin = manager.preparePluginRenderer(plugin, pluginSelected, RuntimeAbi.ARM64).getOrThrow()
        assertEquals("1", preparedPlugin.environmentVars["FANCY_GL"])
    }

    @Test
    fun `RendererConfigSpec is empty for krypton and mobileglues, active only for plugins declaring spec`() = runBlocking {
        val tempRoot = Files.createTempDirectory("spec_state_test").toFile()
        val externalDir = File(tempRoot, "renderers").apply { mkdirs() }
        val manager = RendererManager(
            externalRenderersDir = externalDir,
            appContext = null
        )

        // Verify available renderers only contain Krypton Wrapper as built-in (no built-in MobileGlues)
        val available = manager.getAvailableRenderers()
        assertEquals(1, available.size)
        assertEquals(RendererType.KRYPTON_WRAPPER.id, available.first().id)

        // Krypton Wrapper has NO renderer configuration spec
        val kryptonDesc = RendererDescriptor.fromBuiltIn(RendererType.KRYPTON_WRAPPER)
        val kryptonSpec = manager.getRendererConfigSpec(kryptonDesc)
        assertTrue(kryptonSpec.isEmpty)
        assertFalse(kryptonSpec.isNotEmpty)

        // MobileGL plugin has renderer configuration spec (backend selection + switches)
        val mgDesc = RendererDescriptor(id = "plugin:mobilegl", displayName = "MobileGL", isPlugin = true)
        val mgSpec = manager.getRendererConfigSpec(mgDesc)
        assertTrue(mgSpec.isNotEmpty)
        assertFalse(mgSpec.isEmpty)
        assertTrue(mgSpec.selectables.any { it.key == "MOBILEGL_BACKEND_TYPE" })
        assertTrue(mgSpec.toggleables.any { it.key == "MOBILEGL_USE_ANGLE" })

        // Plugin with explicit config spec (switches and dropdown selection)
        val dummyLibDir = File(tempRoot, "plugin_libs").apply { mkdirs() }
        val dummyGl = File(dummyLibDir, "libGL.so").apply { writeText("gl") }
        val pluginWithSpec = RendererPlugin(
            id = "mobilegl_app_plugin",
            displayName = "MobileGL Plugin",
            nativeLibDir = dummyLibDir,
            primaryLibFile = dummyGl,
            configSpec = RendererConfigSpec.createMobileGlSpec()
        )
        val pluginDesc = RendererDescriptor.fromPlugin(pluginWithSpec)
        val pluginSpec = manager.getRendererConfigSpec(pluginDesc)
        assertTrue(pluginSpec.isNotEmpty)
        assertTrue(pluginSpec.selectables.any { it.key == "MOBILEGL_BACKEND_TYPE" })
        assertTrue(pluginSpec.toggleables.any { it.key == "MOBILEGL_USE_ANGLE" })

        // Check initial default state for this plugin
        val initialState = manager.getRendererConfigState(pluginDesc)
        assertEquals("DirectGLES", initialState.selectedValues["MOBILEGL_BACKEND_TYPE"])
        assertEquals(false, initialState.toggledKeys["MOBILEGL_USE_ANGLE"])

        // Modify state (switch ANGLE on and change backend to DirectVulkan)
        val modifiedState = initialState.copy(
            selectedValues = mapOf("MOBILEGL_BACKEND_TYPE" to "DirectVulkan"),
            toggledKeys = initialState.toggledKeys + ("MOBILEGL_USE_ANGLE" to true)
        )
        manager.saveRendererConfigState(pluginDesc, modifiedState)

        val retrievedState = manager.getRendererConfigState(pluginDesc)
        assertEquals("DirectVulkan", retrievedState.selectedValues["MOBILEGL_BACKEND_TYPE"])
        assertEquals(true, retrievedState.toggledKeys["MOBILEGL_USE_ANGLE"])

        // Effective environment includes both
        val env = manager.getEffectiveEnvironment(pluginDesc, retrievedState)
        assertEquals("DirectVulkan", env["MOBILEGL_BACKEND_TYPE"])
        assertEquals("1", env["MOBILEGL_USE_ANGLE"])
        assertEquals("1", env["MG_ENABLE_ANGLE"])

        // Generic plugin without spec has empty spec (dropdown should not show)
        val plainPlugin = RendererPlugin(
            id = "plain_virgl",
            displayName = "VirGL",
            nativeLibDir = externalDir,
            primaryLibFile = File(externalDir, "libGL.so")
        )
        val plainDesc = RendererDescriptor.fromPlugin(plainPlugin)
        val plainSpec = manager.getRendererConfigSpec(plainDesc)
        assertTrue(plainSpec.isEmpty)
        assertFalse(plainSpec.isNotEmpty)
    }

    @Test
    fun `PluginConfigData correctly parses ZalithLauncher2 buildEnvs array with selectables and toggleables`() {
        val zalithJson = """
            {
                "displayName": "Zalith Custom Renderer",
                "rendererId": "zalith_custom",
                "rendererGLPath": "**|libGL.so",
                "rendererEGLPath": "**|libEGL.so",
                "env": [
                    {
                        "key": "LIB_MESA_NAME",
                        "value": "**|libMesa.so"
                    },
                    {
                        "key": "GL_VERSION",
                        "items": ["3.0", "3.1", "3.2"],
                        "title": "title_gl_version",
                        "defaultValue": "3.1"
                    },
                    {
                        "key": "ENABLE_FAST_MATH",
                        "value": "1",
                        "toggle": true,
                        "title": { "resName": "title_enable_fast_math" },
                        "description": "Enables aggressive math optimizations"
                    }
                ]
            }
        """.trimIndent()

        val parsed = PluginConfigData.parse(zalithJson, fallbackId = "zalith_custom", fallbackName = "Zalith Custom")
        assertEquals("zalith_custom", parsed.id)
        assertEquals("Zalith Custom Renderer", parsed.displayName)
        assertEquals("libGL.so", parsed.glLibFileName)
        assertEquals("libEGL.so", parsed.eglLibFileName)
        assertEquals("**|libMesa.so", parsed.environment["LIB_MESA_NAME"])

        assertTrue(parsed.configSpec.isNotEmpty)
        assertEquals(1, parsed.configSpec.selectables.size)
        val sel = parsed.configSpec.selectables[0]
        assertEquals("GL_VERSION", sel.key)
        assertEquals("Gl Version", sel.title)
        assertEquals(listOf("3.0", "3.1", "3.2"), sel.options)
        assertEquals("3.1", sel.defaultValue)

        assertEquals(1, parsed.configSpec.toggleables.size)
        val tog = parsed.configSpec.toggleables[0]
        assertEquals("ENABLE_FAST_MATH", tog.key)
        assertEquals("Enable Fast Math", tog.title)
        assertEquals("1", tog.value)
        assertEquals(true, tog.defaultEnabled)
        assertEquals("Enables aggressive math optimizations", tog.description)
    }
}
