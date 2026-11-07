package com.ich.socialsave.core

/** Networks SocialSave recognises. [rawValue] is what the database stores, identical to the iOS app. */
enum class SocialPlatform(val rawValue: String, val displayName: String) {
    INSTAGRAM("instagram", "Instagram"),
    TIKTOK("tiktok", "TikTok"),
    FACEBOOK("facebook", "Facebook"),
    YOUTUBE("youtube", "YouTube"),
    X("x", "X"),
    THREADS("threads", "Threads"),
    PINTEREST("pinterest", "Pinterest"),
    REDDIT("reddit", "Reddit"),
    OTHER("other", "Other");

    companion object {
        fun fromRaw(raw: String?): SocialPlatform = entries.firstOrNull { it.rawValue == raw } ?: OTHER
    }
}

object PlatformDetector {
    fun detect(rawUrl: String): SocialPlatform {
        val host = UrlNormalizer.hostOf(rawUrl) ?: return SocialPlatform.OTHER
        return detectHost(host)
    }

    fun detectHost(rawHost: String): SocialPlatform {
        val host = rawHost.lowercase()
        fun m(domain: String) = host == domain || host.endsWith(".$domain")
        return when {
            m("instagram.com") || m("instagr.am") -> SocialPlatform.INSTAGRAM
            m("tiktok.com") -> SocialPlatform.TIKTOK
            m("facebook.com") || m("fb.watch") || m("fb.me") || m("fb.com") -> SocialPlatform.FACEBOOK
            m("youtube.com") || m("youtu.be") || m("youtube-nocookie.com") -> SocialPlatform.YOUTUBE
            m("x.com") || m("twitter.com") -> SocialPlatform.X
            m("threads.net") || m("threads.com") -> SocialPlatform.THREADS
            m("pinterest.com") || m("pin.it") -> SocialPlatform.PINTEREST
            m("reddit.com") || m("redd.it") -> SocialPlatform.REDDIT
            else -> SocialPlatform.OTHER
        }
    }
}
