package com.shoto.socialsave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {
    @Test fun stripsTrackingWwwFragmentAndTrailingSlash() {
        assertEquals(
            "https://instagram.com/reel/ABC123",
            UrlNormalizer.canonicalUrl("https://www.Instagram.com/reel/ABC123/?igsh=xyz&utm_source=ig#frag"),
        )
    }

    @Test fun sortsRemainingQueryItems() {
        assertEquals(
            "https://youtube.com/watch?t=10&v=abc",
            UrlNormalizer.canonicalUrl("https://www.youtube.com/watch?v=abc&si=share&t=10"),
        )
    }

    @Test fun findsLinkInsideSharedCaption() {
        val shared = "Look at this! https://vm.tiktok.com/ZMabc123/ so good."
        assertEquals("https://vm.tiktok.com/ZMabc123/", UrlNormalizer.firstHttpUrl(shared))
        assertEquals("https://vm.tiktok.com/ZMabc123", UrlNormalizer.canonicalUrl(shared))
    }

    @Test fun acceptsBareWwwLinks() {
        assertEquals("http://example.com/a", UrlNormalizer.canonicalUrl("see www.example.com/a."))
    }

    @Test fun rejectsNonWebText() {
        assertNull(UrlNormalizer.canonicalUrl("hello there"))
        assertNull(UrlNormalizer.canonicalUrl("ftp://example.com/file"))
        assertNull(UrlNormalizer.canonicalUrl(""))
    }

    @Test fun keepsRootWithoutSlash() {
        assertEquals("https://example.com", UrlNormalizer.canonicalUrl("https://example.com"))
        assertEquals("https://example.com/", UrlNormalizer.canonicalUrl("https://example.com/"))
    }

    @Test fun encodesSpacesLeniently() {
        assertEquals("https://example.com/a%20b", UrlNormalizer.canonicalUrl("https://example.com/a b"))
    }
}
