package com.domrei.gameengine

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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class DomreiGameEngineTest {

    @Test
    fun `selectRuntimeForMinecraft maps Minecraft versions to JRE 8, 17, 21, and 25`() {
        assertEquals(JavaRuntimeVersion.JRE_8, JdkManager.selectRuntimeForMinecraft("b1.7.3"))
        assertEquals(JavaRuntimeVersion.JRE_8, JdkManager.selectRuntimeForMinecraft("1.8.9"))
        assertEquals(JavaRuntimeVersion.JRE_8, JdkManager.selectRuntimeForMinecraft("1.16.5"))

        assertEquals(JavaRuntimeVersion.JRE_17, JdkManager.selectRuntimeForMinecraft("1.17.1"))
        assertEquals(JavaRuntimeVersion.JRE_17, JdkManager.selectRuntimeForMinecraft("1.18.2"))
        assertEquals(JavaRuntimeVersion.JRE_17, JdkManager.selectRuntimeForMinecraft("1.20.4"))

        assertEquals(JavaRuntimeVersion.JRE_21, JdkManager.selectRuntimeForMinecraft("1.20.5"))
        assertEquals(JavaRuntimeVersion.JRE_21, JdkManager.selectRuntimeForMinecraft("1.21.4"))
        assertEquals(JavaRuntimeVersion.JRE_21, JdkManager.selectRuntimeForMinecraft("24w33a"))

        assertEquals(JavaRuntimeVersion.JRE_25, JdkManager.selectRuntimeForMinecraft("26.1"))
        assertEquals(JavaRuntimeVersion.JRE_25, JdkManager.selectRuntimeForMinecraft("26w14a"))
        assertEquals(JavaRuntimeVersion.JRE_25, JdkManager.selectRuntimeForMinecraft("1.21.4", requiredJavaMajor = 25))
    }

    @Test
    fun `JdkManager builds valid Domrei-Runtimes archive URLs and extracts tar xz`() {
        val tempRoot = Files.createTempDirectory("domrei_jdk_test").toFile()
        try {
            val manager = JdkManager(externalRootDir = tempRoot, internalExecRootDir = tempRoot)
            assertEquals(
                "https://raw.githubusercontent.com/STMCGaming/Domrei-Runtimes/main/runtimes/jre-21/universal.tar.xz",
                manager.buildUniversalArchiveUrl(JavaRuntimeVersion.JRE_21)
            )
            assertEquals(
                "https://raw.githubusercontent.com/STMCGaming/Domrei-Runtimes/main/runtimes/jre-21/bin-arm64.tar.xz",
                manager.buildBinArchiveUrl(JavaRuntimeVersion.JRE_21, RuntimeAbi.ARM64)
            )

            // Create a synthetic .tar.xz archive containing bin/java and lib/server/libjvm.so
            val sampleArchive = File(tempRoot, "sample.tar.xz")
            FileOutputStream(sampleArchive).use { fos ->
                XZOutputStream(fos, LZMA2Options()).use { xzos ->
                    TarArchiveOutputStream(xzos).use { tarOut ->
                        val javaBytes = "#!/bin/sh\necho DomreiOpenJDK".toByteArray()
                        val javaEntry = TarArchiveEntry("bin/java").apply {
                            size = javaBytes.size.toLong()
                            mode = 0b111_101_101 // rwxr-xr-x
                        }
                        tarOut.putArchiveEntry(javaEntry)
                        tarOut.write(javaBytes)
                        tarOut.closeArchiveEntry()

                        val jvmBytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
                        val jvmEntry = TarArchiveEntry("lib/server/libjvm.so").apply {
                            size = jvmBytes.size.toLong()
                            mode = 0b111_101_101
                        }
                        tarOut.putArchiveEntry(jvmEntry)
                        tarOut.write(jvmBytes)
                        tarOut.closeArchiveEntry()
                    }
                }
            }

            val extractDir = manager.getRuntimeStorageDir(JavaRuntimeVersion.JRE_21)
            manager.extractTarXzArchive(sampleArchive, extractDir)
            File(extractDir, ".domrei_runtime_version").writeText("2")

            val installed = manager.getInstalledRuntime(JavaRuntimeVersion.JRE_21, RuntimeAbi.ARM64)
            assertNotNull(installed)
            assertTrue(installed!!.javaExecutable.isFile)
            assertTrue(installed.javaExecutable.canExecute())
            assertNotNull(installed.libJvmSo)
            assertTrue(installed.libJvmSo!!.isFile)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `RendererManager configures MobileGlues and Krypton Wrapper environments`() {
        val tempRoot = Files.createTempDirectory("domrei_renderer_test").toFile()
        try {
            val rendererManager = RendererManager(externalRenderersDir = tempRoot, internalExecRenderersDir = tempRoot)

            val mgLib = File(tempRoot, "opengles3_mg/arm64-v8a/libmobileglues.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val mgConfig = MobileGluesConfig(enableANGLE = 1, enableNoError = 1, maxGlslCacheSize = 128)
            val mgConfigFile = File(tempRoot, "mobileglues/mg_config.json").apply {
                parentFile?.mkdirs()
                writeText(mgConfig.toJsonString())
            }
            val mgEnv = rendererManager.buildRendererEnvironment(
                type = RendererType.MOBILE_GLUES,
                nativeLibFile = mgLib,
                configFile = mgConfigFile,
                mobileGluesConfig = mgConfig
            )
            assertEquals("opengles3_mg", mgEnv["POJAV_RENDERER"])
            assertEquals("libmobileglues.so", mgEnv["LIBGL_EGL"])
            assertEquals("3", mgEnv["LIBGL_ES"])
            assertEquals(mgConfigFile.parentFile?.absolutePath, mgEnv["MG_DIR_PATH"])

            val kryptonLib = File(tempRoot, "opengles3_krypton/arm64-v8a/libng_gl4es.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(5, 6, 7, 8))
            }
            val kryptonEnv = rendererManager.buildRendererEnvironment(
                type = RendererType.KRYPTON_WRAPPER,
                nativeLibFile = kryptonLib,
                kryptonConfig = KryptonWrapperConfig()
            )
            assertEquals("opengles3", kryptonEnv["POJAV_RENDERER"])
            assertEquals("31", kryptonEnv["LIBGL_GL"])
            assertEquals("3", kryptonEnv["LIBGL_ES"])
            assertEquals("1", kryptonEnv["LIBGL_USE_MC_COLOR"])
            assertEquals("1", kryptonEnv["LIBGL_NORMALIZE"])
            assertEquals("1", kryptonEnv["LIBGL_NOERROR"])
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `DomreiGameEngine builds full launch spec and deduplicates Fabric vs Vanilla libraries`() {
        val tempRoot = Files.createTempDirectory("domrei_engine_test").toFile()
        try {
            val jdkManager = JdkManager(File(tempRoot, "runtimes"))
            val rendererManager = RendererManager(File(tempRoot, "renderers"))
            val logManager = EngineLogManager(File(tempRoot, "logs"))
            val engine = DomreiGameEngine(jdkManager, rendererManager, logManager)

            val latestLog = logManager.startNewSession("Fabric 1.21.4")
            logManager.appendLogLine("[DomreiEngine] Test log entry")
            assertTrue(latestLog.isFile)
            assertTrue(latestLog.readText().contains("[DomreiEngine] Test log entry"))
            assertEquals(2, logManager.listLogFiles().size)

            val fakeJava = File(tempRoot, "runtimes/jre-21/bin/java").apply {
                parentFile?.mkdirs()
                writeText("#!/bin/sh")
                setExecutable(true)
            }
            val fakeLibDir = File(tempRoot, "runtimes/jre-21/lib/server").apply { mkdirs() }
            val fakeJvm = File(fakeLibDir, "libjvm.so").apply { writeBytes(byteArrayOf(1)) }

            val runtime = InstalledJavaRuntime(
                version = JavaRuntimeVersion.JRE_21,
                abi = RuntimeAbi.ARM64,
                homeDir = File(tempRoot, "runtimes/jre-21"),
                execHomeDir = File(tempRoot, "runtimes/jre-21"),
                javaExecutable = fakeJava,
                libJliSo = null,
                libJvmSo = fakeJvm,
                libraryDirs = listOf(fakeLibDir),
                releaseVersionTag = "2"
            )

            val fakeMgSo = File(tempRoot, "renderers/opengles3_mg/arm64-v8a/libmobileglues.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val renderer = PreparedRenderer(
                type = RendererType.MOBILE_GLUES,
                nativeLibFile = fakeMgSo,
                nativeLibDir = fakeMgSo.parentFile!!,
                configFile = null,
                environmentVars = rendererManager.buildRendererEnvironment(RendererType.MOBILE_GLUES, fakeMgSo),
                jvmSystemProperties = rendererManager.buildRendererJvmProperties(RendererType.MOBILE_GLUES, fakeMgSo)
            )

            val baseJson = JSONObject(
                """
                {
                  "id": "1.21.4",
                  "mainClass": "net.minecraft.client.main.Main",
                  "assetIndex": { "id": "19" },
                  "javaVersion": { "majorVersion": 21 },
                  "libraries": [
                    { "name": "org.ow2.asm:asm:9.3" },
                    { "name": "com.mojang:brigadier:1.2.9" }
                  ],
                  "arguments": {
                    "game": ["--username", "${'$'}{auth_player_name}", "--version", "${'$'}{version_name}"],
                    "jvm": ["-XstartOnFirstThread", "-Djava.library.path=${'$'}{natives_directory}"]
                  }
                }
                """.trimIndent()
            )

            val fabricJson = JSONObject(
                """
                {
                  "id": "fabric-loader-0.16.10-1.21.4",
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "libraries": [
                    { "name": "org.ow2.asm:asm:9.7.1" },
                    { "name": "net.fabricmc:fabric-loader:0.16.10" }
                  ],
                  "arguments": {
                    "game": [],
                    "jvm": ["-DFabricMcEmu=net.minecraft.client.main.Main"]
                  }
                }
                """.trimIndent()
            )

            val request = GameLaunchRequest(
                instanceName = "Fabric 1.21.4",
                minecraftVersion = "1.21.4",
                loaderType = "Fabric",
                loaderVersion = "0.16.10",
                minecraftRootDir = File(tempRoot, "minecraft"),
                instanceGameDir = File(tempRoot, "minecraft/instances/inst_1/gamedir"),
                username = "DomreiTester"
            )

            val spec = engine.buildLaunchSpecFromJson(
                request = request,
                baseJson = baseJson,
                fabricJson = fabricJson,
                runtime = runtime,
                renderer = renderer
            )

            assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", spec.mainClass)
            assertEquals("19", spec.assetIndexId)
            assertEquals("opengles3_mg", spec.environment["POJAV_RENDERER"])
            assertFalse(spec.jvmArguments.contains("-XstartOnFirstThread"))
            assertTrue(spec.jvmArguments.contains("-Dorg.lwjgl.opengl.libname=libmobileglues.so"))
            assertTrue(spec.gameArguments.contains("DomreiTester"))

            // Verify ASM 9.7.1 from Fabric replaced ASM 9.3 from Vanilla
            val cpPaths = spec.classpathEntries.map { it.path }
            assertTrue(cpPaths.any { it.endsWith("org/ow2/asm/asm/9.7.1/asm-9.7.1.jar") })
            assertFalse(cpPaths.any { it.endsWith("org/ow2/asm/asm/9.3/asm-9.3.jar") })

            // Verify DomreiJvmService spec serialization round-trip & read-only DCL permissions
            val serialized = DomreiJvmService.serializeLaunchSpec(spec)
            val roundTrip = DomreiJvmService.deserializeLaunchSpec(serialized)
            assertEquals(spec.mainClass, roundTrip.mainClass)
            assertEquals(spec.commandLine, roundTrip.commandLine)
            assertEquals(spec.environment, roundTrip.environment)

            DomreiJliRunner.enforceReadOnlyExecutablePermissions(fakeMgSo.parentFile!!)
            assertFalse(fakeMgSo.canWrite())
            DomreiJliRunner.restoreWritablePermissions(fakeMgSo.parentFile!!)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
