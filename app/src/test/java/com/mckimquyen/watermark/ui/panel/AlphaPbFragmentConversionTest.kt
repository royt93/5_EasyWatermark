package com.mckimquyen.watermark.ui.panel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** ENH-48: slider/native alpha conversions round instead of losing a percent by truncation. */
class AlphaPbFragmentConversionTest {

    @Test
    fun sliderPercentToNativeAlpha_roundsExpectedValuesAndClamps() {
        assertThat(AlphaPbFragment.alphaPercentToNative(0f)).isEqualTo(0)
        assertThat(AlphaPbFragment.alphaPercentToNative(1f)).isEqualTo(3)
        assertThat(AlphaPbFragment.alphaPercentToNative(50f)).isEqualTo(128)
        assertThat(AlphaPbFragment.alphaPercentToNative(99f)).isEqualTo(252)
        assertThat(AlphaPbFragment.alphaPercentToNative(100f)).isEqualTo(255)
        assertThat(AlphaPbFragment.alphaPercentToNative(-1f)).isEqualTo(0)
        assertThat(AlphaPbFragment.alphaPercentToNative(101f)).isEqualTo(255)
    }

    @Test
    fun nativeAlphaToSliderPercent_roundsExpectedValuesAndClamps() {
        assertThat(AlphaPbFragment.nativeAlphaToPercent(0)).isEqualTo(0)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(1)).isEqualTo(0)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(128)).isEqualTo(50)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(252)).isEqualTo(99)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(255)).isEqualTo(100)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(-1)).isEqualTo(0)
        assertThat(AlphaPbFragment.nativeAlphaToPercent(256)).isEqualTo(100)
    }

    @Test
    fun percentRoundTrip_hasAtMostOnePointQuantizationError() {
        SLIDER_PERCENTAGES.forEach { percentage ->
            val nativeAlpha = AlphaPbFragment.alphaPercentToNative(percentage.toFloat())
            val restoredPercentage = AlphaPbFragment.nativeAlphaToPercent(nativeAlpha)

            assertThat(kotlin.math.abs(restoredPercentage - percentage)).isAtMost(MAX_ROUND_TRIP_ERROR_PERCENT)
        }
    }

    private companion object {
        val SLIDER_PERCENTAGES = 0..100
        const val MAX_ROUND_TRIP_ERROR_PERCENT = 1
    }
}
