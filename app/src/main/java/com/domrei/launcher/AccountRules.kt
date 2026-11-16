package com.domrei.launcher

import androidx.annotation.StringRes

/** Minecraft Java edition username rules: 3-16 characters, letters, digits and underscore only. */
private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{3,16}$")

/** Validation failure for a username, with the message resource to show. */
enum class UsernameError(@StringRes val messageRes: Int) {
    INVALID(R.string.dialog_username_invalid),
    TAKEN(R.string.dialog_username_taken),
}

/**
 * Validates [username] against Minecraft's naming rules and against the accounts already
 * in the list (names are unique, case-insensitive). Pass [ignoreAccountId] when renaming.
 */
fun validateUsername(
    username: String,
    accounts: List<LauncherAccount>,
    ignoreAccountId: String? = null,
): UsernameError? {
    if (!USERNAME_PATTERN.matches(username)) return UsernameError.INVALID
    val isTaken = accounts.any {
        it.id != ignoreAccountId && it.username.equals(username, ignoreCase = true)
    }
    return if (isTaken) UsernameError.TAKEN else null
}
