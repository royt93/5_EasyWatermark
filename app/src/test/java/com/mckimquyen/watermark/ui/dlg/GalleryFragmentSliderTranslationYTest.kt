package com.mckimquyen.watermark.ui.dlg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-AUDIT-2026-09-29: `computeSliderTranslationY()` phải trả về 0f khi `verticalScrollRange` <= 0
 * (list ít ảnh, không cuộn được) thay vì chia cho 0 ra NaN — trước đây `coerceAtLeast(0f)` không
 * clamp được NaN (mọi so sánh với NaN đều false) nên slider dính NaN, lệch vị trí.
 */
class GalleryFragmentSliderTranslationYTest {

    @Test
    fun computeSliderTranslationY_zeroScrollRange_returnsZeroNotNaN() {
        val result = GalleryFragment.computeSliderTranslationY(
            offset = 0,
            verticalScrollRange = 0,
            recyclerViewVisibleHeight = 1000
        )

        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun computeSliderTranslationY_negativeScrollRange_returnsZero() {
        val result = GalleryFragment.computeSliderTranslationY(
            offset = 5,
            verticalScrollRange = -1,
            recyclerViewVisibleHeight = 1000
        )

        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun computeSliderTranslationY_normalRange_computesProportionalValue() {
        val result = GalleryFragment.computeSliderTranslationY(
            offset = 500,
            verticalScrollRange = 2000,
            recyclerViewVisibleHeight = 800
        )

        // 500/2000 * 800 = 200
        assertThat(result).isWithin(0.0001f).of(200f)
    }

    @Test
    fun computeSliderTranslationY_negativeRawResult_clampsToZero() {
        val result = GalleryFragment.computeSliderTranslationY(
            offset = -10,
            verticalScrollRange = 100,
            recyclerViewVisibleHeight = 500
        )

        assertThat(result).isEqualTo(0f)
    }
}
