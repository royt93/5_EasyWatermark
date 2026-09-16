package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-25: `obtainSealedClass()` phải fallback về giá trị mặc định cho ordinal không hợp lệ
 * (DataStore hỏng/restore từ version khác), không được `throw` — exception ném từ đây nằm SAU
 * `.catch` trong `WaterMarkRepository.waterMark` nên không bao giờ bị bắt, văng thẳng ra collector.
 */
class TextPaintStyleTest {

    @Test
    fun obtainSealedClass_validKeys_returnsMatchingInstance() {
        assertThat(TextPaintStyle.obtainSealedClass(0)).isEqualTo(TextPaintStyle.Fill)
        assertThat(TextPaintStyle.obtainSealedClass(1)).isEqualTo(TextPaintStyle.Stroke)
    }

    @Test
    fun obtainSealedClass_outOfRangeKey_fallsBackToFill_doesNotThrow() {
        assertThat(TextPaintStyle.obtainSealedClass(999)).isEqualTo(TextPaintStyle.Fill)
        assertThat(TextPaintStyle.obtainSealedClass(-1)).isEqualTo(TextPaintStyle.Fill)
    }
}
