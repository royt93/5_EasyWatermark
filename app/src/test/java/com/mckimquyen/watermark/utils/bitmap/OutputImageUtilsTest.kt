package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
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

    @Test
    fun resizePresets_containsOriginalAsFirstEntry() {
        val first = OutputImageUtils.resizePresets.first()
        assertThat(first.label).isEqualTo("Original")
        assertThat(first.maxLongEdge).isEqualTo(OutputImageUtils.RESIZE_ORIGINAL)
    }

    @Test
    fun resizePresets_containsPlatformPresets() {
        val byLabel = OutputImageUtils.resizePresets.associate { it.label to it.maxLongEdge }
        assertThat(byLabel["Instagram (1080)"]).isEqualTo(1080)
        assertThat(byLabel["Facebook (2048)"]).isEqualTo(2048)
        assertThat(byLabel["Zalo (1600)"]).isEqualTo(1600)
    }

    @Test
    fun resizePresets_keepsLegacyPxPresetsForBackwardCompat() {
        val byLabel = OutputImageUtils.resizePresets.associate { it.label to it.maxLongEdge }
        assertThat(byLabel["1080"]).isEqualTo(1080)
        assertThat(byLabel["2048"]).isEqualTo(2048)
        assertThat(byLabel["4096"]).isEqualTo(4096)
    }

    @Test
    fun resizePresets_neverBreaksAspectRatio() {
        // Mọi preset chỉ giới hạn cạnh dài (targetDimensions không crop) — verify qua 1 ảnh mẫu.
        OutputImageUtils.resizePresets.forEach { preset ->
            val (w, h) = OutputImageUtils.targetDimensions(4000, 3000, preset.maxLongEdge)
            val originalRatio = 4000.0 / 3000.0
            val resultRatio = w.toDouble() / h.toDouble()
            assertThat(resultRatio).isWithin(0.01).of(originalRatio)
        }
    }

    // ── FEAT-07: estimateOutputBytes() — ước tính dung lượng output (heuristic) ────────────

    @Test
    fun estimateOutputBytes_higherQuality_producesLargerEstimate() {
        val low = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, 30)
        val high = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, 95)
        assertThat(high).isGreaterThan(low)
    }

    @Test
    fun estimateOutputBytes_largerDimensions_producesLargerEstimate() {
        val small = OutputImageUtils.estimateOutputBytes(720, 1280, Bitmap.CompressFormat.JPEG, 80)
        val large = OutputImageUtils.estimateOutputBytes(2160, 3840, Bitmap.CompressFormat.JPEG, 80)
        assertThat(large).isGreaterThan(small)
    }

    @Test
    fun estimateOutputBytes_png_ignoresQuality() {
        val q30 = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.PNG, 30)
        val q100 = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.PNG, 100)
        assertThat(q30).isEqualTo(q100)
    }

    @Test
    fun estimateOutputBytes_qualityOutOfRange_isClamped() {
        val negative = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, -50)
        val zero = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, 0)
        assertThat(negative).isEqualTo(zero)

        val over100 = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, 200)
        val hundred = OutputImageUtils.estimateOutputBytes(1080, 1920, Bitmap.CompressFormat.JPEG, 100)
        assertThat(over100).isEqualTo(hundred)
    }

    @Test
    fun estimateOutputBytes_zeroDimensions_neverReturnsBelowFloor() {
        assertThat(OutputImageUtils.estimateOutputBytes(0, 0, Bitmap.CompressFormat.JPEG, 80)).isAtLeast(1024L)
    }
}
