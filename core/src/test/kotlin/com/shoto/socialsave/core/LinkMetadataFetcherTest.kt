package com.shoto.socialsave.core

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LinkMetadataFetcherTest {
    private val server = MockWebServer()
    private val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/post" -> MockResponse()
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(
                        """<html><head><title>Fallback</title>
                        <meta property="og:title" content="Pasta in 15 minutes &amp; more">
                        <meta property="og:image" content="/cover.jpg"></head></html>""",
                    )
                "/cover.jpg" -> MockResponse().setBody(Buffer().write(image))
                "/plain" -> MockResponse().setBody("<html><head><title>Just a title</title></head></html>")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun stop() = server.shutdown()

    @Test fun readsOpenGraphTitleAndImage() = runTest {
        val result = LinkMetadataFetcher().fetch(server.url("/post").toString())!!
        assertEquals("Pasta in 15 minutes & more", result.title)
        assertEquals(server.url("/cover.jpg").toString(), result.thumbnailUrl)
        assertArrayEquals(image, result.thumbnail)
    }

    @Test fun fallsBackToDocumentTitle() = runTest {
        val result = LinkMetadataFetcher().fetch(server.url("/plain").toString())!!
        assertEquals("Just a title", result.title)
        assertNull(result.thumbnail)
    }

    @Test fun returnsNullWhenNothingFound() = runTest {
        assertNull(LinkMetadataFetcher().fetch(server.url("/missing").toString()))
    }

    @Test fun rejectsImagesTheConverterCannotDecode() = runTest {
        val result = LinkMetadataFetcher(imageConverter = { null }).fetch(server.url("/post").toString())!!
        assertNull(result.thumbnail)
    }

    @Test fun jsonFieldsUnescape() {
        assertEquals("A \"quote\" é/", JsonFields.string("""{"title":"A \"quote\" é\/"}""", "title"))
    }

    @Test fun shortLinkDetection() {
        assertEquals(true, ShortLinkExpander.isShortLink("https://vm.tiktok.com/ZM1"))
        assertEquals(true, ShortLinkExpander.isShortLink("https://www.tiktok.com/t/ZT1"))
        assertEquals(false, ShortLinkExpander.isShortLink("https://www.tiktok.com/@a/video/1"))
        assertEquals(false, ShortLinkExpander.isShortLink("https://youtu.be/dQw4w9WgXcQ"))
    }
}
