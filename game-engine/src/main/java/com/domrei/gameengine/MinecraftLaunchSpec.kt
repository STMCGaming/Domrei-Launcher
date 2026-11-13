package com.domrei.gameengine

import com.domrei.gameengine.renderer.PreparedRenderer
import com.domrei.gameengine.renderer.RendererType
import com.domrei.gameengine.runtime.InstalledJavaRuntime
import com.domrei.gameengine.runtime.JavaRuntimeVersion
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Player account & launch configuration parameters for Domrei Game Engine.
 */
data class GameLaunchRequest(
    val instanceName: String,
    val minecraftVersion: String,
    val loaderType: String = "Vanilla",
    val loaderVersion: String? = null,
    val minecraftRootDir: File,
    val instanceGameDir: File,
    val username: String = "DomreiPlayer",
    val uuid: String = generateOfflineUuid(username),
    val accessToken: String = "0",
    val minMemoryMb: Int = 512,
    val maxMemoryMb: Int = 2048,
    val windowWidth: Int = 1920,
    val windowHeight: Int = 1080,
    val preferredRuntime: JavaRuntimeVersion? = null,
    val preferredRenderer: RendererType? = null,
    val extraJvmArgs: List<String> = emptyList(),
    val extraGameArgs: List<String> = emptyList()
) {
    companion object {
        fun generateOfflineUuid(username: String): String {
            val bytes = "OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8)
            return UUID.nameUUIDFromBytes(bytes).toString()
        }
    }
}

/**
 * Complete, resolved launch specification built by DomreiGameEngine from
 * Mojang & Fabric metadata, OpenJDK runtime paths, and MobileGlues / Krypton Wrapper renderer configs.
 */
data class MinecraftLaunchSpec(
    val instanceName: String,
    val versionId: String,
    val minecraftVersion: String,
    val mainClass: String,
    val assetIndexId: String,
    val runtime: InstalledJavaRuntime,
    val renderer: PreparedRenderer,
    val gameDir: File,
    val assetsDir: File,
    val nativesDir: File,
    val classpathEntries: List<File>,
    val jvmArguments: List<String>,
    val gameArguments: List<String>,
    val environment: Map<String, String>
) {
    val classpathString: String
        get() = classpathEntries.joinToString(File.pathSeparator) { it.absolutePath }

    /**
     * Full command line starting with the OpenJDK `bin/java` executable.
     */
    val commandLine: List<String>
        get() = buildList {
            add(runtime.javaExecutable.absolutePath)
            addAll(jvmArguments)
            add(mainClass)
            addAll(gameArguments)
        }
}
