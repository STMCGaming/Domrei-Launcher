package com.domrei.launcher

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's account list and the selected account in SharedPreferences as a small
 * JSON array, so added accounts survive app restarts.
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAccounts(): List<LauncherAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString(FIELD_ID).trim()
                    val username = obj.optString(FIELD_USERNAME).trim()
                    if (id.isEmpty() || username.isEmpty()) continue
                    add(
                        LauncherAccount(
                            id = id,
                            username = username,
                            typeRes = typeResFor(obj.optString(FIELD_TYPE)),
                            uuid = obj.optString(FIELD_UUID).takeIf { it.isNotEmpty() },
                            accessToken = obj.optString(FIELD_ACCESS_TOKEN).takeIf { it.isNotEmpty() },
                            msaRefreshToken = obj.optString(FIELD_REFRESH_TOKEN).takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccounts(accounts: List<LauncherAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            val obj = JSONObject()
                .put(FIELD_ID, account.id)
                .put(FIELD_USERNAME, account.username)
                .put(FIELD_TYPE, typeKeyFor(account.typeRes))
            account.uuid?.let { obj.put(FIELD_UUID, it) }
            account.accessToken?.let { obj.put(FIELD_ACCESS_TOKEN, it) }
            account.msaRefreshToken?.let { obj.put(FIELD_REFRESH_TOKEN, it) }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun loadSelectedAccountId(): String =
        prefs.getString(KEY_SELECTED_ACCOUNT, "").orEmpty()

    fun saveSelectedAccountId(accountId: String) {
        prefs.edit().putString(KEY_SELECTED_ACCOUNT, accountId).apply()
    }

    private fun typeKeyFor(@StringRes resId: Int): String =
        if (resId == R.string.account_type_microsoft) TYPE_MICROSOFT else TYPE_OFFLINE

    private fun typeResFor(key: String): Int =
        if (key == TYPE_MICROSOFT) R.string.account_type_microsoft else R.string.account_type_offline

    private companion object {
        const val PREFS_NAME = "domrei_accounts"
        const val KEY_ACCOUNTS = "accounts_json"
        const val KEY_SELECTED_ACCOUNT = "selected_account_id"
        const val FIELD_ID = "id"
        const val FIELD_USERNAME = "username"
        const val FIELD_TYPE = "type"
        const val FIELD_UUID = "uuid"
        const val FIELD_ACCESS_TOKEN = "access_token"
        const val FIELD_REFRESH_TOKEN = "refresh_token"
        const val TYPE_MICROSOFT = "microsoft"
        const val TYPE_OFFLINE = "offline"
    }
}
