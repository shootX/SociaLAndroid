package com.shoto.socialsave.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {
    @Test fun detectsNetworks() {
        assertEquals(SocialPlatform.INSTAGRAM, PlatformDetector.detect("https://www.instagram.com/p/abc"))
        assertEquals(SocialPlatform.TIKTOK, PlatformDetector.detect("https://vm.tiktok.com/x"))
        assertEquals(SocialPlatform.FACEBOOK, PlatformDetector.detect("https://fb.watch/x"))
        assertEquals(SocialPlatform.YOUTUBE, PlatformDetector.detect("https://youtu.be/x"))
        assertEquals(SocialPlatform.X, PlatformDetector.detect("https://twitter.com/a/status/1"))
        assertEquals(SocialPlatform.THREADS, PlatformDetector.detect("https://www.threads.net/@a"))
        assertEquals(SocialPlatform.PINTEREST, PlatformDetector.detect("https://pin.it/x"))
        assertEquals(SocialPlatform.REDDIT, PlatformDetector.detect("https://old.reddit.com/r/a"))
        assertEquals(SocialPlatform.OTHER, PlatformDetector.detect("https://notinstagram.com/p/abc"))
    }

    @Test fun rawValuesMatchIos() {
        assertEquals(SocialPlatform.X, SocialPlatform.fromRaw("x"))
        assertEquals(SocialPlatform.OTHER, SocialPlatform.fromRaw("myspace"))
    }
}
