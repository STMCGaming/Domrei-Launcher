package com.domrei.skinview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MojangSkinApiTest {

    @Test
    fun testFetchNotchProfile() = runBlocking {
        val result = MojangSkinApi.fetchByUsername("Notch")
        assertTrue("Fetching Notch should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val profile = result.getOrNull()
        assertNotNull(profile)
        assertEquals("Notch", profile?.username)
        assertNotNull("Skin URL should be present", profile?.skinUrl)
        val skinUrl = profile!!.skinUrl!!
        assertTrue(
            "Skin URL should point to textures.minecraft.net, crafatar.com, or minotar.net: $skinUrl",
            skinUrl.contains("textures.minecraft.net") || skinUrl.contains("crafatar.com") || skinUrl.contains("minotar.net"),
        )
    }
}
