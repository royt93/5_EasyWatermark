package com.mckimquyen.watermark.ui.dlg

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-29: `computeSliderScrollPercent()` phải dùng phép chia Float — Int/Int mất hoàn toàn phần
 * thập phân (luôn 0 hoặc số nguyên thô), khiến kéo slider tuỳ biến không cuộn hết list/nhảy sai
 * vị trí. Test dùng giá trị mà Int/Int trả về 0 hoặc sai lệch rõ rệt so với kết quả đúng.
 */
class GalleryFragmentSliderScrollPercentTest {

    @Test
    fun computeSliderScrollPercent_scrollRangeSmallerThanTotalHeight_returnsFractionalValue() {
        // Int/Int (3000 / 4000 = 0) sẽ làm slider không bao giờ cuộn được — phải trả về 0.75f.
        val percent = GalleryFragment.computeSliderScrollPercent(scrollRange = 3000, totalHeight = 4000)

        assertThat(percent).isWithin(0.0001f).of(0.75f)
    }

    @Test
    fun computeSliderScrollPercent_scrollRangeLargerThanTotalHeight_returnsValueGreaterThanOne() {
        val percent = GalleryFragment.computeSliderScrollPercent(scrollRange = 10000, totalHeight = 4000)

        assertThat(percent).isWithin(0.0001f).of(2.5f)
    }

    @Test
    fun computeSliderScrollPercent_equalValues_returnsOne() {
        val percent = GalleryFragment.computeSliderScrollPercent(scrollRange = 5000, totalHeight = 5000)

        assertThat(percent).isWithin(0.0001f).of(1.0f)
    }
}
