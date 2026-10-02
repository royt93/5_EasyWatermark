package com.mckimquyen.watermark.utils

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualPresetBuilderTest {

    private fun baseWaterMark(
        text: String = "Test Copyright",
        iconUri: Uri = Uri.parse("content://media/external/images/media/42"),
        extraLayers: List<WatermarkLayer> = emptyList()
    ) = WaterMark(
        text = text,
        textSize = 14f,
        textColor = 0xFFFFFFFF.toInt(),
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = iconUri,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false,
        anchor = Anchor.CENTER.ordinal,
        marginPercent = 0.05f,
        extraLayers = extraLayers
    )

    @Test
    fun brandCopyrightPreset_setsCorrectAnchorsAndModes() {
        val original = baseWaterMark()
        val result = DualPresetBuilder.applyPreset(original, DualWatermarkPreset.BRAND_COPYRIGHT)

        assertThat(result.markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
        assertThat(result.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)
        assertThat(result.marginPercent).isEqualTo(DualWatermarkPreset.DEFAULT_MARGIN_PERCENT)
        assertThat(result.text).isEqualTo("Test Copyright")

        assertThat(result.extraLayers).hasSize(1)
        val secondary = result.extraLayers[0]
        assertThat(secondary.markMode).isEqualTo(WaterMarkRepository.MarkMode.Image)
        assertThat(secondary.anchor).isEqualTo(Anchor.TOP_RIGHT.ordinal)
        assertThat(secondary.iconUri).isEqualTo(original.iconUri)
        assertThat(secondary.alpha).isEqualTo(DualWatermarkPreset.DEFAULT_SECONDARY_ALPHA)
        assertThat(secondary.marginPercent).isEqualTo(DualWatermarkPreset.DEFAULT_MARGIN_PERCENT)
    }

    @Test
    fun diagonalBalancePreset_setsTopLeftForLogo_bottomRightForText() {
        val original = baseWaterMark()
        val result = DualPresetBuilder.applyPreset(original, DualWatermarkPreset.DIAGONAL_BALANCE)

        assertThat(result.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)
        assertThat(result.extraLayers).hasSize(1)
        assertThat(result.extraLayers[0].anchor).isEqualTo(Anchor.TOP_LEFT.ordinal)
    }

    @Test
    fun stampFooterPreset_setsTopRightForLogo_bottomCenterForText() {
        val original = baseWaterMark()
        val result = DualPresetBuilder.applyPreset(original, DualWatermarkPreset.STAMP_FOOTER)

        assertThat(result.anchor).isEqualTo(Anchor.BOTTOM_CENTER.ordinal)
        assertThat(result.extraLayers).hasSize(1)
        assertThat(result.extraLayers[0].anchor).isEqualTo(Anchor.TOP_RIGHT.ordinal)
    }

    @Test
    fun bottomDualPreset_setsBottomLeftForLogo_bottomRightForText() {
        val original = baseWaterMark()
        val result = DualPresetBuilder.applyPreset(original, DualWatermarkPreset.BOTTOM_DUAL)

        assertThat(result.anchor).isEqualTo(Anchor.BOTTOM_RIGHT.ordinal)
        assertThat(result.extraLayers).hasSize(1)
        assertThat(result.extraLayers[0].anchor).isEqualTo(Anchor.BOTTOM_LEFT.ordinal)
    }

    @Test
    fun emptyText_usesProvidedDefaultOrFallback() {
        val originalWithEmptyText = baseWaterMark(text = "")
        val resultWithCustomDefault = DualPresetBuilder.applyPreset(
            originalWithEmptyText,
            DualWatermarkPreset.BRAND_COPYRIGHT,
            defaultText = "Custom Studio"
        )
        assertThat(resultWithCustomDefault.text).isEqualTo("Custom Studio")

        val resultWithFallback = DualPresetBuilder.applyPreset(
            originalWithEmptyText,
            DualWatermarkPreset.BRAND_COPYRIGHT,
            defaultText = null
        )
        assertThat(resultWithFallback.text).isEqualTo(DualPresetBuilder.DEFAULT_FALLBACK_TEXT)
    }

    @Test
    fun existingExtraLayers_replacesFirstLayer_andRespectsMaxLimit() {
        val existingLayer1 = WatermarkLayer(
            markMode = WaterMarkRepository.MarkMode.Text,
            text = "Old Layer 1",
            anchor = Anchor.CENTER.ordinal
        )
        val existingLayer2 = WatermarkLayer(
            markMode = WaterMarkRepository.MarkMode.Text,
            text = "Old Layer 2",
            anchor = Anchor.TOP_CENTER.ordinal
        )
        val original = baseWaterMark(extraLayers = listOf(existingLayer1, existingLayer2))

        val result = DualPresetBuilder.applyPreset(original, DualWatermarkPreset.BRAND_COPYRIGHT)

        assertThat(result.extraLayers).hasSize(2)
        // First layer replaced with secondary preset (Image at TOP_RIGHT)
        assertThat(result.extraLayers[0].markMode).isEqualTo(WaterMarkRepository.MarkMode.Image)
        assertThat(result.extraLayers[0].anchor).isEqualTo(Anchor.TOP_RIGHT.ordinal)
        // Second layer retained
        assertThat(result.extraLayers[1].text).isEqualTo("Old Layer 2")
    }

    @Test
    fun presetFromId_returnsMatchingOrFallback() {
        assertThat(DualWatermarkPreset.fromId("brand_copyright")).isEqualTo(DualWatermarkPreset.BRAND_COPYRIGHT)
        assertThat(DualWatermarkPreset.fromId("diagonal_balance")).isEqualTo(DualWatermarkPreset.DIAGONAL_BALANCE)
        assertThat(DualWatermarkPreset.fromId("stamp_footer")).isEqualTo(DualWatermarkPreset.STAMP_FOOTER)
        assertThat(DualWatermarkPreset.fromId("bottom_dual")).isEqualTo(DualWatermarkPreset.BOTTOM_DUAL)
        assertThat(DualWatermarkPreset.fromId("unknown_id")).isEqualTo(DualWatermarkPreset.BRAND_COPYRIGHT)
        assertThat(DualWatermarkPreset.fromId(null)).isEqualTo(DualWatermarkPreset.BRAND_COPYRIGHT)
    }
}
