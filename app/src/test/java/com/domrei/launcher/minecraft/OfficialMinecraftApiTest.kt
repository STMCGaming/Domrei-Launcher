package com.domrei.launcher.minecraft

import com.domrei.launcher.InstanceLoaderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfficialMinecraftApiTest {

    @Test
    fun testFetchOfficialVersionManifestAndFilters() = runBlocking {
        val result = OfficialMinecraftApi.fetchVersionManifest(forceRefresh = true)
        assertTrue("Manifest fetch should succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val manifest = result.getOrThrow()
        assertTrue(manifest.latestRelease.isNotEmpty())
        assertTrue(manifest.latestSnapshot.isNotEmpty())
        assertTrue(manifest.versions.isNotEmpty())

        val releases = manifest.versions.filter { it.matchesFilter(MinecraftVersionFilter.RELEASE) }
        val snapshots = manifest.versions.filter { it.matchesFilter(MinecraftVersionFilter.SNAPSHOT) }
        val oldVersions = manifest.versions.filter { it.matchesFilter(MinecraftVersionFilter.OLD) }

        assertTrue("Expected official releases", releases.isNotEmpty())
        assertTrue("Expected official snapshots", snapshots.isNotEmpty())
        assertTrue("Expected official beta/alpha versions", oldVersions.isNotEmpty())

        assertTrue(releases.all { it.type == "release" })
        assertTrue(snapshots.all { it.type == "snapshot" })
        assertTrue(oldVersions.all { it.type == "old_beta" || it.type == "old_alpha" })

        assertNotNull("Expected 1.21.4 in manifest", manifest.versions.firstOrNull { it.id == "1.21.4" })
        assertNotNull("Expected 1.8.9 in manifest", manifest.versions.firstOrNull { it.id == "1.8.9" })
    }

    @Test
    fun testFetchOfficialFabricLoadersAndProfile() = runBlocking {
        val gamesResult = OfficialMinecraftApi.fetchFabricGameVersions(forceRefresh = true)
        assertTrue("Fabric game versions fetch should succeed: ${gamesResult.exceptionOrNull()}", gamesResult.isSuccess)
        assertTrue("1.21.4 should be supported by Fabric", "1.21.4" in gamesResult.getOrThrow())

        val loadersResult = OfficialMinecraftApi.fetchFabricLoaders("1.21.4")
        assertTrue("Fabric loaders fetch should succeed: ${loadersResult.exceptionOrNull()}", loadersResult.isSuccess)
        val loaders = loadersResult.getOrThrow()
        assertTrue("Expected non-empty Fabric loaders", loaders.isNotEmpty())

        val stableLoader = loaders.firstOrNull { it.stable } ?: loaders.first()
        val profileUrl = OfficialMinecraftApi.fabricProfileJsonUrl("1.21.4", stableLoader.version)
        val rawProfileJson = OfficialMinecraftApi.fetchRawJson(profileUrl)
        val parsed = OfficialMinecraftApi.parseFabricProfileJson(rawProfileJson)

        assertEquals("1.21.4", parsed.inheritsFrom)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", parsed.mainClass)
        assertTrue("Expected Fabric profile libraries", parsed.libraries.isNotEmpty())
    }

    @Test
    fun testRealInstanceInstallAndPersistence() = runBlocking {
        val tempDir = Files.createTempDirectory("domrei_mc_test").toFile()
        try {
            val manager = MinecraftInstanceManager(tempDir)
            assertTrue("Fresh manager should have no preview instances", manager.loadInstances().isEmpty())

            val snapshots = mutableListOf<InstallProgressSnapshot>()
            val result = manager.installInstance(
                taskId = "test_vanilla_1_8_9",
                instanceName = "Vanilla 1.8.9",
                loaderType = InstanceLoaderType.VANILLA,
                mcVersion = "1.8.9",
                loaderVersion = null,
                includeAssets = false,
                onProgress = { snapshot -> snapshots.add(snapshot) },
            )

            assertTrue("Real install should succeed: ${result.exceptionOrNull()}", result.isSuccess)
            val installed = result.getOrThrow()
            assertEquals("test_vanilla_1_8_9", installed.id)
            assertEquals("Vanilla 1.8.9", installed.name)
            assertEquals("1.8.9", installed.mcVersion)
            assertEquals(InstanceLoaderType.VANILLA, installed.loaderType)

            assertTrue("Expected progress snapshots", snapshots.isNotEmpty())
            assertTrue("Final snapshot should be complete", snapshots.last().isComplete)
            assertEquals(1f, snapshots.last().progress, 0.001f)

            val versionJsonFile = File(tempDir, "versions/1.8.9/1.8.9.json")
            val clientJarFile = File(tempDir, "versions/1.8.9/1.8.9.jar")
            val assetIndexFile = File(tempDir, "assets/indexes/${installed.assetIndexId}.json")
            assertTrue("version.json must exist on disk", versionJsonFile.isFile)
            assertTrue("client.jar must exist on disk", clientJarFile.isFile)
            assertTrue("assetIndex.json must exist on disk", assetIndexFile.isFile)
            assertTrue("gamedir must exist on disk", manager.gameDirFor("test_vanilla_1_8_9").isDirectory)

            val parsedVersion = OfficialMinecraftApi.parseVersionPackageJson(
                "1.8.9",
                versionJsonFile.readText(Charsets.UTF_8),
            )
            assertEquals(
                parsedVersion.clientJar.sha1,
                MinecraftInstanceManager.computeSha1(clientJarFile),
            )
            parsedVersion.libraries.forEach { lib ->
                assertTrue("Library ${lib.relativePath} must exist on disk", File(tempDir, lib.relativePath).isFile)
            }

            val reloaded = manager.loadInstances()
            assertEquals(1, reloaded.size)
            assertEquals("test_vanilla_1_8_9", reloaded.first().id)

            manager.removeInstance("test_vanilla_1_8_9")
            assertTrue("Instances should be empty after remove", manager.loadInstances().isEmpty())
            assertFalse(File(tempDir, "instances/test_vanilla_1_8_9").exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
