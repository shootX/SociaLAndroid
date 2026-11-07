package com.ich.socialsave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleSanitizerTest {
    @Test fun decodesEntitiesTagsAndWhitespace() {
        assertEquals("Tom & Jerry’s “best”", TitleSanitizer.cleaned("  <b>Tom &amp;amp; Jerry&rsquo;s</b>\n &#8220;best&#x201D; "))
    }

    @Test fun decodesJsonEscapes() {
        assertEquals("Café", TitleSanitizer.cleaned("Caf\\u00e9"))
    }

    @Test fun repairsMojibake() {
        assertEquals("Café", TitleSanitizer.cleaned("CafÃ©"))
    }

    @Test fun rejectsUrlsAndGarbage() {
        assertNull(TitleSanitizer.cleaned("https://instagram.com/p/x"))
        assertNull(TitleSanitizer.cleaned("12345678 ---"))
        assertNull(TitleSanitizer.cleaned("   "))
    }

    @Test fun keepsEmojiOnlyTitles() {
        assertEquals("🔥🔥🔥🔥 🔥🔥🔥🔥", TitleSanitizer.cleaned("🔥🔥🔥🔥 🔥🔥🔥🔥"))
    }
}
