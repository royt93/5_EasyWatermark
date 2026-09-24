package com.mckimquyen.watermark.data.model

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-03: `Uri.parse()`/`Uri.encode()` cần Android stub → Robolectric (không cần DataStore/Context
 * thật, chỉ test hàm thuần [WatermarkLayer.serializeList]/[WatermarkLayer.parseList]).
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkLayerSerializationTest {

    private fun sampleLayer(
        text: String = "Copyright 2026",
        markMode: WaterMarkRepository.MarkMode = WaterMarkRepository.MarkMode.Text,
        iconUri: Uri = Uri.EMPTY
    ) = WatermarkLayer(
        markMode = markMode,
        text = text,
        textSize = 18f,
        textColor = 0xAABBCC,
        textStyle = TextPaintStyle.Stroke,
        textTypeface = TextTypeface.BoldItalic,
        iconUri = iconUri,
        alpha = 200,
        degree = 45f,
        hGap = 10,
        vGap = 20,
        anchor = Anchor.BOTTOM_RIGHT.ordinal,
        marginPercent = 0.08f
    )

    @Test
    fun parseList_nullOrBlank_returnsEmpty() {
        assertThat(WatermarkLayer.parseList(null)).isEmpty()
        assertThat(WatermarkLayer.parseList("")).isEmpty()
        assertThat(WatermarkLayer.parseList("   ")).isEmpty()
    }

    @Test
    fun serializeList_emptyList_roundTripsToEmpty() {
        val serialized = WatermarkLayer.serializeList(emptyList())
        assertThat(WatermarkLayer.parseList(serialized)).isEmpty()
    }

    @Test
    fun serializeList_singleLayer_roundTrips() {
        val layer = sampleLayer()

        val parsed = WatermarkLayer.parseList(WatermarkLayer.serializeList(listOf(layer)))

        assertThat(parsed).containsExactly(layer)
    }

    @Test
    fun serializeList_maxLayers_roundTripsInOrder() {
        val layers = (1..WaterMarkRepository.MAX_EXTRA_LAYERS).map { sampleLayer(text = "Layer $it") }

        val parsed = WatermarkLayer.parseList(WatermarkLayer.serializeList(layers))

        assertThat(parsed).containsExactlyElementsIn(layers).inOrder()
    }

    @Test
    fun serializeList_textContainsDelimiterCharacters_roundTripsExactly() {
        // "|" là field delimiter, "\n" là layer delimiter — text user gõ có thể chứa cả 2 (watermark
        // nhiều dòng dùng "\n" làm dấu xuống dòng, xem buildTextBitmapShader).
        val layer = sampleLayer(text = "Line 1|with pipe\nLine 2\nLine 3")

        val parsed = WatermarkLayer.parseList(WatermarkLayer.serializeList(listOf(layer)))

        assertThat(parsed).containsExactly(layer)
        assertThat(parsed.single().text).isEqualTo("Line 1|with pipe\nLine 2\nLine 3")
    }

    @Test
    fun serializeList_imageLayerWithEmptyIconUri_roundTrips() {
        val layer = sampleLayer(markMode = WaterMarkRepository.MarkMode.Image, iconUri = Uri.EMPTY)

        val parsed = WatermarkLayer.parseList(WatermarkLayer.serializeList(listOf(layer)))

        assertThat(parsed).containsExactly(layer)
    }

    @Test
    fun serializeList_imageLayerWithRealIconUri_roundTrips() {
        val layer = sampleLayer(markMode = WaterMarkRepository.MarkMode.Image, iconUri = Uri.parse("content://media/logo.png"))

        val parsed = WatermarkLayer.parseList(WatermarkLayer.serializeList(listOf(layer)))

        assertThat(parsed).containsExactly(layer)
    }

    @Test
    fun parseList_malformedEntry_skippedInsteadOfCrashing() {
        val valid = sampleLayer(text = "Valid")
        // 1 entry hỏng (thiếu field) chen giữa 2 entry hợp lệ — không được làm hỏng cả list.
        val raw = WatermarkLayer.serializeList(listOf(valid)) + "\n" + "not-enough-fields" + "\n" + WatermarkLayer.serializeList(listOf(valid))

        val parsed = WatermarkLayer.parseList(raw)

        assertThat(parsed).containsExactly(valid, valid)
    }
}
