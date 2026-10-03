package com.mckimquyen.watermark.utils.bitmap

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test

/**
 * FEAT-28: [CardFrameRenderer.computeLayout] là hàm thuần (chỉ tính số) nên test được bằng JUnit
 * thường — không dùng Rect/RectF (bị stub rỗng với returnDefaultValues). Pixel thật nằm ở
 * CardFrameRendererRoboTest/CardFramePixelIntegrationTest.
 */
class CardFrameRendererLayoutTest {

    private companion object {
        const val W = 1000
        const val H = 600
        const val SHORT = 600
    }

    @Test
    fun canvas_isSourcePlusPaddingOnBothSides() {
        val l = CardFrameRenderer.computeLayout(W, H, 0.06f, 0.03f)
        assertThat(l.canvasWidth).isEqualTo(W + l.padding * 2)
        assertThat(l.canvasHeight).isEqualTo(H + l.padding * 2)
    }

    @Test
    fun radius_isPercentOfShortSide() {
        val l = CardFrameRenderer.computeLayout(W, H, 0.10f, 0f)
        assertThat(l.cornerRadiusPx).isWithin(0.01f).of(SHORT * 0.10f)
    }

    @Test
    fun radius_neverExceedsHalfShortSide_evenWithOutOfRangeInput() {
        val l = CardFrameRenderer.computeLayout(W, H, 5f, 0f)
        assertThat(l.cornerRadiusPx).isAtMost(SHORT / 2f)
    }

    @Test
    fun zeroShadow_hasNoBlur_butStillKeepsMinimumPadding() {
        val l = CardFrameRenderer.computeLayout(W, H, 0.06f, 0f)
        assertThat(l.shadowBlurPx).isEqualTo(0f)
        assertThat(l.padding).isAtLeast(1)
    }

    @Test
    fun bigShadow_growsPadding_soShadowIsNotClipped() {
        val small = CardFrameRenderer.computeLayout(W, H, 0.06f, 0.01f)
        val big = CardFrameRenderer.computeLayout(W, H, 0.06f, WaterMarkRepository.MAX_CARD_SHADOW_PERCENT)
        assertThat(big.padding).isGreaterThan(small.padding)
        // padding phải chứa trọn blur ở cả 2 phía + độ lệch xuống dưới
        assertThat(big.padding.toFloat()).isAtLeast(big.shadowBlurPx * 2f + big.shadowOffsetY)
    }

    @Test
    fun shadowPercent_isClampedToMax() {
        val clamped = CardFrameRenderer.computeLayout(W, H, 0.06f, 9f)
        val max = CardFrameRenderer.computeLayout(W, H, 0.06f, WaterMarkRepository.MAX_CARD_SHADOW_PERCENT)
        assertThat(clamped.padding).isEqualTo(max.padding)
    }

    @Test
    fun negativeInputs_areClampedToZero_notNegativePadding() {
        val l = CardFrameRenderer.computeLayout(W, H, -1f, -1f)
        assertThat(l.cornerRadiusPx).isEqualTo(0f)
        assertThat(l.shadowBlurPx).isEqualTo(0f)
        assertThat(l.padding).isAtLeast(1)
    }

    @Test
    fun degenerateOnePixelSource_doesNotCrashOrDivideByZero() {
        val l = CardFrameRenderer.computeLayout(1, 1, 0.5f, 0.1f)
        assertThat(l.canvasWidth).isAtLeast(1)
        assertThat(l.cornerRadiusPx).isAtMost(0.5f)
    }
}
