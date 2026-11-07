package com.ich.socialsave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialPreviewParserTest {
    @Test fun instagramShortcodes() {
        assertEquals("Cxyz123", SocialPreviewParser.instagramShortcode("https://instagram.com/reel/Cxyz123/"))
        assertEquals("Cxyz123", SocialPreviewParser.instagramShortcode("https://instagram.com/user/p/Cxyz123"))
        assertNull(SocialPreviewParser.instagramShortcode("https://instagram.com/someone"))
    }

    @Test fun imageCandidatesInOrder() {
        val html = """
            <meta content="/img/og.jpg" property="og:image">
            <meta name="twitter:image" content="https://cdn.example.com/tw.jpg?a=1&amp;b=2">
            <script>{"display_url":"https:\/\/cdn.example.com\/d.jpg?x=1&y=2"}</script>
            <meta property="og:image" content="https://www.instagram.com/static/images/logo.png">
        """.trimIndent()
        val candidates = SocialPreviewParser.imageCandidates(html, "https://example.com/post/1")
        assertEquals(
            listOf(
                "https://example.com/img/og.jpg",
                "https://cdn.example.com/tw.jpg?a=1&b=2",
                "https://cdn.example.com/d.jpg?x=1&y=2",
            ),
            candidates,
        )
    }

    @Test fun detectsHtmlBodies() {
        assertTrue(SocialPreviewParser.looksLikeHtml("  <!DOCTYPE html><html>".toByteArray()))
    }
}
