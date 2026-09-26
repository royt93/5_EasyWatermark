package com.mckimquyen.watermark.export

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** IDEA-13: cấu hình proofing và HTML index thuần. */
@RunWith(RobolectricTestRunner::class)
class ProofingModeTest {

    private fun config(text: String = "Studio"): WaterMark = WaterMark(
        text = text,
        textSize = 14f,
        textColor = 0,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = 10,
        vGap = 10,
        iconUri = Uri.parse("content://icon"),
        markMode = WaterMarkRepository.MarkMode.Image,
        enableBounds = false,
        enableExif = true,
        extraLayers = listOf(WatermarkLayer(WaterMarkRepository.MarkMode.Text, text = "extra")),
        qrDynamicEnabled = true,
        qrContentTemplate = "{hash}",
        qrPortfolioLink = "site"
    )

    @Test
    fun overrideConfig_forcesLargeRepeatedText_andRemovesOptionalLayers() {
        val result = ProofingMode.overrideConfig(config())

        assertThat(result.markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
        // Text cố định, KHÔNG lấy base.text (text dài xoay -30° làm ô tile vượt ảnh, mất "#001").
        assertThat(result.text).isEqualTo("PROOF  #{seq3}")
        assertThat(result.textSize).isEqualTo(ProofingMode.PROOF_TEXT_SIZE)
        assertThat(result.alpha).isEqualTo(ProofingMode.PROOF_ALPHA)
        assertThat(result.degree).isEqualTo(ProofingMode.PROOF_DEGREE)
        assertThat(result.enableExif).isFalse()
        assertThat(result.extraLayers).isEmpty()
        assertThat(result.qrDynamicEnabled).isFalse()
    }

    @Test
    fun overrideConfig_ignoresBaseText_alwaysUsesFixedProofText() {
        assertThat(ProofingMode.overrideConfig(config(" ")).text).isEqualTo("PROOF  #{seq3}")
        assertThat(ProofingMode.overrideConfig(config("Text rất dài của user")).text).isEqualTo("PROOF  #{seq3}")
    }

    @Test
    fun withSeq_alwaysAppendsSeq3_toCaption() {
        assertThat(ProofingMode.withSeq("Client caption")).isEqualTo("Client caption  #{seq3}")
        assertThat(ProofingMode.withSeq("")).isEqualTo("  #{seq3}")
    }

    @Test
    fun buildHtml_preservesInputOrder_andOriginalSequenceGaps() {
        val html = ProofingMode.buildHtml(
            listOf(
                ProofingMode.Entry(1, "photo1.jpg"),
                ProofingMode.Entry(3, "photo3.jpg")
            )
        )

        assertThat(html.indexOf("#001")).isLessThan(html.indexOf("#003"))
        assertThat(html).contains("src=\"photo1.jpg\"")
        assertThat(html).contains("src=\"photo3.jpg\"")
        assertThat(html).doesNotContain("#002")
    }

    @Test
    fun buildHtml_escapesFileNames() {
        val html = ProofingMode.buildHtml(listOf(ProofingMode.Entry(7, "a&<b>\"'.jpg")))

        assertThat(html).contains("a&amp;&lt;b&gt;&quot;&#39;.jpg")
        assertThat(html).doesNotContain("a&<b>")
    }

    @Test
    fun buildHtml_emptyList_stillProducesValidEmptyIndex() {
        val html = ProofingMode.buildHtml(emptyList())

        assertThat(html).contains("<!doctype html>")
        assertThat(html).contains("<div class=\"grid\"></div>")
    }
}
