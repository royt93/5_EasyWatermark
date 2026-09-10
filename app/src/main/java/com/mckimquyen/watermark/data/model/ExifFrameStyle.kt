package com.mckimquyen.watermark.data.model

import android.graphics.Color

/**
 * Kiểu khung cho tính năng EXIF border (xem [com.mckimquyen.watermark.ui.MainViewModel]
 * hàm buildExifBorderBitmap). Chỉ vẽ bằng Canvas thuần — không dùng logo hãng máy thật
 * (tránh rủi ro bản quyền/trademark).
 *
 * [defaultBandColor]/[defaultBandThicknessPercent]/[defaultUseSerifCaption] là giá trị gốc của
 * từng style — dùng làm fallback khi user chưa tuỳ chỉnh (FEAT-14 Custom Frame Builder) và làm
 * giá trị hiển thị mặc định trên UI (`ExifPbFragment`).
 */
enum class ExifFrameStyle(
    val defaultBandColor: Int,
    val defaultBandThicknessPercent: Float,
    val defaultUseSerifCaption: Boolean
) {
    /** Thanh trắng dưới đáy: tên máy đậm bên trái, thông số + ngày bên phải. */
    CLASSIC(Color.WHITE, 0.12f, false),

    /** Viền trắng dày đều 4 cạnh kiểu ảnh Polaroid, caption ở đáy. */
    POLAROID(Color.WHITE, 0.16f, true),

    /** Dải đen trên/dưới có lỗ sprocket như phim máy ảnh, chữ trắng ở dải dưới. */
    FILM_STRIP(Color.BLACK, 0.10f, false),

    /** Dải trắng mỏng, 1 dòng chữ nhỏ gọn. */
    MINIMAL(Color.WHITE, 0.06f, false);

    companion object {
        fun obtain(ordinal: Int): ExifFrameStyle = entries.getOrElse(ordinal) { CLASSIC }
    }
}
