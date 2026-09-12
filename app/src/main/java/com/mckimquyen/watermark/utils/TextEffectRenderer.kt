package com.mckimquyen.watermark.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.mckimquyen.watermark.data.model.WaterMark

/**
 * FEAT-11: viền tương phản / đổ bóng / nền pill cho text watermark — giúp chữ đọc rõ trên ảnh nền
 * phức tạp (nhiều màu, độ tương phản thấp). Cả 3 hiệu ứng dùng chung 1 màu tương phản B/W tự động
 * tính theo độ sáng màu chữ (WCAG luminance) — không thêm color picker riêng, ngoài phạm vi AC.
 */
object TextEffectRenderer {

    private const val STROKE_WIDTH_RATIO = 0.08f
    private const val SHADOW_RADIUS_RATIO = 0.18f
    private const val SHADOW_DY_RATIO = 0.06f
    private const val PILL_PADDING_H_RATIO = 0.35f
    private const val PILL_PADDING_V_RATIO = 0.28f
    private const val PILL_ALPHA = 140
    private const val STROKE_ALPHA = 220
    private const val LUMINANCE_THRESHOLD = 0.5

    fun strokeWidthPx(textSize: Float) = (textSize * STROKE_WIDTH_RATIO).coerceAtLeast(1f)

    fun shadowRadiusPx(textSize: Float) = textSize * SHADOW_RADIUS_RATIO

    fun shadowDyPx(textSize: Float) = textSize * SHADOW_DY_RATIO

    fun pillPaddingHPx(textSize: Float) = textSize * PILL_PADDING_H_RATIO

    fun pillPaddingVPx(textSize: Float) = textSize * PILL_PADDING_V_RATIO

    /** Màu B/W tương phản với [textColor] theo độ sáng — không phụ thuộc bảng màu người dùng chọn. */
    fun contrastingColor(textColor: Int, alpha: Int): Int {
        val base = if (ColorUtils.calculateLuminance(textColor) > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE
        return ColorUtils.setAlphaComponent(base, alpha)
    }

    /** Biên (px, mỗi phía) cần cộng thêm quanh chữ để viền/bóng/nền pill không bị cắt ở mép bitmap. */
    fun marginPx(config: WaterMark): Float {
        var margin = 0f
        if (config.textEffectStroke) margin = maxOf(margin, strokeWidthPx(config.textSize))
        if (config.textEffectShadow) margin = maxOf(margin, shadowRadiusPx(config.textSize) + shadowDyPx(config.textSize))
        if (config.textEffectPillBackground) {
            margin = maxOf(margin, pillPaddingHPx(config.textSize), pillPaddingVPx(config.textSize))
        }
        return margin
    }

    /** Vẽ nền pill (rounded rect) NGAY DƯỚI chữ, bao trọn kích thước [layoutWidth]x[layoutHeight] hiện tại. */
    fun drawPillBackground(canvas: Canvas, config: WaterMark, layoutWidth: Float, layoutHeight: Float) {
        val paddingH = pillPaddingHPx(config.textSize)
        val paddingV = pillPaddingVPx(config.textSize)
        val rect = RectF(-paddingH, -paddingV, layoutWidth + paddingH, layoutHeight + paddingV)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contrastingColor(config.textColor, PILL_ALPHA)
            style = Paint.Style.FILL
        }
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    /** Vẽ 1 lớp chữ viền (Style.STROKE, màu tương phản) NGAY DƯỚI lớp chữ chính, cùng vị trí/kích thước. */
    fun drawStrokeOutline(canvas: Canvas, config: WaterMark, basePaint: TextPaint, maxLineWidth: Int) {
        val strokePaint = TextPaint().apply {
            set(basePaint)
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx(config.textSize)
            color = contrastingColor(config.textColor, STROKE_ALPHA)
            clearShadowLayer()
        }
        val strokeLayout = StaticLayout.Builder.obtain(config.text, 0, config.text.length, strokePaint, maxLineWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        strokeLayout.draw(canvas)
    }
}
