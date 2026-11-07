package com.ich.socialsave.core

import java.net.URI

/** HTML helpers for finding a post's title and preview image. */
object SocialPreviewParser {
    private val shortcodePattern = Regex("^[A-Za-z0-9_-]{5,}$")

    fun instagramShortcode(url: String): String? {
        val path = UrlNormalizer.parse(url)?.path ?: return null
        val parts = path.split('/').filter { it.isNotEmpty() }
        val markers = setOf("p", "reel", "reels", "tv", "share")
        for ((index, part) in parts.withIndex()) {
            if (part.lowercase() !in markers || index + 1 >= parts.size) continue
            var code = parts[index + 1]
            if ((code.lowercase() == "reel" || code.lowercase() == "p") && index + 2 < parts.size) {
                code = parts[index + 2]
            }
            if (shortcodePattern.matches(code)) return code
        }
        return null
    }

    /** First non-empty `content` of a `<meta>` tag named [key], in document order. */
    fun metaContent(html: String, key: String): String? = metaContents(html, key).firstOrNull()

    /** Every non-empty `content` of `<meta>` tags named [key], in document order, whichever attribute comes first. */
    fun metaContents(html: String, key: String): List<String> {
        val k = Regex.escape(key)
        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name|itemprop)=["']$k["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+(?:property|name|itemprop)=["']$k["']""", RegexOption.IGNORE_CASE),
        )
        return patterns
            .flatMap { pattern -> pattern.findAll(html).map { it.range.first to it.groupValues[1].trim() } }
            .filter { it.second.isNotEmpty() }
            .sortedBy { it.first }
            .map { it.second }
    }

    fun documentTitle(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)

    fun imageCandidates(html: String, pageUrl: String): List<String> {
        val urls = LinkedHashSet<String>()
        fun append(raw: String?) {
            normalizedImageUrl(raw, pageUrl)?.let(urls::add)
        }

        for (key in listOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")) {
            metaContents(html, key).forEach(::append)
        }

        val jsonKeys = "display_url|thumbnail_src|thumbnail_url|video_thumbnail|poster_url"
        Regex(""""(?:$jsonKeys)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { append(it.groupValues[1]) }

        Regex("""<link[^>]+rel=["']preload["'][^>]+as=["']image["'][^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { append(it.groupValues[1]) }

        return urls.toList()
    }

    fun normalizedImageUrl(raw: String?, pageUrl: String): String? {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        text = text.replace("\\u0026", "&").replace("\\/", "/")
        text = TitleSanitizer.decodeHtmlEntities(text)
        text = text.trim('"', '\'')

        if (text.startsWith("//")) {
            text = "https:$text"
        } else if (text.startsWith("/")) {
            val page = UrlNormalizer.parse(pageUrl)
            val authority = page?.rawAuthority ?: return null
            text = "${page.scheme ?: "https"}://$authority$text"
        }

        val uri = UrlNormalizer.parse(text) ?: return null
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrEmpty()) return null
        if (isGenericLogo(text)) return null
        return uri.toString()
    }

    fun looksLikeHtml(data: ByteArray): Boolean {
        val prefix = String(data, 0, minOf(64, data.size), Charsets.UTF_8).trim().lowercase()
        return prefix.startsWith("<!doctype") || prefix.startsWith("<html")
    }

    private fun isGenericLogo(url: String): Boolean {
        val value = url.lowercase()
        return value.contains("/static/images/") ||
            value.contains("instagram.com/static/") ||
            value.contains("facebook.com/images/fb_icon") ||
            value.contains("tiktok.com/obj/tiktok-web-static")
    }

    internal fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (_: Exception) {
        null
    }
}
