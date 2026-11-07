package com.ich.socialsave.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun challengeMatchesTheRfc7636AppendixBVector() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifiersAre43UnpaddedBase64UrlCharactersAndUnique() {
        val verifier = Pkce.randomToken()
        assertTrue(verifier, Regex("[A-Za-z0-9_-]{43}").matches(verifier))
        assertNotEquals(verifier, Pkce.randomToken())
    }
}
