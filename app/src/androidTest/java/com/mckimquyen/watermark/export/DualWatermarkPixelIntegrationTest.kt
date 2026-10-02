package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.widget.WaterMarkImageView
import com.mckimquyen.watermark.ui.widget.utils.WaterMarkShader
import com.mckimquyen.watermark.utils.DualPresetBuilder
import com.mckimquyen.watermark.utils.ktx.applyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * FEAT-26 Integration Test (chạy Skia thật trên thiết bị Android):
 * Chứng minh Pixel: Sau khi áp dụng preset Dual Watermark (Logo Top-Right + Text Bottom-Right),
 * - Góc Top-Right có pixel màu của Logo.
 * - Góc Bottom-Right có pixel màu của Text.
 * - Vùng giữa hoặc góc Top-Left giữ nguyên màu nền.
 */
@RunWith(AndroidJUnit4::class)
class DualWatermarkPixelIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    companion object {
        private const val CANVAS_SIZE = 800
        private const val LOGO_SIZE = 100
        private val BG_COLOR = Color.rgb(10, 10, 10) // Nền đen tuyền
        private val LOGO_COLOR = Color.rgb(255, 0, 0) // Logo đỏ
        private val TEXT_COLOR = Color.rgb(255, 255, 255) // Text trắng
    }

    private fun createLogoUri(): Uri {
        val logoBitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(LOGO_COLOR)
        }
        val file = File(context.cacheDir, "test_dual_logo.png")
        FileOutputStream(file).use { out ->
            logoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Uri.fromFile(file)
    }

    /** Mô phỏng `WaterMarkImageView.drawExtraLayers` nhánh CLAMP: translate theo neo rồi vẽ đúng kích thước shader. */
    private fun drawAnchored(canvas: Canvas, shader: WaterMarkShader, anchor: Int, margin: Float, paint: Paint) {
        val size = CANVAS_SIZE.toFloat()
        val (ox, oy) = Anchor.obtain(anchor).toOffset(margin, shader.width / size, shader.height / size)
        canvas.save()
        canvas.translate(ox * size, oy * size)
        canvas.drawRect(0f, 0f, shader.width.toFloat(), shader.height.toFloat(), paint)
        canvas.restore()
    }

    @Test
    fun brandCopyrightPreset_rendersLogoTopRight_andTextBottomRight() = runBlocking {
        val logoUri = createLogoUri()

        val baseConfig = WaterMark(
            text = "BRAND COPYRIGHT 2026",
            textSize = 28f,
            textColor = TEXT_COLOR,
            textStyle = TextPaintStyle.Fill,
            textTypeface = TextTypeface.Bold,
            alpha = 255,
            degree = 0f,
            hGap = 0,
            vGap = 0,
            iconUri = logoUri,
            markMode = WaterMarkRepository.MarkMode.Text,
            enableBounds = false,
            anchor = Anchor.CENTER.ordinal,
            marginPercent = 0.04f
        )

        val dualConfig = DualPresetBuilder.applyPreset(baseConfig, DualWatermarkPreset.BRAND_COPYRIGHT)

        val targetBitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(BG_COLOR)
        }
        val canvas = Canvas(targetBitmap)

        val imageInfo = ImageInfo(
            uri = Uri.parse("file:///dummy"),
            width = CANVAS_SIZE,
            height = CANVAS_SIZE,
            // View thật đặt watermark theo anchor ở CLAMP (REPEAT sẽ tile phủ kín canvas).
            tileMode = Shader.TileMode.CLAMP.ordinal
        )

        // 1. Vẽ layer chính (Text ở Bottom-Right)
        val textPaint = android.text.TextPaint().applyConfig(imageInfo, dualConfig, isScale = false)
        val textShader = WaterMarkImageView.buildTextBitmapShader(
            imageInfo = imageInfo,
            config = dualConfig,
            textPaint = textPaint,
            coroutineContext = Dispatchers.IO
        )
        assertThat(textShader).isNotNull()

        val wmPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            shader = textShader?.bitmapShader
        }
        drawAnchored(canvas, textShader!!, dualConfig.anchor, dualConfig.marginPercent, wmPaint)

        // 2. Vẽ layer phụ (Logo ở Top-Right)
        val secondaryLayer = dualConfig.extraLayers.first()
        val logoSrcBitmap = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(LOGO_COLOR)
        }
        val secondaryConfig = secondaryLayer.toWaterMark()
        val logoShader = WaterMarkImageView.buildIconBitmapShader(
            imageInfo = imageInfo,
            srcBitmap = logoSrcBitmap,
            config = secondaryConfig,
            textPaint = textPaint,
            scale = false,
            coroutineContext = Dispatchers.IO
        )
        assertThat(logoShader).isNotNull()

        val layerPaint = Paint().apply {
            isAntiAlias = true
            isDither = true
            shader = logoShader?.bitmapShader
            alpha = secondaryLayer.alpha
        }
        drawAnchored(canvas, logoShader!!, secondaryLayer.anchor, secondaryLayer.marginPercent, layerPaint)

        // 3. Pixel Assertions:
        // Top-Left (x=50, y=50) -> Phải là màu nền (không có watermark)
        val topLeftPixel = targetBitmap.getPixel(50, 50)
        assertThat(topLeftPixel).isEqualTo(BG_COLOR)

        // Center (x=400, y=400) -> Phải là màu nền
        val centerPixel = targetBitmap.getPixel(400, 400)
        assertThat(centerPixel).isEqualTo(BG_COLOR)

        // Top-Right: quét vùng Logo (x từ 650 đến 750, y từ 40 đến 120) -> Có chứa pixel đỏ
        var hasRedLogoPixel = false
        for (x in 650..750) {
            for (y in 40..120) {
                val p = targetBitmap.getPixel(x, y)
                if (Color.red(p) > 200 && Color.green(p) < 50 && Color.blue(p) < 50) {
                    hasRedLogoPixel = true
                    break
                }
            }
            if (hasRedLogoPixel) break
        }
        assertThat(hasRedLogoPixel).isTrue()

        // Bottom-Right: quét vùng Text (x từ 550 đến 780, y từ 700 đến 780) -> Có chứa pixel sáng (chữ trắng)
        var hasTextPixel = false
        for (x in 550..780) {
            for (y in 700..780) {
                val p = targetBitmap.getPixel(x, y)
                if (Color.red(p) > 200 && Color.green(p) > 200 && Color.blue(p) > 200) {
                    hasTextPixel = true
                    break
                }
            }
            if (hasTextPixel) break
        }
        assertThat(hasTextPixel).isTrue()
    }
}
