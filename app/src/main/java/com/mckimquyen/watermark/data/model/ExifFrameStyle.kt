package com.mckimquyen.watermark.data.model

/**
 * Kiểu khung cho tính năng EXIF border (xem [com.mckimquyen.watermark.ui.MainViewModel]
 * hàm buildExifBorderBitmap). Chỉ vẽ bằng Canvas thuần — không dùng logo hãng máy thật
 * (tránh rủi ro bản quyền/trademark).
 */
enum class ExifFrameStyle {
    /** Thanh trắng dưới đáy: tên máy đậm bên trái, thông số + ngày bên phải. */
    CLASSIC,

    /** Viền trắng dày đều 4 cạnh kiểu ảnh Polaroid, caption ở đáy. */
    POLAROID,

    /** Dải đen trên/dưới có lỗ sprocket như phim máy ảnh, chữ trắng ở dải dưới. */
    FILM_STRIP,

    /** Dải trắng mỏng, 1 dòng chữ nhỏ gọn. */
    MINIMAL;

    companion object {
        fun obtain(ordinal: Int): ExifFrameStyle = entries.getOrElse(ordinal) { CLASSIC }
    }
}
