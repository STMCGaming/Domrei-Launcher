package com.domrei.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftAuthenticatorTest {

    @Test
    fun testLoginUrlFormat() {
        val url = MicrosoftAuthenticator.LOGIN_URL
        assertTrue("URL should point to login.live.com", url.startsWith("https://login.live.com/oauth20_authorize.srf"))
        assertTrue("URL should include client_id", url.contains("client_id=00000000402b5328"))
        assertTrue("URL should request response_type=code", url.contains("response_type=code"))
        assertTrue("URL should request xboxlive scope", url.contains("scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"))
    }

    @Test
    fun testIsRedirectUrl() {
        assertTrue(
            MicrosoftAuthenticator.isRedirectUrl("https://login.live.com/oauth20_desktop.srf?code=M.R3_BL2.abcdef"),
        )
        assertTrue(
            MicrosoftAuthenticator.isRedirectUrl("ms-xal-00000000402b5328://auth?code=test_code"),
        )
        assertFalse(
            MicrosoftAuthenticator.isRedirectUrl("https://login.live.com/ppsecure/post.srf"),
        )
        assertFalse(
            MicrosoftAuthenticator.isRedirectUrl("https://account.microsoft.com"),
        )
    }

    @Test
    fun testExtractCode() {
        val code = MicrosoftAuthenticator.extractCode("https://login.live.com/oauth20_desktop.srf?code=M.C123456_XYZ")
        assertEquals("M.C123456_XYZ", code)

        val noCode = MicrosoftAuthenticator.extractCode("https://login.live.com/oauth20_desktop.srf?error=access_denied")
        assertNull(noCode)
    }
}
