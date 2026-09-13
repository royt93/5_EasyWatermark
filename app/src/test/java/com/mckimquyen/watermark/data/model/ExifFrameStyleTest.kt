package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho [ExifFrameStyle.obtain] — round-trip ordinal cho mọi giá trị
 * enum + fallback an toàn khi DataStore chứa ordinal không hợp lệ (dữ liệu cũ/hỏng).
 */
class ExifFrameStyleTest {

    @Test
    fun obtain_validOrdinal_returnsMatchingEntry() {
        ExifFrameStyle.entries.forEachIndexed { index, style ->
            assertThat(ExifFrameStyle.obtain(index)).isEqualTo(style)
        }
    }

    @Test
    fun obtain_outOfBoundsOrdinal_fallsBackToClassic() {
        assertThat(ExifFrameStyle.obtain(99)).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun obtain_negativeOrdinal_fallsBackToClassic() {
        assertThat(ExifFrameStyle.obtain(-1)).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun entries_hasExactlyFourStyles_noLogoBasedStyle() {
        // Quyết định sản phẩm: không dùng logo hãng máy thật (rủi ro trademark) — chỉ 4 style
        // vẽ bằng Canvas thuần.
        assertThat(ExifFrameStyle.entries).containsExactly(
            ExifFrameStyle.CLASSIC,
            ExifFrameStyle.POLAROID,
            ExifFrameStyle.FILM_STRIP,
            ExifFrameStyle.MINIMAL
        )
    }

    // ── FEAT-10: suggestFor(make) — gợi ý style theo hãng máy đọc từ EXIF ──────────────

    @Test
    fun suggestFor_emptyMake_fallsBackToMinimal() {
        // Đúng ví dụ AC gốc: "máy không rõ hãng gợi ý Minimal".
        assertThat(ExifFrameStyle.suggestFor("")).isEqualTo(ExifFrameStyle.MINIMAL)
    }

    @Test
    fun suggestFor_blankMake_fallsBackToMinimal() {
        assertThat(ExifFrameStyle.suggestFor("   ")).isEqualTo(ExifFrameStyle.MINIMAL)
    }

    @Test
    fun suggestFor_fujifilm_suggestsClassic() {
        // Đúng ví dụ AC gốc: "máy Fujifilm/Leica gợi ý Classic".
        assertThat(ExifFrameStyle.suggestFor("FUJIFILM")).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun suggestFor_leica_suggestsClassic() {
        assertThat(ExifFrameStyle.suggestFor("LEICA CAMERA AG")).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun suggestFor_isCaseInsensitive() {
        assertThat(ExifFrameStyle.suggestFor("leica")).isEqualTo(ExifFrameStyle.CLASSIC)
        assertThat(ExifFrameStyle.suggestFor("Fujifilm")).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun suggestFor_trimsWhitespace() {
        assertThat(ExifFrameStyle.suggestFor("  LEICA  ")).isEqualTo(ExifFrameStyle.CLASSIC)
    }

    @Test
    fun suggestFor_polaroidBrand_suggestsPolaroid() {
        assertThat(ExifFrameStyle.suggestFor("Polaroid")).isEqualTo(ExifFrameStyle.POLAROID)
    }

    @Test
    fun suggestFor_kodak_suggestsPolaroid() {
        assertThat(ExifFrameStyle.suggestFor("Eastman Kodak Company")).isEqualTo(ExifFrameStyle.POLAROID)
    }

    @Test
    fun suggestFor_dslrBrands_suggestFilmStrip() {
        assertThat(ExifFrameStyle.suggestFor("Canon")).isEqualTo(ExifFrameStyle.FILM_STRIP)
        assertThat(ExifFrameStyle.suggestFor("NIKON CORPORATION")).isEqualTo(ExifFrameStyle.FILM_STRIP)
        assertThat(ExifFrameStyle.suggestFor("SONY")).isEqualTo(ExifFrameStyle.FILM_STRIP)
    }

    @Test
    fun suggestFor_unrecognizedBrand_fallsBackToMinimal() {
        // Điện thoại/hãng không nằm trong danh sách nhận diện (vd Apple, Samsung) — vẫn coi là
        // "không rõ hãng" theo đúng phạm vi AC, không cần liệt kê từng hãng điện thoại.
        assertThat(ExifFrameStyle.suggestFor("Apple")).isEqualTo(ExifFrameStyle.MINIMAL)
        assertThat(ExifFrameStyle.suggestFor("samsung")).isEqualTo(ExifFrameStyle.MINIMAL)
        assertThat(ExifFrameStyle.suggestFor("Xiaomi")).isEqualTo(ExifFrameStyle.MINIMAL)
    }
}
