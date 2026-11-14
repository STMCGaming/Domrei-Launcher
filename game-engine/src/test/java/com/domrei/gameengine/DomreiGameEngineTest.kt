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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

    @Test
    fun `applyAndroidCompatibilityPatches patches PojavRendererInit in lwjgl merged modules jar`() {
        val tempRoot = Files.createTempDirectory("domrei_patch_test").toFile()
        try {
            val engine = DomreiGameEngine(
                JdkManager(File(tempRoot, "runtimes")),
                RendererManager(File(tempRoot, "renderers")),
                EngineLogManager(File(tempRoot, "logs"))
            )

            val assetBridgeJar = listOf(
                File("src/main/assets/domrei-glfw-bridge.jar"),
                File("game-engine/src/main/assets/domrei-glfw-bridge.jar")
            ).firstOrNull { it.isFile }
            assertNotNull("Expected bundled domrei-glfw-bridge.jar asset", assetBridgeJar)

            val bridgeJar = File(tempRoot, "00-domrei-glfw-bridge.jar")
            assetBridgeJar!!.copyTo(bridgeJar, overwrite = true)

            val expectedPojavBytes = ZipFile(bridgeJar).use { zip ->
                val entry = zip.getEntry("org/lwjgl/opengl/PojavRendererInit.class")
                assertNotNull("Expected PojavRendererInit override in bridge jar", entry)
                zip.getInputStream(entry).use { it.readBytes() }
            }

            val mergedModulesJar = File(tempRoot, "lwjgl-3.3.3-merged-modules.jar")
            val stalePojavBytes = byteArrayOf(0x01, 0x02, 0x03)
            val preservedBytes = byteArrayOf(0x10, 0x20, 0x30)
            ZipOutputStream(FileOutputStream(mergedModulesJar)).use { zos ->
                zos.putNextEntry(ZipEntry("org/lwjgl/opengl/PojavRendererInit.class"))
                zos.write(stalePojavBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("org/lwjgl/opengl/GLCapabilities.class"))
                zos.write(preservedBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zos.write(byteArrayOf(0x7F))
                zos.closeEntry()
            }

            engine.applyAndroidCompatibilityPatches(listOf(bridgeJar, mergedModulesJar))

            ZipFile(mergedModulesJar).use { zip ->
                val patchedEntry = zip.getEntry("org/lwjgl/opengl/PojavRendererInit.class")
                assertNotNull(patchedEntry)
                val actualBytes = zip.getInputStream(patchedEntry).use { it.readBytes() }
                assertArrayEquals(expectedPojavBytes, actualBytes)

                val keptEntry = zip.getEntry("org/lwjgl/opengl/GLCapabilities.class")
                assertNotNull(keptEntry)
                val actualKept = zip.getInputStream(keptEntry).use { it.readBytes() }
                assertArrayEquals(preservedBytes, actualKept)

                assertNotNull(zip.getEntry("org/lwjgl/opengl/GL31.class"))
                assertNotNull(zip.getEntry("org/lwjgl/opengl/GL31C.class"))
                assertNotNull(zip.getEntry("org/lwjgl/opengl/GL32.class"))
                assertNotNull(zip.getEntry("org/lwjgl/opengl/GL32C.class"))
                assertNull(zip.getEntry("META-INF/CERT.RSA"))
            }
            assertTrue(File(tempRoot, "lwjgl-3.3.3-merged-modules.jar.domrei_patch_v3").isFile)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `applyAndroidCompatibilityPatches skips legacy authlib 1 and patches modern authlib`() {
        val tempRoot = Files.createTempDirectory("domrei_authlib_patch_test").toFile()
        try {
            val engine = DomreiGameEngine(
                JdkManager(File(tempRoot, "runtimes")),
                RendererManager(File(tempRoot, "renderers"))
            )
            val bridgeJar = File(tempRoot, "00-domrei-glfw-bridge.jar")
            val expectedAuthlibBytes = byteArrayOf(0x0A, 0x0B, 0x0C)
            ZipOutputStream(FileOutputStream(bridgeJar)).use { zos ->
                zos.putNextEntry(ZipEntry("patches/authlib/com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class"))
                zos.write(expectedAuthlibBytes)
                zos.closeEntry()
            }

            val legacyAuthlibJar = File(tempRoot, "authlib-1.5.25.jar")
            val originalLegacyBytes = byteArrayOf(0x52, 0x00)
            ZipOutputStream(FileOutputStream(legacyAuthlibJar)).use { zos ->
                zos.putNextEntry(ZipEntry("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class"))
                zos.write(originalLegacyBytes)
                zos.closeEntry()
            }

            val modernAuthlibJar = File(tempRoot, "authlib-3.17.30.jar")
            val originalModernBytes = byteArrayOf(0x61, 0x00)
            ZipOutputStream(FileOutputStream(modernAuthlibJar)).use { zos ->
                zos.putNextEntry(ZipEntry("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class"))
                zos.write(originalModernBytes)
                zos.closeEntry()
            }

            engine.applyAndroidCompatibilityPatches(listOf(bridgeJar, legacyAuthlibJar, modernAuthlibJar))

            // Legacy Authlib 1.5.25 MUST NOT be modified or patched
            ZipFile(legacyAuthlibJar).use { zip ->
                val entry = zip.getEntry("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertArrayEquals(originalLegacyBytes, bytes)
            }
            assertFalse(File(tempRoot, "authlib-1.5.25.jar.domrei_patch_v3").exists())

            // Modern Authlib 3.x MUST be patched with the override
            ZipFile(modernAuthlibJar).use { zip ->
                val entry = zip.getEntry("com/mojang/authlib/yggdrasil/YggdrasilAuthenticationService.class")
                assertNotNull(entry)
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                assertArrayEquals(expectedAuthlibBytes, bytes)
            }
            assertTrue(File(tempRoot, "authlib-3.17.30.jar.domrei_patch_v3").exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `domrei-glfw-bridge jar bundles LWJGLX for legacy Minecraft 1 12 2 support`() {
        val bridgeStream = javaClass.classLoader?.getResourceAsStream("domrei-glfw-bridge.jar")
            ?: File("src/main/assets/domrei-glfw-bridge.jar").inputStream()
        val tempJar = File.createTempFile("bridge_test", ".jar")
        try {
            tempJar.outputStream().use { bridgeStream.copyTo(it) }
            ZipFile(tempJar).use { zip ->
                assertNotNull("org/lwjgl/opengl/Display.class should be bundled for LWJGL 2 support", zip.getEntry("org/lwjgl/opengl/Display.class"))
                assertNotNull("org/lwjgl/input/Keyboard.class should be bundled", zip.getEntry("org/lwjgl/input/Keyboard.class"))
                assertNotNull("org/lwjgl/input/Mouse.class should be bundled", zip.getEntry("org/lwjgl/input/Mouse.class"))
                assertNotNull("org/lwjgl/opengl/GL31.class should be present", zip.getEntry("org/lwjgl/opengl/GL31.class"))
                assertNotNull("org/lwjgl/opengl/GL32.class should be present", zip.getEntry("org/lwjgl/opengl/GL32.class"))
            }
        } finally {
            tempJar.delete()
        }
    }

    @Test
    fun `JdkManager invalidates JRE 8 if rt jar is missing and packed`() {
        val tempRoot = Files.createTempDirectory("domrei_jre8_test").toFile()
        try {
            val manager = JdkManager(externalRootDir = tempRoot, internalExecRootDir = tempRoot)
            val jre8Dir = manager.getRuntimeStorageDir(JavaRuntimeVersion.JRE_8).apply { mkdirs() }
            File(jre8Dir, "bin/java").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
                setExecutable(true, false)
            }
            File(jre8Dir, "lib/aarch64/server/libjvm.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4, 5, 6))
            }
            File(jre8Dir, ".domrei_runtime_version").writeText("2")

            // Simulate broken / packed state without rt.jar
            File(jre8Dir, "lib/rt.jar.pack").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(7, 8, 9))
            }

            // Since rt.jar is missing and pack cannot be unpacked without unpacker in mock test, should return null
            val installed = manager.getInstalledRuntime(JavaRuntimeVersion.JRE_8, RuntimeAbi.ARM64)
            assertNull(installed)

            // Once rt.jar is restored, runtime becomes valid
            File(jre8Dir, "lib/rt.jar").writeBytes(byteArrayOf(10, 11, 12))
            val validInstalled = manager.getInstalledRuntime(JavaRuntimeVersion.JRE_8, RuntimeAbi.ARM64)
            assertNotNull(validInstalled)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
