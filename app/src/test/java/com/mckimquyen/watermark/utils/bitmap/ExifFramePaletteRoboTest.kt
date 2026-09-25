package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-18: [ExifFramePalette.resolve] là hàm thuần nhưng cần Robolectric — `Color.red/green/blue` dưới
 * android.jar stub (`isReturnDefaultValues=true`) trả 0 cho mọi input, làm sai `ColorUtils` (cùng lý do
 * `TextEffectRendererTest`). Màu trên bitmap khung KHÔNG assert được ở đây (Robolectric không rasterize) —
 * xem `ExifBorderAutoPaletteIntegrationTest` (androidTest) cho phần pixel thật.
 */
@RunWith(RobolectricTestRunner::class)
class ExifFramePaletteRoboTest {

    private val darkNavy = Color.rgb(20, 30, 60)
    private val sunnyYellow = Color.rgb(250, 220, 90)

    @Test
    fun resolve_darkDominant_bandIsThatColor_textIsWhite() {
        val colors = ExifFramePalette.resolve(darkNavy, ExifFrameStyle.CLASSIC)

        assertThat(colors.band).isEqualTo(darkNavy)
        assertThat(colors.primaryText).isEqualTo(Color.WHITE)
    }

    @Test
    fun resolve_brightDominant_bandIsThatColor_textIsBlack() {
        val colors = ExifFramePalette.resolve(sunnyYellow, ExifFrameStyle.CLASSIC)

        assertThat(colors.band).isEqualTo(sunnyYellow)
        assertThat(colors.primaryText).isEqualTo(Color.BLACK)
    }

    /** AC: 2 ảnh tông khác biệt rõ → khung khác màu nền VÀ khác màu chữ, không dùng chung bảng cố định. */
    @Test
    fun resolve_darkVsBrightPhoto_produceDifferentBandAndText() {
        val dark = ExifFramePalette.resolve(darkNavy, ExifFrameStyle.POLAROID)
        val bright = ExifFramePalette.resolve(sunnyYellow, ExifFrameStyle.POLAROID)

        assertThat(dark.band).isNotEqualTo(bright.band)
        assertThat(dark.primaryText).isNotEqualTo(bright.primaryText)
    }

    @Test
    fun resolve_everyStyle_primaryAndSecondaryText_meetWcagAa() {
        val samples = listOf(darkNavy, sunnyYellow, Color.rgb(128, 128, 128), Color.rgb(200, 40, 40), Color.rgb(30, 160, 90))
        for (style in ExifFrameStyle.entries) {
            for (dominant in samples) {
                val colors = ExifFramePalette.resolve(dominant, style)
                assertThat(ColorUtils.calculateContrast(colors.primaryText, colors.band))
                    .isAtLeast(ExifFramePalette.MIN_TEXT_CONTRAST)
                assertThat(ColorUtils.calculateContrast(colors.secondaryText, colors.band))
                    .isAtLeast(ExifFramePalette.MIN_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun resolve_translucentDominant_bandForcedOpaque() {
        val colors = ExifFramePalette.resolve(Color.argb(40, 20, 30, 60), ExifFrameStyle.MINIMAL)

        assertThat(Color.alpha(colors.band)).isEqualTo(255)
    }

    /** Ảnh không trích được màu → bảng màu gốc từng style, output y hệt trước IDEA-18. */
    @Test
    fun resolve_nullDominant_returnsStyleDefaults() {
        for (style in ExifFrameStyle.entries) {
            assertThat(ExifFramePalette.resolve(null, style)).isEqualTo(ExifFramePalette.defaultsFor(style))
        }
    }

    @Test
    fun defaultsFor_matchesPreviouslyHardcodedRendererColors() {
        assertThat(ExifFramePalette.defaultsFor(ExifFrameStyle.CLASSIC))
            .isEqualTo(ExifFramePalette.FrameColors(Color.WHITE, Color.BLACK, Color.DKGRAY, Color.LTGRAY))
        assertThat(ExifFramePalette.defaultsFor(ExifFrameStyle.POLAROID))
            .isEqualTo(ExifFramePalette.FrameColors(Color.WHITE, Color.BLACK, Color.DKGRAY, Color.LTGRAY))
        assertThat(ExifFramePalette.defaultsFor(ExifFrameStyle.FILM_STRIP))
            .isEqualTo(ExifFramePalette.FrameColors(Color.BLACK, Color.WHITE, Color.WHITE, Color.WHITE))
        assertThat(ExifFramePalette.defaultsFor(ExifFrameStyle.MINIMAL))
            .isEqualTo(ExifFramePalette.FrameColors(Color.WHITE, Color.DKGRAY, Color.DKGRAY, Color.LTGRAY))
    }
}
