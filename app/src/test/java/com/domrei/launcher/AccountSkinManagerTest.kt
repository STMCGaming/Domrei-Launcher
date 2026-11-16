package com.domrei.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSkinManagerTest {

    @Test
    fun testIsMicrosoftAccount() {
        assertFalse(AccountSkinManager.isMicrosoftAccount(null))

        val offline = LauncherAccount(
            id = "offline-1",
            username = "Steve",
            typeRes = R.string.account_type_offline,
        )
        assertFalse(AccountSkinManager.isMicrosoftAccount(offline))

        val msWithoutUuid = LauncherAccount(
            id = "ms-1",
            username = "Alex",
            typeRes = R.string.account_type_microsoft,
            uuid = "",
        )
        assertFalse(AccountSkinManager.isMicrosoftAccount(msWithoutUuid))

        val msWithUuid = LauncherAccount(
            id = "ms-2",
            username = "Alex",
            typeRes = R.string.account_type_microsoft,
            uuid = "12345678-1234-1234-1234-123456789abc",
        )
        assertTrue(AccountSkinManager.isMicrosoftAccount(msWithUuid))
    }

    @Test
    fun testOfflineAccountHasNoCape() {
        val offline = LauncherAccount(
            id = "offline-1",
            username = "Steve",
            typeRes = R.string.account_type_offline,
        )
        assertEquals(emptyList<String>(), AccountSkinManager.getSkinCandidates(offline))
    }

    @Test
    fun testMicrosoftAccountCandidates() {
        val ms = LauncherAccount(
            id = "account-id-123",
            username = "Player",
            typeRes = R.string.account_type_microsoft,
            uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5",
        )
        val candidates = AccountSkinManager.getSkinCandidates(ms)
        assertTrue(candidates.contains("069a79f4-44e9-4726-a5be-fca90e38aaf5"))
        assertTrue(candidates.contains("069a79f444e94726a5befca90e38aaf5"))
        assertTrue(candidates.contains("account-id-123"))
    }
}
