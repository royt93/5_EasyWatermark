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

        /**
         * FEAT-10: gợi ý style khung theo hãng máy đọc từ EXIF `TAG_MAKE` — chỉ là GIÁ TRỊ MẶC ĐỊNH
         * ban đầu, user luôn đổi tay được sau đó (xem `MainViewModel.suggestExifFrameStyleIfNeeded`).
         * Hãng gắn liền phim/rangefinder cổ điển → Classic; hãng phim lấy liền/máy phim → Polaroid;
         * DSLR/mirrorless chuyên nghiệp → Film Strip (gợi nhớ cuộn phim); điện thoại hoặc không rõ
         * hãng → Minimal (đúng ví dụ AC gốc: "máy không rõ hãng gợi ý Minimal").
         */
        fun suggestFor(make: String): ExifFrameStyle {
            val normalized = make.trim().uppercase()
            return when {
                normalized.isEmpty() -> MINIMAL
                listOf("LEICA", "FUJIFILM", "FUJI").any { normalized.contains(it) } -> CLASSIC
                listOf("POLAROID", "INSTAX", "KODAK").any { normalized.contains(it) } -> POLAROID
                listOf("CANON", "NIKON", "SONY", "PENTAX", "OLYMPUS", "PANASONIC").any { normalized.contains(it) } -> FILM_STRIP
                else -> MINIMAL
            }
        }
    }
}
