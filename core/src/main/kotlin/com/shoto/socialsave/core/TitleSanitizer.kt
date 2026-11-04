package com.shoto.socialsave.core

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Cleans titles scraped from pages: entities, escapes, tags, mojibake, and obvious garbage. */
object TitleSanitizer {
    fun cleaned(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        text = decodeJsonEscapes(text)
        text = text.replace(Regex("<[^>]+>"), " ")
        text = decodeHtmlEntities(text)
        text = repairMojibake(text)
        text = text.replace(Regex("\\s+"), " ").trim()

        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        if (isGarbage(text)) return null
        return text
    }

    fun decodeHtmlEntities(raw: String): String {
        var text = raw
        repeat(3) {
            val decoded = decodeEntitiesOnce(text)
            if (decoded == text) return text
            text = decoded
        }
        return text
    }

    private val entityPattern = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

    private fun decodeEntitiesOnce(text: String): String = entityPattern.replace(text) { match ->
        val inner = match.groupValues[1]
        when {
            inner.startsWith("#x") || inner.startsWith("#X") ->
                inner.drop(2).toIntOrNull(16)?.let(::codePointString) ?: match.value
            inner.startsWith("#") ->
                inner.drop(1).toIntOrNull()?.let(::codePointString) ?: match.value
            else -> namedEntities[inner] ?: match.value
        }
    }

    private fun codePointString(value: Int): String? =
        if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null

    private val unicodeEscape = Regex("\\\\u([0-9a-fA-F]{4})")

    private fun decodeJsonEscapes(text: String): String {
        if (!text.contains("\\u")) return text
        return unicodeEscape.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
    }

    private val mojibakeMarkers = listOf("Ã", "Â", "Ð", "ðŸ", "â€", "ï¿½")

    private fun repairMojibake(text: String): String {
        if (mojibakeMarkers.none { text.contains(it) }) return text
        if (text.any { it.code > 0xFF }) return text
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val repaired = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            return text
        }
        return if (repaired.contains('�') || repaired == text) text else repaired
    }

    private fun isGarbage(text: String): Boolean {
        if (text.count { it == '�' } >= 2) return true
        val letters = text.count { it.isLetter() }
        val emoji = text.codePoints().filter(::isEmoji).count()
        val length = text.codePointCount(0, text.length)
        return length >= 8 && letters == 0 && emoji == 0L
    }

    private fun isEmoji(codePoint: Int): Boolean =
        codePoint >= 0x1F000 ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x2300..0x23FF ||
            codePoint in 0x2B00..0x2BFF ||
            Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "copy" to "©", "reg" to "®", "trade" to "™",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "sbquo" to "‚", "bdquo" to "„",
    )
}
