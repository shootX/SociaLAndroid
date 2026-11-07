package com.shoto.socialsave.core

import java.net.URI
import java.net.URLDecoder

/** Same rules as the iOS URLNormalizer, so a link saved on either platform gets the same canonical form. */
object UrlNormalizer {
    private val trackingParameters = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "fb_action_ids", "fb_action_types", "fb_source",
        "gclid", "gclsrc", "dclid", "gbraid", "wbraid",
        "ttclid", "tt_from",
        "igsh", "igshid", "ig_rid",
        "mibextid", "mc_cid", "mc_eid",
        "ref_src", "ref_url",
        "si", "share_app_id", "share_id",
        "twclid", "yclid", "_r",
    )

    private val socialDomains = listOf(
        "youtube.com", "facebook.com", "x.com", "twitter.com", "instagram.com",
        "threads.net", "threads.com", "tiktok.com", "pinterest.com", "reddit.com",
    )

    /** Query keys kept on social hosts; any other social domain keeps none. */
    private val socialQueryAllowlist = mapOf(
        "youtube.com" to setOf("v"),
        "facebook.com" to setOf("v", "story_fbid", "id", "fbid"),
    )

    private val linkPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"'`]+""")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}>»\"'”’…"

    /** Canonical form of the first web link in [raw], or null when there is none. */
    fun canonicalUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.none { it.isWhitespace() }) canonicalize(trimmed)?.let { return it }
        val extracted = firstHttpUrl(trimmed) ?: return null
        return canonicalize(extracted)
    }

    /** The first http(s) link in free text, such as a caption shared together with its link. */
    fun firstHttpUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.none { it.isWhitespace() } && canonicalize(trimmed) != null) return parse(trimmed)?.toString()

        for (match in linkPattern.findAll(trimmed)) {
            var candidate = match.value.trimEnd { it in TRAILING_PUNCTUATION }
            if (candidate.startsWith("www.", ignoreCase = true)) candidate = "http://$candidate"
            if (canonicalize(candidate) != null) return parse(candidate)?.toString()
        }
        return null
    }

    fun hostOf(raw: String): String? = parse(raw.trim())?.host?.lowercase()

    fun canonicalize(raw: String): String? {
        val uri = parse(raw) ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        var host = uri.host?.lowercase() ?: return null
        if (host.isEmpty()) return null
        if (host.startsWith("www.")) host = host.removePrefix("www.")

        var path = uri.rawPath.orEmpty()
        if (path.length > 1 && path.endsWith("/")) path = path.dropLast(1)

        var items = uri.rawQuery?.split('&').orEmpty()
        socialDomains.firstOrNull { host == "m.$it" || host == "mobile.$it" }?.let { host = it }
        if (host == "twitter.com") host = "x.com"
        if (host == "youtu.be" && path.length > 1 && path.indexOf('/', 1) == -1) {
            items = items + "v=${path.substring(1)}"
            host = "youtube.com"
            path = "/watch"
        }
        val social = socialDomains.firstOrNull { host == it || host.endsWith(".$it") }
        val allowed = social?.let { socialQueryAllowlist[it].orEmpty() }

        val query = items
            .filter { it.isNotEmpty() }
            .map { it to decode(it.substringBefore('=')).lowercase() }
            .filter { (_, key) -> key !in trackingParameters && (allowed == null || key in allowed) }
            .sortedBy { it.second }
            .map { it.first }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("&")

        return buildString {
            append(scheme).append("://").append(host)
            if (uri.port != -1) append(':').append(uri.port)
            append(path)
            if (query != null) append('?').append(query)
        }
    }

    /** Parses leniently: characters URI rejects (spaces, unicode, `|`…) are percent-encoded first, like iOS 17 does. */
    internal fun parse(raw: String): URI? {
        if (raw.isEmpty()) return null
        return try {
            URI(raw)
        } catch (_: Exception) {
            try {
                URI(encodeIllegal(raw))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun encodeIllegal(raw: String): String {
        val allowed = "-._~:/?#[]@!$&'()*+,;=%"
        val out = StringBuilder()
        for (char in raw) {
            if ((char in 'a'..'z') || (char in 'A'..'Z') || (char in '0'..'9') || allowed.indexOf(char) >= 0) {
                out.append(char)
            } else {
                for (byte in char.toString().toByteArray(Charsets.UTF_8)) {
                    out.append('%').append(String.format("%02X", byte.toInt() and 0xFF))
                }
            }
        }
        return out.toString()
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
