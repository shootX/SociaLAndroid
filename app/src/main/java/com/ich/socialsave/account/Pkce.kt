package com.ich.socialsave.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636, S256) for the Apple web flow: the server redeems the one-time code from the deep link
 * only together with the verifier, so another app that intercepts `socialsave://auth/apple` gets nothing.
 */
object Pkce {
    private val random = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** 32 random bytes, base64url without padding (43 characters): a `code_verifier`, or a `state`. */
    fun randomToken(): String = base64Url.encodeToString(ByteArray(32).also(random::nextBytes))

    /** `code_challenge` = base64url(SHA-256(verifier)) without padding. */
    fun challengeFor(verifier: String): String =
        base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
