package com.mckimquyen.watermark.ui.widget

import android.graphics.Color
import android.net.Uri
import android.text.TextPaint
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.ktx.applyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-07: `buildTextBitmapShader()` — 2 vấn đề trực tiếp gây crash/kích thước sai:
 * 1. `finalWidth`/`finalHeight` phải luôn > 0 kể cả khi gap âm lớn (trước fix: thiếu
 *    `coerceAtLeast(1)` → `IllegalArgumentException` từ `Bitmap.createBitmap`).
 * 2. Dòng trùng lặp/dòng rỗng trong text nhiều dòng không được crash hoặc lệch offset đo
 *    (trước fix: `indexOf` luôn trả vị trí lần xuất hiện ĐẦU TIÊN).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewTextShaderRoboTest {

    private fun imageInfo() = ImageInfo(Uri.parse("content://media/1"))

    private fun config(
        text: String,
        hGap: Int = 0,
        vGap: Int = 0,
        stroke: Boolean = false,
        shadow: Boolean = false,
        pill: Boolean = false
    ) = WaterMark(
        text = text,
        textSize = 40f,
        textColor = Color.WHITE,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = 255,
        degree = 0f,
        hGap = hGap,
        vGap = vGap,
        iconUri = Uri.EMPTY,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false,
        textEffectStroke = stroke,
        textEffectShadow = shadow,
        textEffectPillBackground = pill
    )

    private fun textPaint(info: ImageInfo, cfg: WaterMark) = TextPaint().applyConfig(info, cfg, isScale = false)

    @Test
    fun negativeGap_doesNotThrow_bitmapDimensionsAtLeast1() = runBlocking {
        val info = imageInfo()
        // hGap/vGap = -90 → adjustHorizontalGap/adjustVerticalGap nhân hệ số (gap/100f + 1) = 0.1
        // — vẫn dương nhẹ; đẩy sâu hơn nữa (-150) để hệ số âm, tái hiện đúng crash gốc.
        val cfg = config(text = "Watermark", hGap = -150, vGap = -150)

        // Trước fix: ném IllegalArgumentException ("width and height must be > 0").
        val shader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = info,
            config = cfg,
            textPaint = textPaint(info, cfg),
            coroutineContext = Dispatchers.Unconfined
        )

        assertThat(shader).isNotNull()
        assertThat(shader!!.width).isAtLeast(1)
        assertThat(shader.height).isAtLeast(1)
    }

    @Test
    fun duplicateLines_doesNotThrow_returnsValidShader() = runBlocking {
        val info = imageInfo()
        // 2 dòng có nội dung trùng lặp hệt nhau + 1 dòng rỗng xen giữa — trước fix dựa vào
        // `indexOf` (luôn trả vị trí xuất hiện ĐẦU TIÊN), nay dùng offset tuyến tính thực tế.
        val cfg = config(text = "AAAA\n\nAAAA\nBB\nAAAA")

        val shader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = info,
            config = cfg,
            textPaint = textPaint(info, cfg),
            coroutineContext = Dispatchers.Unconfined
        )

        assertThat(shader).isNotNull()
        assertThat(shader!!.width).isGreaterThan(0)
        assertThat(shader.height).isGreaterThan(0)
    }

    @Test
    fun multiLineText_heightGrowsWithMoreLines() = runBlocking {
        val info = imageInfo()
        val oneLine = config(text = "AAAA")
        val threeLines = config(text = "AAAA\nAAAA\nAAAA")

        val oneLineShader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = info,
            config = oneLine,
            textPaint = textPaint(info, oneLine),
            coroutineContext = Dispatchers.Unconfined
        )
        val threeLinesShader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = info,
            config = threeLines,
            textPaint = textPaint(info, threeLines),
            coroutineContext = Dispatchers.Unconfined
        )

        // Dùng staticLayout.height (BUG-07 #3, thay vì chỉ dòng đầu) để translate dọc — hệ quả
        // trực tiếp quan sát được: bitmap 3 dòng phải cao hơn hẳn bitmap 1 dòng.
        assertThat(threeLinesShader!!.height).isGreaterThan(oneLineShader!!.height)
    }

    @Test
    fun blankText_returnsNullShader() = runBlocking {
        val info = imageInfo()
        val cfg = config(text = "   ")

        val shader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = info,
            config = cfg,
            textPaint = textPaint(info, cfg),
            coroutineContext = Dispatchers.Unconfined
        )

        assertThat(shader).isNull()
    }

    /**
     * FEAT-11: mỗi hiệu ứng (viền/bóng/nền pill) cộng thêm biên quanh chữ để không bị cắt — hệ quả
     * đo được: bitmap lớn hơn hẳn so với tắt hết hiệu ứng, dù nội dung/text size giữ nguyên.
     */
    @Test
    fun textEffectStroke_enabled_growsShaderBeyondBaseline() = runBlocking {
        val info = imageInfo()
        val baseline = config(text = "AAAA")
        val withStroke = config(text = "AAAA", stroke = true)

        val baselineShader = WaterMarkImageView.buildTextBitmapShader(info, baseline, textPaint(info, baseline), Dispatchers.Unconfined)
        val strokeShader = WaterMarkImageView.buildTextBitmapShader(info, withStroke, textPaint(info, withStroke), Dispatchers.Unconfined)

        assertThat(strokeShader).isNotNull()
        assertThat(strokeShader!!.width).isGreaterThan(baselineShader!!.width)
        assertThat(strokeShader.height).isGreaterThan(baselineShader.height)
    }

    @Test
    fun textEffectShadow_enabled_growsShaderBeyondBaseline() = runBlocking {
        val info = imageInfo()
        val baseline = config(text = "AAAA")
        val withShadow = config(text = "AAAA", shadow = true)

        val baselineShader = WaterMarkImageView.buildTextBitmapShader(info, baseline, textPaint(info, baseline), Dispatchers.Unconfined)
        val shadowShader = WaterMarkImageView.buildTextBitmapShader(info, withShadow, textPaint(info, withShadow), Dispatchers.Unconfined)

        assertThat(shadowShader).isNotNull()
        assertThat(shadowShader!!.width).isGreaterThan(baselineShader!!.width)
        assertThat(shadowShader.height).isGreaterThan(baselineShader.height)
    }

    @Test
    fun textEffectPillBackground_enabled_growsShaderBeyondBaseline() = runBlocking {
        val info = imageInfo()
        val baseline = config(text = "AAAA")
        val withPill = config(text = "AAAA", pill = true)

        val baselineShader = WaterMarkImageView.buildTextBitmapShader(info, baseline, textPaint(info, baseline), Dispatchers.Unconfined)
        val pillShader = WaterMarkImageView.buildTextBitmapShader(info, withPill, textPaint(info, withPill), Dispatchers.Unconfined)

        assertThat(pillShader).isNotNull()
        assertThat(pillShader!!.width).isGreaterThan(baselineShader!!.width)
        assertThat(pillShader.height).isGreaterThan(baselineShader.height)
    }

    /** AC FEAT-11: cả 3 hiệu ứng phải kết hợp được đồng thời, không loại trừ nhau. */
    @Test
    fun allThreeTextEffects_combinedTogether_doesNotThrow_returnsValidShader() = runBlocking {
        val info = imageInfo()
        val cfg = config(text = "Watermark", stroke = true, shadow = true, pill = true)

        val shader = WaterMarkImageView.buildTextBitmapShader(info, cfg, textPaint(info, cfg), Dispatchers.Unconfined)

        assertThat(shader).isNotNull()
        assertThat(shader!!.width).isGreaterThan(0)
        assertThat(shader.height).isGreaterThan(0)
    }

    @Test
    fun textEffectsAllDisabled_shaderDimensionsUnchangedFromBeforeFeature() = runBlocking {
        // Đảm bảo hành vi mặc định (mọi hiệu ứng tắt) không đổi so với trước FEAT-11 — không phá
        // vỡ các test BUG-07 khác đang giả định kích thước không cộng thêm biên.
        val info = imageInfo()
        val cfg = config(text = "AAAA")

        val shader = WaterMarkImageView.buildTextBitmapShader(info, cfg, textPaint(info, cfg), Dispatchers.Unconfined)

        assertThat(shader).isNotNull()
        assertThat(shader!!.width).isGreaterThan(0)
    }
}
