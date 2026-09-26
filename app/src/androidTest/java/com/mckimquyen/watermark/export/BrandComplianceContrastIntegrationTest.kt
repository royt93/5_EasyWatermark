package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.palette.graphics.Palette
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-15 integration test trên thiết bị thật (Skia rasterize pixel thật để Palette đo màu):
 * Kiểm tra đo độ tương phản giữa màu watermark và màu nền thật:
 * - Bitmap trắng (255,255,255) + chữ trắng (255,255,255) → tương phản 1:1 < 1.5 → LOW_CONTRAST FAIL.
 * - Bitmap đen (0,0,0) + chữ trắng (255,255,255) → tương phản 21:1 > 3.0 → PASS.
 */
@RunWith(AndroidJUnit4::class)
class BrandComplianceContrastIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun solidBitmap(color: Int): Bitmap {
        return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(color)
        }
    }

    @Test
    fun whiteTextOnWhiteBackground_failsComplianceWithLowContrast() {
        val bitmap = solidBitmap(Color.WHITE)
        val palette = Palette.from(bitmap).generate()
        val bgRgb = palette.dominantSwatch?.rgb ?: Color.WHITE
        val contrast = androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, bgRgb)

        val safeRect = BrandComplianceScorer.NormalizedBox(0.1f, 0.1f, 0.4f, 0.2f)
        val result = BrandComplianceScorer.evaluate(
            watermarkRect = safeRect,
            alpha = 255,
            contrastRatio = contrast,
            faces = emptyList(),
            layoutMode = BrandComplianceScorer.LayoutMode.SINGLE
        )

        assertThat(contrast).isLessThan(BrandComplianceScorer.CONTRAST_FAIL)
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(result.issues).contains(BrandComplianceScorer.Issue.LOW_CONTRAST)
        bitmap.recycle()
    }

    @Test
    fun whiteTextOnBlackBackground_passesCompliance() {
        val bitmap = solidBitmap(Color.BLACK)
        val palette = Palette.from(bitmap).generate()
        val bgRgb = palette.dominantSwatch?.rgb ?: Color.BLACK
        val contrast = androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, bgRgb)

        val safeRect = BrandComplianceScorer.NormalizedBox(0.1f, 0.1f, 0.4f, 0.2f)
        val result = BrandComplianceScorer.evaluate(
            watermarkRect = safeRect,
            alpha = 255,
            contrastRatio = contrast,
            faces = emptyList(),
            layoutMode = BrandComplianceScorer.LayoutMode.SINGLE
        )

        assertThat(contrast).isAtLeast(BrandComplianceScorer.CONTRAST_WARN)
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.PASS)
        assertThat(result.issues).isEmpty()
        bitmap.recycle()
    }
}
