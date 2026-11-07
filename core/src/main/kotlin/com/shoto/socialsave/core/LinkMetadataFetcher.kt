package com.shoto.socialsave.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

data class LinkMetadata(
    val title: String? = null,
    val authorName: String? = null,
    val sourceHost: String? = null,
    val thumbnailUrl: String? = null,
    /** Already converted by the fetcher's image converter (a JPEG thumbnail on Android). */
    val thumbnail: ByteArray? = null,
) {
    fun mergedWith(extra: LinkMetadata?): LinkMetadata {
        if (extra == null) return this
        return LinkMetadata(
            title = title ?: extra.title,
            authorName = authorName ?: extra.authorName,
            sourceHost = extra.sourceHost ?: sourceHost,
            thumbnailUrl = thumbnailUrl ?: extra.thumbnailUrl,
            thumbnail = thumbnail ?: extra.thumbnail,
        )
    }

    val isEmpty: Boolean get() = title == null && thumbnail == null && authorName == null
}

/**
 * Finds a title and preview image for a saved link, entirely on the device:
 * oEmbed for TikTok and YouTube, Open Graph tags for everything, and Instagram's embed page as a last resort.
 *
 * [imageConverter] turns downloaded image bytes into the stored thumbnail, or returns null when the bytes
 * are not a decodable image.
 */
class LinkMetadataFetcher(
    private val client: OkHttpClient = defaultClient(),
    private val imageConverter: (ByteArray) -> ByteArray? = { it },
    private val endpoints: Endpoints = Endpoints(),
) {
    data class Endpoints(
        val tiktokOEmbed: String = "https://www.tiktok.com/oembed?url=",
        val youtubeOEmbed: String = "https://www.youtube.com/oembed?format=json&url=",
        val instagram: String = "https://www.instagram.com",
    )

    suspend fun fetch(url: String): LinkMetadata? = withContext(Dispatchers.IO) {
        val host = UrlNormalizer.hostOf(url) ?: return@withContext null
        var result = LinkMetadata(sourceHost = host)

        result = result.mergedWith(fetchOEmbed(url, host))
        if (result.title == null || result.thumbnail == null) {
            result = result.mergedWith(fetchOpenGraph(url))
        }
        if (result.thumbnail == null) {
            result = result.mergedWith(fetchInstagramPoster(url))
        }
        if (result.isEmpty) null else result
    }

    private fun fetchOEmbed(url: String, host: String): LinkMetadata? {
        val endpoint = when {
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> endpoints.tiktokOEmbed
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" -> endpoints.youtubeOEmbed
            else -> return null
        }
        val body = fetchBytes(endpoint + URLEncoder.encode(url, "UTF-8"), "application/json", 256_000)
            ?: return null
        val json = String(body, Charsets.UTF_8)
        val title = TitleSanitizer.cleaned(JsonFields.string(json, "title"))
        val author = TitleSanitizer.cleaned(JsonFields.string(json, "author_name"))
        val imageUrl = SocialPreviewParser.normalizedImageUrl(JsonFields.string(json, "thumbnail_url"), url)
        val image = imageUrl?.let { downloadImage(it, url) }
        if (title == null && image == null) return null
        return LinkMetadata(title, author, UrlNormalizer.hostOf(url), imageUrl, image)
    }

    private fun fetchOpenGraph(url: String): LinkMetadata? {
        val html = fetchText(url) ?: return null
        val title = TitleSanitizer.cleaned(
            SocialPreviewParser.metaContent(html, "og:title")
                ?: SocialPreviewParser.metaContent(html, "twitter:title")
                ?: SocialPreviewParser.documentTitle(html),
        )
        val author = TitleSanitizer.cleaned(
            SocialPreviewParser.metaContent(html, "author")
                ?: SocialPreviewParser.metaContent(html, "article:author"),
        )
        val (imageUrl, image) = firstImage(SocialPreviewParser.imageCandidates(html, url), url)
        if (title == null && image == null && author == null) return null
        return LinkMetadata(title, author, UrlNormalizer.hostOf(url), imageUrl, image)
    }

    private fun fetchInstagramPoster(url: String): LinkMetadata? {
        val shortcode = SocialPreviewParser.instagramShortcode(url) ?: return null
        val base = endpoints.instagram
        val candidates = mutableListOf<String>()

        val embeds = listOf(
            "$base/p/$shortcode/embed/captioned/",
            "$base/reel/$shortcode/embed/captioned/",
            "$base/p/$shortcode/embed/",
            "$base/reel/$shortcode/embed/",
        )
        for (embed in embeds) {
            fetchText(embed)?.let { candidates += SocialPreviewParser.imageCandidates(it, embed) }
            if (candidates.isNotEmpty()) break
        }

        redirectedLocation("$base/p/$shortcode/media/?size=l")
            ?.let { SocialPreviewParser.normalizedImageUrl(it, url) }
            ?.let { candidates.add(0, it) }

        val (imageUrl, image) = firstImage(candidates, url)
        if (image == null) return null
        return LinkMetadata(sourceHost = UrlNormalizer.hostOf(url), thumbnailUrl = imageUrl, thumbnail = image)
    }

    private fun firstImage(candidates: List<String>, referer: String): Pair<String?, ByteArray?> {
        for (candidate in candidates.distinct()) {
            downloadImage(candidate, referer)?.let { return candidate to it }
        }
        return null to null
    }

    private fun downloadImage(url: String, referer: String): ByteArray? {
        val data = fetchBytes(url, "image/avif,image/webp,image/apng,image/*,*/*;q=0.8", 8_000_000, referer)
            ?: return null
        if (SocialPreviewParser.looksLikeHtml(data)) return null
        return imageConverter(data)
    }

    private fun request(url: String, accept: String, referer: String? = null): Request? = try {
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply { if (referer != null) header("Referer", referer) }
            .build()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun fetchText(url: String): String? {
        val request = request(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8") ?: return null
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code !in 200..399) return null
                val bytes = response.readLimited(512_000) ?: return null
                decodeHtml(bytes, response.body?.contentType())
            }
        }.getOrNull()
    }

    private fun fetchBytes(url: String, accept: String, maxBytes: Long, referer: String? = null): ByteArray? {
        val request = request(url, accept, referer) ?: return null
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.readLimited(maxBytes + 1) ?: return null
                if (bytes.size > maxBytes) null else bytes
            }
        }.getOrNull()
    }

    private fun redirectedLocation(url: String): String? {
        val request = request(url, "*/*")?.newBuilder()?.head()?.build() ?: return null
        return runCatching {
            client.newCall(request).execute().use { it.request.url.toString() }
        }.getOrNull()
    }

    private fun Response.readLimited(limit: Long): ByteArray? {
        val source = body?.source() ?: return null
        source.request(limit)
        return source.buffer.readByteArray(minOf(source.buffer.size, limit))
    }

    private fun decodeHtml(bytes: ByteArray, type: MediaType?): String {
        val charset = type?.charset()
            ?: charsetFromMeta(bytes)
            ?: Charsets.UTF_8
        return String(bytes, charset)
    }

    private fun charsetFromMeta(bytes: ByteArray): Charset? {
        val probe = String(bytes, 0, minOf(2048, bytes.size), Charsets.ISO_8859_1).lowercase()
        val name = Regex("""charset=["']?([a-z0-9._-]+)""").find(probe)?.groupValues?.get(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}

/** Reads top-level string fields out of a small JSON object without a JSON library. */
internal object JsonFields {
    fun string(json: String, key: String): String? {
        val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = pattern.find(json)?.groupValues?.get(1) ?: return null
        return unescape(raw)
    }

    private fun unescape(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) {
                out.append(c); i++; continue
            }
            when (val next = raw[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                    val code = hex.toIntOrNull(16)
                    if (hex.length == 4 && code != null) {
                        out.append(code.toChar()); i += 6; continue
                    }
                    out.append(next)
                }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}

/** Follows redirects of share links such as vm.tiktok.com or pin.it to the real post URL. */
class ShortLinkExpander(private val client: OkHttpClient = defaultClient()) {
    suspend fun expand(raw: String): String = withContext(Dispatchers.IO) {
        if (!isShortLink(raw)) return@withContext raw
        val request = try {
            Request.Builder().url(raw).header("User-Agent", LinkMetadataFetcher.USER_AGENT).build()
        } catch (_: IllegalArgumentException) {
            return@withContext raw
        }
        runCatching {
            client.newCall(request).execute().use { it.request.url.toString() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: raw
    }

    companion object {
        private val hosts = setOf(
            "vm.tiktok.com", "vt.tiktok.com", "fb.watch", "fb.me", "l.facebook.com", "lm.facebook.com",
            "t.co", "pin.it", "instagr.am", "l.instagram.com", "redd.it",
        )

        fun isShortLink(raw: String): Boolean {
            val uri = UrlNormalizer.parse(raw) ?: return false
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
            val path = uri.path.orEmpty()
            return host in hosts ||
                (host == "tiktok.com" && path.startsWith("/t/")) ||
                (host.endsWith("instagram.com") && path.lowercase().contains("/share"))
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }
}
