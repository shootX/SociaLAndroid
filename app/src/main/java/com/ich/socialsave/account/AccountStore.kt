package com.ich.socialsave.account

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccountUser(val id: String, val name: String?, val email: String?, val avatarUrl: String?)

sealed interface AccountState {
    data object SignedOut : AccountState

    /** [expired]: the server answered 401; the library stays, Settings asks to sign in again. */
    data class SignedIn(val user: AccountUser, val expired: Boolean) : AccountState
}

/** The `state` and PKCE `code_verifier` of an Apple web flow the app started. */
data class PendingAppleSignIn(val state: String, val codeVerifier: String)

data class SyncStatus(val running: Boolean = false, val lastSyncedAt: Long? = null, val failed: Boolean = false)

/** Where the sync engine keeps the server's opaque cursor. */
interface SyncCursorStore {
    var cursor: String?
}

/**
 * Token, user, cursor and sync state in a private preferences file. `account.xml` is excluded from cloud
 * backup and device transfer (res/xml/data_extraction_rules.xml, backup_rules.xml), so a token never
 * leaves this phone.
 */
class AccountStore(context: Context) : SyncCursorStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _sync = MutableStateFlow(
        SyncStatus(
            lastSyncedAt = prefs.getLong(KEY_LAST_SYNCED, 0L).takeIf { it > 0 },
            failed = prefs.getBoolean(KEY_SYNC_FAILED, false),
        ),
    )
    val sync: StateFlow<SyncStatus> = _sync.asStateFlow()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val tokenIssuedAt: Long get() = prefs.getLong(KEY_TOKEN_ISSUED_AT, 0L)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)

    /** Signed in, possibly with an expired session: deletes must be remembered for the next sync. */
    val hasAccount: Boolean get() = userId != null

    val canSync: Boolean get() = token != null && !prefs.getBoolean(KEY_EXPIRED, false)

    override var cursor: String?
        get() = prefs.getString(KEY_CURSOR, null)
        set(value) = prefs.edit { putString(KEY_CURSOR, value) }

    fun saveSession(token: String, user: AccountUser, issuedAt: Long) {
        prefs.edit(commit = true) {
            putString(KEY_TOKEN, token)
            putLong(KEY_TOKEN_ISSUED_AT, issuedAt)
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_AVATAR, user.avatarUrl)
            putBoolean(KEY_EXPIRED, false)
        }
        _state.value = readState()
    }

    fun markExpired() {
        if (!hasAccount) return
        prefs.edit(commit = true) { putBoolean(KEY_EXPIRED, true) }
        _state.value = readState()
    }

    fun setSyncRunning(running: Boolean) {
        _sync.value = _sync.value.copy(running = running)
    }

    fun setSyncResult(succeeded: Boolean, at: Long) {
        prefs.edit {
            if (succeeded) putLong(KEY_LAST_SYNCED, at)
            putBoolean(KEY_SYNC_FAILED, !succeeded)
        }
        _sync.value = _sync.value.copy(
            lastSyncedAt = if (succeeded) at else _sync.value.lastSyncedAt,
            failed = !succeeded,
        )
    }

    /**
     * Remembers the `state` sent to the Apple web flow, so the deep link can be checked against it, and the
     * PKCE `code_verifier` that redeems the one-time code.
     */
    fun setPendingApple(state: String, codeVerifier: String, at: Long) {
        prefs.edit(commit = true) {
            putString(KEY_APPLE_STATE, state)
            putString(KEY_APPLE_CODE_VERIFIER, codeVerifier)
            putLong(KEY_APPLE_STATE_AT, at)
        }
    }

    /** Returns and forgets the pending Apple sign-in, or null when there is none or it is older than [maxAgeMillis]. */
    fun takePendingApple(now: Long, maxAgeMillis: Long): PendingAppleSignIn? {
        val state = prefs.getString(KEY_APPLE_STATE, null)
        val codeVerifier = prefs.getString(KEY_APPLE_CODE_VERIFIER, null)
        val at = prefs.getLong(KEY_APPLE_STATE_AT, 0L)
        prefs.edit(commit = true) {
            remove(KEY_APPLE_STATE)
            remove(KEY_APPLE_CODE_VERIFIER)
            remove(KEY_APPLE_STATE_AT)
        }
        if (state == null || codeVerifier == null || now - at !in 0..maxAgeMillis) return null
        return PendingAppleSignIn(state, codeVerifier)
    }

    /** Signs out: token, user, cursor and sync state are all forgotten. */
    fun clear() {
        prefs.edit(commit = true) { clear() }
        _state.value = AccountState.SignedOut
        _sync.value = SyncStatus()
    }

    private fun readState(): AccountState {
        val id = prefs.getString(KEY_USER_ID, null) ?: return AccountState.SignedOut
        return AccountState.SignedIn(
            AccountUser(
                id = id,
                name = prefs.getString(KEY_USER_NAME, null),
                email = prefs.getString(KEY_USER_EMAIL, null),
                avatarUrl = prefs.getString(KEY_USER_AVATAR, null),
            ),
            expired = prefs.getBoolean(KEY_EXPIRED, false),
        )
    }

    private companion object {
        const val FILE = "account"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_ISSUED_AT = "token.issuedAt"
        const val KEY_USER_ID = "user.id"
        const val KEY_USER_NAME = "user.name"
        const val KEY_USER_EMAIL = "user.email"
        const val KEY_USER_AVATAR = "user.avatarUrl"
        const val KEY_EXPIRED = "session.expired"
        const val KEY_CURSOR = "sync.cursor"
        const val KEY_LAST_SYNCED = "sync.lastSyncedAt"
        const val KEY_SYNC_FAILED = "sync.failed"
        const val KEY_APPLE_STATE = "apple.state"
        const val KEY_APPLE_CODE_VERIFIER = "apple.codeVerifier"
        const val KEY_APPLE_STATE_AT = "apple.stateAt"
    }
}
