package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-25: `obtainSealedClass()` phải fallback về giá trị mặc định cho ordinal không hợp lệ
 * (DataStore hỏng/restore từ version khác), không được `throw` — exception ném từ đây nằm SAU
 * `.catch` trong `WaterMarkRepository.waterMark` nên không bao giờ bị bắt, văng thẳng ra collector.
 */
class TextTypefaceTest {

    @Test
    fun obtainSealedClass_validKeys_returnsMatchingInstance() {
        assertThat(TextTypeface.obtainSealedClass(0)).isEqualTo(TextTypeface.Normal)
        assertThat(TextTypeface.obtainSealedClass(1)).isEqualTo(TextTypeface.Italic)
        assertThat(TextTypeface.obtainSealedClass(2)).isEqualTo(TextTypeface.Bold)
        assertThat(TextTypeface.obtainSealedClass(3)).isEqualTo(TextTypeface.BoldItalic)
    }

    @Test
    fun obtainSealedClass_outOfRangeKey_fallsBackToNormal_doesNotThrow() {
        assertThat(TextTypeface.obtainSealedClass(999)).isEqualTo(TextTypeface.Normal)
        assertThat(TextTypeface.obtainSealedClass(-1)).isEqualTo(TextTypeface.Normal)
    }
}
