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
}
