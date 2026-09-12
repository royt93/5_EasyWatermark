package com.mckimquyen.watermark.utils

import android.graphics.Color
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-11: [TextEffectRenderer] là hàm thuần (không cần Canvas thật) nhưng vẫn cần
 * `RobolectricTestRunner` — `Color.red/green/blue/alpha()` là method call thật (không phải hằng
 * số), dưới android.jar stub JVM thuần (`isReturnDefaultValues=true`) sẽ trả về 0 cho MỌI input,
 * làm sai lệch `ColorUtils.calculateLuminance` bên trong [TextEffectRenderer.contrastingColor].
 */
@RunWith(RobolectricTestRunner::class)
class TextEffectRendererTest {

    private fun config(
        textColor: Int = Color.WHITE,
        stroke: Boolean = false,
        shadow: Boolean = false,
        pill: Boolean = false
    ) = WaterMark(
        text = "Watermark",
        textSize = 40f,
        textColor = textColor,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = Uri.EMPTY,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false,
        textEffectStroke = stroke,
        textEffectShadow = shadow,
        textEffectPillBackground = pill
    )

    @Test
    fun contrastingColor_lightText_returnsBlack() {
        val color = TextEffectRenderer.contrastingColor(Color.WHITE, 255)

        assertThat(Color.red(color)).isEqualTo(0)
        assertThat(Color.green(color)).isEqualTo(0)
        assertThat(Color.blue(color)).isEqualTo(0)
    }

    @Test
    fun contrastingColor_darkText_returnsWhite() {
        val color = TextEffectRenderer.contrastingColor(Color.BLACK, 255)

        assertThat(Color.red(color)).isEqualTo(255)
        assertThat(Color.green(color)).isEqualTo(255)
        assertThat(Color.blue(color)).isEqualTo(255)
    }

    @Test
    fun contrastingColor_appliesRequestedAlpha() {
        val color = TextEffectRenderer.contrastingColor(Color.WHITE, 140)

        assertThat(Color.alpha(color)).isEqualTo(140)
    }

    @Test
    fun marginPx_allEffectsDisabled_isZero() {
        val margin = TextEffectRenderer.marginPx(config())

        assertThat(margin).isEqualTo(0f)
    }

    @Test
    fun marginPx_strokeOnly_equalsStrokeWidth() {
        val cfg = config(stroke = true)

        val margin = TextEffectRenderer.marginPx(cfg)

        assertThat(margin).isEqualTo(TextEffectRenderer.strokeWidthPx(cfg.textSize))
    }

    @Test
    fun marginPx_pillOnly_equalsLargerOfHorizontalVerticalPadding() {
        val cfg = config(pill = true)

        val margin = TextEffectRenderer.marginPx(cfg)

        val expected = maxOf(TextEffectRenderer.pillPaddingHPx(cfg.textSize), TextEffectRenderer.pillPaddingVPx(cfg.textSize))
        assertThat(margin).isEqualTo(expected)
    }

    @Test
    fun marginPx_multipleEffects_takesTheLargestOne() {
        val cfg = config(stroke = true, shadow = true, pill = true)

        val margin = TextEffectRenderer.marginPx(cfg)

        val strokeMargin = TextEffectRenderer.strokeWidthPx(cfg.textSize)
        val shadowMargin = TextEffectRenderer.shadowRadiusPx(cfg.textSize) + TextEffectRenderer.shadowDyPx(cfg.textSize)
        val pillMargin = maxOf(TextEffectRenderer.pillPaddingHPx(cfg.textSize), TextEffectRenderer.pillPaddingVPx(cfg.textSize))
        assertThat(margin).isEqualTo(maxOf(strokeMargin, shadowMargin, pillMargin))
    }
}
