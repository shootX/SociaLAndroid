package com.ich.socialsave.core

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
            "https://example.com/watch?t=10&v=abc",
            UrlNormalizer.canonicalUrl("https://www.example.com/watch?v=abc&si=share&t=10"),
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
        assertEquals("https://example.com/a", UrlNormalizer.canonicalUrl("https://example.com/a b"))
    }

    @Test fun encodesIllegalCharactersInSingleToken() {
        assertEquals("https://example.com/%C3%BC%7Cb", UrlNormalizer.canonicalUrl("https://example.com/ü|b"))
        assertEquals("https://example.com/a%7Cb", UrlNormalizer.firstHttpUrl("https://example.com/a|b"))
    }

    @Test fun linkFollowedByCaptionIsNotEncodedIntoTheUrl() {
        val shared = "https://youtube.com/watch?v=abc Never gonna"
        assertEquals("https://youtube.com/watch?v=abc", UrlNormalizer.firstHttpUrl(shared))
        assertEquals("https://youtube.com/watch?v=abc", UrlNormalizer.canonicalUrl(shared))
    }

    // Shared test vectors: the iOS app and the API must produce exactly the same canonical values.
    private fun assertCanonical(input: String, expected: String, firstLink: String? = null) {
        assertEquals(expected, UrlNormalizer.canonicalUrl(input))
        if (firstLink != null) assertEquals(firstLink, UrlNormalizer.firstHttpUrl(input))
    }

    @Test fun xDropsShareQuery() =
        assertCanonical("https://x.com/u/status/123?s=46&t=AbCd", "https://x.com/u/status/123")

    @Test fun twitterBecomesX() =
        assertCanonical("https://twitter.com/u/status/123?s=20", "https://x.com/u/status/123")

    @Test fun mobileTwitterBecomesX() =
        assertCanonical("https://mobile.twitter.com/u/status/123", "https://x.com/u/status/123")

    @Test fun tiktokDropsQuery() =
        assertCanonical("https://www.tiktok.com/@a/video/1?_t=8hAAA&_r=1", "https://tiktok.com/@a/video/1")

    @Test fun youtuBeBecomesWatchUrl() =
        assertCanonical("https://youtu.be/dQw4w9WgXcQ?si=abc", "https://youtube.com/watch?v=dQw4w9WgXcQ")

    @Test fun mobileYoutubeKeepsOnlyV() = assertCanonical(
        "https://m.youtube.com/watch?feature=youtu.be&v=dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ",
    )

    @Test fun youtubeDropsTimestamp() = assertCanonical(
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s",
        "https://youtube.com/watch?v=dQw4w9WgXcQ",
    )

    @Test fun instagramDropsQuery() = assertCanonical(
        "https://www.instagram.com/reel/ABC/?igsh=xyz&utm_source=ig",
        "https://instagram.com/reel/ABC",
    )

    @Test fun mobileFacebookKeepsAllowlistedKeys() = assertCanonical(
        "https://m.facebook.com/story.php?story_fbid=1&id=2&ref=share",
        "https://facebook.com/story.php?id=2&story_fbid=1",
    )

    @Test fun nonSocialKeepsTParameter() = assertCanonical(
        "https://example.com/forum/viewtopic.php?t=5&utm_source=x",
        "https://example.com/forum/viewtopic.php?t=5",
    )

    @Test fun linkFollowedBySpaceAndCaption() = assertCanonical(
        "https://www.instagram.com/p/ABC/ look at this",
        "https://instagram.com/p/ABC",
        firstLink = "https://www.instagram.com/p/ABC/",
    )

    @Test fun linkFollowedByNewlineAndCaption() =
        assertCanonical("https://vm.tiktok.com/ZMabc/\nCheck this out", "https://vm.tiktok.com/ZMabc")

    @Test fun linkInsideCaption() = assertCanonical(
        "Look at this! https://vm.tiktok.com/ZMabc123/ so good.",
        "https://vm.tiktok.com/ZMabc123",
    )

    @Test fun rewrittenHostsKeepTheirPlatform() {
        assertEquals(SocialPlatform.X, PlatformDetector.detect(UrlNormalizer.canonicalUrl("https://mobile.twitter.com/u/status/1")!!))
        assertEquals(SocialPlatform.YOUTUBE, PlatformDetector.detect(UrlNormalizer.canonicalUrl("https://youtu.be/abc")!!))
    }
}
