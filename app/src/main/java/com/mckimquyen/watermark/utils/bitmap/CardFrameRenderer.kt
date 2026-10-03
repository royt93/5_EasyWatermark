package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import kotlin.math.ceil
import kotlin.math.min

/**
 * FEAT-28 Frame & Shadow Builder — hàm thuần (không đụng state), hiện chỉ `BatchExportEngine` gọi.
 * Ảnh nguồn được bo góc, đặt vào giữa canvas MỞ RỘNG nền màu, kèm đổ bóng mềm phía dưới như thẻ
 * sản phẩm. [computeLayout] cũng dùng để ước tính kích thước ở grid preview export.
 */
object CardFrameRenderer {

    /** Bóng đổ lệch xuống dưới = 1/3 độ nhoè, cho cảm giác "nổi" thay vì quầng đều 4 phía. */
    private const val SHADOW_OFFSET_DIVISOR = 3f

    /** Alpha bóng (0..255) — đủ rõ trên nền trắng, vẫn nhẹ trên nền tối. */
    private const val SHADOW_ALPHA = 90

    /** Padding tối thiểu quanh ảnh (px) kể cả khi tắt bóng, để nền màu còn thấy được như 1 "thẻ". */
    private const val MIN_PADDING_PX = 8

    /** Padding tối thiểu theo tỉ lệ cạnh ngắn, cộng thêm vào phần bóng. */
    private const val BASE_PADDING_PERCENT = 0.04f

    data class Layout(
        val canvasWidth: Int,
        val canvasHeight: Int,
        val padding: Int,
        val cornerRadiusPx: Float,
        val shadowBlurPx: Float,
        val shadowOffsetY: Float
    )

    /** Tính kích thước canvas/padding từ kích thước ảnh + tham số %. Hàm thuần, dễ unit test. */
    fun computeLayout(
        srcWidth: Int,
        srcHeight: Int,
        cornerPercent: Float,
        shadowPercent: Float
    ): Layout {
        val shortSide = min(srcWidth, srcHeight).coerceAtLeast(1)
        val corner = cornerPercent.coerceIn(0f, WaterMarkRepository.MAX_CARD_CORNER_PERCENT)
        val shadow = shadowPercent.coerceIn(0f, WaterMarkRepository.MAX_CARD_SHADOW_PERCENT)
        // Bán kính không được vượt nửa cạnh ngắn, nếu không RoundRect vẽ méo thành hình lạ.
        val radius = (shortSide * corner).coerceAtMost(shortSide / 2f)
        val blur = shortSide * shadow
        val offsetY = blur / SHADOW_OFFSET_DIVISOR
        val padding = maxOf(
            MIN_PADDING_PX,
            ceil(shortSide * BASE_PADDING_PERCENT + blur * 2f + offsetY).toInt()
        )
        return Layout(
            canvasWidth = srcWidth + padding * 2,
            canvasHeight = srcHeight + padding * 2,
            padding = padding,
            cornerRadiusPx = radius,
            shadowBlurPx = blur,
            shadowOffsetY = offsetY
        )
    }

    /**
     * Trả bitmap MỚI (ARGB_8888); KHÔNG recycle [source] — caller sở hữu và tự recycle (cùng
     * quy ước [ExifBorderRenderer.buildExifBorderBitmap]).
     */
    fun buildCardBitmap(
        source: Bitmap,
        cornerPercent: Float,
        shadowPercent: Float,
        @ColorInt backgroundColor: Int
    ): Bitmap {
        val layout = computeLayout(source.width, source.height, cornerPercent, shadowPercent)
        val output = Bitmap.createBitmap(layout.canvasWidth, layout.canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)

        val pad = layout.padding.toFloat()
        val photoRect = RectF(pad, pad, pad + source.width, pad + source.height)

        if (layout.shadowBlurPx > 0f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(SHADOW_ALPHA, 0, 0, 0)
                maskFilter = BlurMaskFilter(layout.shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
            }
            val shadowRect = RectF(photoRect).apply { offset(0f, layout.shadowOffsetY) }
            canvas.drawRoundRect(shadowRect, layout.cornerRadiusPx, layout.cornerRadiusPx, shadowPaint)
        }

        // Bo góc bằng BitmapShader + drawRoundRect: vẽ 1:1 (không scale) nên không lấy mẫu lại, viền
        // anti-alias mượt, và KHÔNG cần bitmap mask phụ cỡ ảnh gốc (tiết kiệm RAM ở ảnh full-res).
        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.translate(pad, pad)
        canvas.drawRoundRect(
            RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
            layout.cornerRadiusPx,
            layout.cornerRadiusPx,
            photoPaint
        )
        canvas.restore()
        return output
    }
}
