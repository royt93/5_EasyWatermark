package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.mckimquyen.watermark.data.model.ExifFrameStyle

/**
 * IDEA-18: bảng màu khung EXIF sinh theo màu chủ đạo của CHÍNH ảnh đang xuất, thay vì 4 bảng màu
 * cố định của [ExifFrameStyle]. Hàm thuần (không cần Canvas) để test được màu — Robolectric của repo
 * không rasterize pixel nên không assert được màu trên bitmap khung.
 */
object ExifFramePalette {

    /** Màu dùng để vẽ 1 khung: nền dải, chữ chính (tên máy), chữ phụ (thông số/ngày), đường kẻ/lỗ sprocket. */
    data class FrameColors(
        val band: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accent: Int
    )

    /** Ngưỡng WCAG AA cho chữ thường — cùng mức `PaletteKtx.titleTextColor` đang dùng. */
    const val MIN_TEXT_CONTRAST = 4.5
    private const val SECONDARY_TEXT_BLEND = 0.25f
    private const val ACCENT_BLEND = 0.35f
    private const val OPAQUE = 255

    /**
     * [dominantRgb] = màu chủ đạo của ảnh (vd `Palette.dominantSwatch.rgb`); `null` (ảnh không trích được
     * màu) → bảng màu gốc của [style], đúng hành vi trước IDEA-18.
     */
    fun resolve(dominantRgb: Int?, style: ExifFrameStyle): FrameColors {
        if (dominantRgb == null) return defaultsFor(style)
        val band = ColorUtils.setAlphaComponent(dominantRgb, OPAQUE)
        // Không dùng TextEffectRenderer.contrastingColor (ngưỡng luminance 0.5): nền xám trung tính ~#808080
        // sẽ ra chữ trắng chỉ đạt 3.9:1. Chọn đen/trắng theo tỉ lệ WCAG cao hơn luôn đạt ≥ 4.58:1 với mọi nền.
        val primary = if (ColorUtils.calculateContrast(Color.BLACK, band) >= ColorUtils.calculateContrast(Color.WHITE, band)) {
            Color.BLACK
        } else {
            Color.WHITE
        }
        // Chữ phụ pha nhẹ về màu nền cho cảm giác "cùng tông", nhưng không được tụt dưới ngưỡng đọc.
        val secondary = ColorUtils.blendARGB(primary, band, SECONDARY_TEXT_BLEND)
            .takeIf { ColorUtils.calculateContrast(it, band) >= MIN_TEXT_CONTRAST } ?: primary
        return FrameColors(
            band = band,
            primaryText = primary,
            secondaryText = secondary,
            accent = ColorUtils.blendARGB(band, primary, ACCENT_BLEND)
        )
    }

    /** Bảng màu gốc từng style — khớp y nguyên các màu từng hardcode trong [ExifBorderRenderer]. */
    fun defaultsFor(style: ExifFrameStyle): FrameColors = when (style) {
        ExifFrameStyle.CLASSIC -> FrameColors(style.defaultBandColor, Color.BLACK, Color.DKGRAY, Color.LTGRAY)
        ExifFrameStyle.POLAROID -> FrameColors(style.defaultBandColor, Color.BLACK, Color.DKGRAY, Color.LTGRAY)
        ExifFrameStyle.FILM_STRIP -> FrameColors(style.defaultBandColor, Color.WHITE, Color.WHITE, Color.WHITE)
        ExifFrameStyle.MINIMAL -> FrameColors(style.defaultBandColor, Color.DKGRAY, Color.DKGRAY, Color.LTGRAY)
    }
}
