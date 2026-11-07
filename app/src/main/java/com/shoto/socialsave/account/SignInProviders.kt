package com.shoto.socialsave.account

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.FacebookSdk
import com.facebook.login.LoginManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.shoto.socialsave.BuildConfig

/** "<manufacturer> <model>", sent as `device_name` so the account's token list is readable. */
fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

object GoogleSignIn {
    /**
     * Returns a Google ID token whose audience is the server client id, or null if the user cancelled.
     * Tries the account picker first; with no Google account on the phone, falls back to the
     * "Sign in with Google" flow, which can add one.
     */
    suspend fun idToken(activity: Activity): String? {
        val manager = CredentialManager.create(activity)
        val picker = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val response = try {
            try {
                manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(picker).build())
            } catch (_: NoCredentialException) {
                val button = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_SERVER_CLIENT_ID).build()
                manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(button).build())
            }
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        val credential = response.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type ${credential.type}")
    }

    /** Forget the "signed in with Google" state so the next sign-in shows the account picker again. */
    suspend fun clearState(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { Log.w("GoogleSignIn", "clearCredentialState failed", it) }
    }
}

object FacebookSignIn {
    /** Hidden until FACEBOOK_APP_ID and FACEBOOK_CLIENT_TOKEN are configured and the SDK started. */
    val available: Boolean
        get() = BuildConfig.FACEBOOK_ENABLED && runCatching { FacebookSdk.isInitialized() }.getOrDefault(false)

    /** We only need Facebook's token once, to sign in to our API; don't keep it in Facebook's store. */
    fun forgetToken() {
        if (available) runCatching { LoginManager.getInstance().logOut() }
    }
}

object AppleSignIn {
    /** Opens `{API}/auth/apple/start?state=…&code_challenge=…` in a Custom Tab; the answer comes back to [AppleAuthActivity]. */
    fun start(context: Context, url: String): Boolean = runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }.isSuccess
}
