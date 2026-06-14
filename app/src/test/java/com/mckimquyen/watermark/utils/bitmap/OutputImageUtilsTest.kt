package com.mckimquyen.watermark.utils.bitmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho phần tính kích thước resize của [OutputImageUtils]
 * (không phụ thuộc Android nên chạy trực tiếp trên JVM).
 */
class OutputImageUtilsTest {

    @Test
    fun targetDimensions_zeroLimit_keepsOriginal() {
        assertThat(OutputImageUtils.targetDimensions(4000, 3000, 0)).isEqualTo(4000 to 3000)
    }

    @Test
    fun targetDimensions_alreadySmaller_keepsOriginal() {
        assertThat(OutputImageUtils.targetDimensions(800, 600, 1080)).isEqualTo(800 to 600)
    }

    @Test
    fun targetDimensions_landscape_scalesByLongEdge() {
        // cạnh dài 4000 -> 2000 (scale 0.5)
        assertThat(OutputImageUtils.targetDimensions(4000, 3000, 2000)).isEqualTo(2000 to 1500)
    }

    @Test
    fun targetDimensions_portrait_scalesByLongEdge() {
        assertThat(OutputImageUtils.targetDimensions(3000, 4000, 2000)).isEqualTo(1500 to 2000)
    }

    @Test
    fun targetDimensions_keepsAtLeastOnePixel() {
        val (w, h) = OutputImageUtils.targetDimensions(1000, 1, 10)
        assertThat(w).isEqualTo(10)
        assertThat(h).isAtLeast(1)
    }

    @Test
    fun targetDimensions_invalidInput_returnsAsIs() {
        assertThat(OutputImageUtils.targetDimensions(0, 0, 100)).isEqualTo(0 to 0)
    }
}
