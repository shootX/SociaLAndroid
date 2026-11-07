package com.shoto.socialsave.account

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import java.io.IOException
import java.security.SecureRandom

enum class SignInStatus { IDLE, WORKING, FAILED }

/** [LATER]: the server held its cursor; run again in a few seconds (not a failure). */
enum class SyncOutcome { SUCCESS, LATER, SKIPPED, RETRY, FAILED, EXPIRED }

enum class SignOutResult { DONE, UNSYNCED }

/**
 * Signing in and out, deleting the account and running sync. Sync, sign-out and deletion share one lock,
 * so a background sync can never write rows back after the library was wiped, and the 7-day token
 * refresh never revokes a token another request is still using.
 */
class AccountSession(
    private val api: ApiClient,
    private val store: AccountStore,
    private val engine: SyncEngine,
    private val local: RoomSyncStore,
    private val scope: CoroutineScope,
    private val deviceName: String,
    private val onSignedIn: () -> Unit,
    private val onRemoteChanges: (needsMetadata: Boolean) -> Unit,
    private val onSignedOut: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    val state: StateFlow<AccountState> = store.state
    val syncStatus: StateFlow<SyncStatus> = store.sync

    private val _signIn = MutableStateFlow(SignInStatus.IDLE)
    val signInStatus: StateFlow<SignInStatus> = _signIn.asStateFlow()

    fun clearSignInError() {
        if (_signIn.value == SignInStatus.FAILED) _signIn.value = SignInStatus.IDLE
    }

    /** A provider (Google, Facebook) failed before we had a token to send. */
    fun reportSignInFailure() {
        _signIn.value = SignInStatus.FAILED
    }

    fun signInWithGoogle(idToken: String) = launchSignIn { api.signInWithGoogle(idToken, deviceName) }

    fun signInWithFacebook(accessToken: String) = launchSignIn { api.signInWithFacebook(accessToken, deviceName) }

    /** Starts the Apple web flow: returns the URL to open in a Custom Tab. */
    fun appleStartUrl(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val state = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        store.setPendingAppleState(state, clock())
        clearSignInError()
        return api.appleStartUrl(state)
    }

    /** `socialsave://auth/apple?code=…&state=…` or `?error=…&state=…` came back from the server. */
    fun completeApple(code: String?, state: String?, error: String?) {
        val expected = store.takePendingAppleState(clock(), APPLE_STATE_MAX_AGE)
        when {
            // Not a flow we started (or a stale one): ignore it without showing an error.
            expected == null -> Log.w(TAG, "Apple callback without a pending sign-in")
            state != expected || error != null || code.isNullOrEmpty() -> {
                Log.w(TAG, "Apple sign-in failed: ${error ?: "state mismatch or missing code"}")
                _signIn.value = SignInStatus.FAILED
            }
            else -> launchSignIn { api.exchangeAppleCode(code, deviceName) }
        }
    }

    private fun launchSignIn(request: suspend () -> SessionPayload) {
        _signIn.value = SignInStatus.WORKING
        scope.launch {
            val result = runCatching { request() }
            result.onSuccess { session ->
                lock.withLock {
                    val previousUser = store.userId
                    store.saveSession(session.token, session.user, clock())
                    if (previousUser != session.user.id) {
                        // First sign-in (or another account): upload and merge everything on this phone.
                        store.cursor = null
                        local.markAllDirty()
                    }
                }
                _signIn.value = SignInStatus.IDLE
                onSignedIn()
            }.onFailure {
                Log.w(TAG, "Sign-in failed", it)
                _signIn.value = SignInStatus.FAILED
            }
        }
    }

    /** One sync run (WorkManager calls this). */
    suspend fun sync(): SyncOutcome = lock.withLock { syncLocked() }

    private suspend fun syncLocked(): SyncOutcome {
        if (!store.canSync) return SyncOutcome.SKIPPED
        store.setSyncRunning(true)
        try {
            refreshTokenIfOld()
            if (!store.canSync) return SyncOutcome.EXPIRED
            val result = engine.run()
            if (result.changedLocally) onRemoteChanges(result.needsMetadata)
            store.setSyncResult(succeeded = true, at = clock())
            return if (result.retryLater) SyncOutcome.LATER else SyncOutcome.SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val outcome = classify(error)
            Log.w(TAG, "Sync failed: $outcome", error)
            if (outcome == SyncOutcome.EXPIRED) store.markExpired()
            store.setSyncResult(succeeded = false, at = clock())
            return outcome
        } finally {
            store.setSyncRunning(false)
        }
    }

    /** Tokens live 30 days; one older than 7 days is swapped for a new one (the old one is revoked). */
    private suspend fun refreshTokenIfOld() {
        if (clock() - store.tokenIssuedAt < REFRESH_AFTER) return
        try {
            val session = api.refresh()
            store.saveSession(session.token, session.user, clock())
        } catch (error: ApiException) {
            if (error.unauthorized) store.markExpired() else Log.w(TAG, "Token refresh failed", error)
        } catch (error: IOException) {
            Log.w(TAG, "Token refresh failed", error) // offline: the old token is still valid
        }
    }

    /**
     * Sign out: a final sync first. If it fails and [force] is false, returns [SignOutResult.UNSYNCED] so the
     * UI can ask with `account_sign_out_unsynced`. Then logout, wipe the token/cursor, clear the library.
     */
    suspend fun signOut(force: Boolean): SignOutResult = lock.withLock {
        if (!force && !finalSyncLocked()) return@withLock SignOutResult.UNSYNCED
        if (store.canSync) runCatching { api.logout() }.onFailure { Log.w(TAG, "Logout call failed", it) }
        wipeLocked()
        SignOutResult.DONE
    }

    /** Sign-out's final sync succeeded when nothing local is left to upload (pulling more doesn't matter). */
    private suspend fun finalSyncLocked(): Boolean {
        val outcome = syncLocked()
        return (outcome == SyncOutcome.SUCCESS || outcome == SyncOutcome.LATER) && !local.hasPendingChanges()
    }

    /** `DELETE /me`, then the same local wipe as sign-out. Returns false (and keeps everything) on failure. */
    suspend fun deleteAccount(): Boolean = lock.withLock {
        try {
            api.deleteAccount()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Account deletion failed", error)
            if (error is ApiException && error.unauthorized) store.markExpired()
            return@withLock false
        }
        wipeLocked()
        true
    }

    private suspend fun wipeLocked() {
        store.clear()
        local.wipe()
        _signIn.value = SignInStatus.IDLE
        onSignedOut()
    }

    private fun classify(error: Exception): SyncOutcome = when (error) {
        is ApiException -> when {
            error.unauthorized -> SyncOutcome.EXPIRED
            error.transient -> SyncOutcome.RETRY
            else -> SyncOutcome.FAILED
        }
        is IOException -> SyncOutcome.RETRY
        is JSONException -> SyncOutcome.FAILED
        else -> SyncOutcome.FAILED
    }

    private companion object {
        const val TAG = "AccountSession"
        const val REFRESH_AFTER = 7L * 24 * 60 * 60 * 1000
        const val APPLE_STATE_MAX_AGE = 15L * 60 * 1000
    }
}
