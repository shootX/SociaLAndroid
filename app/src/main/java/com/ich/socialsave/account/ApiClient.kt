package com.ich.socialsave.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

data class SessionPayload(val token: String, val user: AccountUser)

/** A non-2xx answer from the API. [status] 401 means the token is no longer valid. */
class ApiException(val status: Int, message: String?) : IOException("HTTP $status${message?.let { ": $it" }.orEmpty()}") {
    val unauthorized: Boolean get() = status == 401

    /** Worth retrying later: the server or a proxy was busy, not a bad request. */
    val transient: Boolean get() = status == 408 || status == 429 || status >= 500
}

/**
 * SocialSave API v1 (see the contract in plans/accounts-sync-ads.md). Answers are wrapped as
 * `{"success": true, "data": …}`; this returns `data`. Plain Kotlin + OkHttp so tests run on the JVM.
 */
class ApiClient(
    baseUrl: String,
    private val http: OkHttpClient,
    private val token: () -> String?,
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun signInWithGoogle(idToken: String, deviceName: String): SessionPayload =
        session(post("auth/google", JSONObject().put("id_token", idToken).put("device_name", deviceName), auth = false))

    suspend fun signInWithFacebook(accessToken: String, deviceName: String): SessionPayload =
        session(post("auth/facebook", JSONObject().put("access_token", accessToken).put("device_name", deviceName), auth = false))

    suspend fun exchangeAppleCode(code: String, codeVerifier: String, deviceName: String): SessionPayload =
        session(
            post(
                "auth/apple/exchange",
                JSONObject().put("code", code).put("code_verifier", codeVerifier).put("device_name", deviceName),
                auth = false,
            ),
        )

    suspend fun refresh(): SessionPayload = session(post("auth/refresh", null))

    suspend fun logout() {
        post("auth/logout", null)
    }

    suspend fun deleteAccount() {
        call("DELETE", "me", null, auth = true)
    }

    suspend fun sync(body: JSONObject): JSONObject =
        post("sync", body) as? JSONObject ?: throw JSONException("sync: missing data")

    /**
     * `GET {API}/auth/apple/start?state=…&code_challenge=…&code_challenge_method=S256` — opened in a Custom Tab,
     * never fetched by the app. Both values are base64url, so they need no escaping.
     */
    fun appleStartUrl(state: String, codeChallenge: String): String =
        "$base/auth/apple/start?state=$state&code_challenge=$codeChallenge&code_challenge_method=S256"

    private suspend fun post(path: String, body: JSONObject?, auth: Boolean = true): Any? =
        call("POST", path, body, auth)

    private suspend fun call(method: String, path: String, body: JSONObject?, auth: Boolean): Any? =
        withContext(Dispatchers.IO) {
            val requestBody: RequestBody? = when {
                body != null -> body.toString().toRequestBody(JSON)
                method == "POST" -> ByteArray(0).toRequestBody(JSON)
                else -> null
            }
            val request = Request.Builder()
                .url("$base/$path")
                .header("Accept", "application/json")
                .apply { if (auth) token()?.let { header("Authorization", "Bearer $it") } }
                .method(method, requestBody)
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = text.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
                if (!response.isSuccessful) throw ApiException(response.code, json?.optString("message")?.takeIf { it.isNotEmpty() })
                if (json != null && json.has("success") && !json.optBoolean("success")) {
                    throw ApiException(response.code, json.optString("message"))
                }
                json?.opt("data")?.takeIf { it != JSONObject.NULL }
            }
        }

    private fun session(data: Any?): SessionPayload {
        val obj = data as? JSONObject ?: throw JSONException("auth: missing data")
        val user = obj.getJSONObject("user")
        return SessionPayload(
            token = obj.getString("token"),
            user = AccountUser(
                id = user.get("id").toString(),
                name = user.stringOrNull("name"),
                email = user.stringOrNull("email"),
                avatarUrl = user.stringOrNull("avatar_url"),
            ),
        )
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** org.json returns the text "null" for JSON nulls on Android; this returns a real null. */
internal fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
