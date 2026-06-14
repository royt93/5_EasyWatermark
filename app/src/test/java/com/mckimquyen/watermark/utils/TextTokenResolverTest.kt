package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho [TextTokenResolver] — thay token động trong text watermark.
 */
class TextTokenResolverTest {

    @Test
    fun resolve_noBraces_returnsInputUnchanged() {
        val text = "© My Studio 2026"
        assertThat(TextTokenResolver.resolve(text, mapOf("date" to "2026"))).isEqualTo(text)
    }

    @Test
    fun resolve_replacesKnownTokens() {
        val text = "{filename} #{seq} - {date}"
        val tokens = mapOf(
            "filename" to "IMG_001",
            "seq" to "3",
            "date" to "2026-06-14"
        )
        assertThat(TextTokenResolver.resolve(text, tokens)).isEqualTo("IMG_001 #3 - 2026-06-14")
    }

    @Test
    fun resolve_nullValue_becomesEmpty() {
        assertThat(TextTokenResolver.resolve("[{iso}]", mapOf("iso" to null))).isEqualTo("[]")
    }

    @Test
    fun resolve_unknownToken_isLeftAsIs() {
        assertThat(TextTokenResolver.resolve("{unknown}", mapOf("date" to "x"))).isEqualTo("{unknown}")
    }

    @Test
    fun resolve_repeatedToken_replacesAllOccurrences() {
        assertThat(TextTokenResolver.resolve("{seq}-{seq}", mapOf("seq" to "7"))).isEqualTo("7-7")
    }

    @Test
    fun supportedTokens_containsExpectedSet() {
        assertThat(TextTokenResolver.SUPPORTED_TOKENS)
            .containsExactly(
                "filename", "seq", "date", "model", "make",
                "iso", "fnumber", "exposure", "focal", "exif"
            )
    }
}
