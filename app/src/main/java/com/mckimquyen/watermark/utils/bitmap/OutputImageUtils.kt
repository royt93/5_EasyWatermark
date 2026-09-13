package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap

/**
 * Tiện ích thuần liên quan tới định dạng & kích thước ảnh xuất.
 * Tách riêng để dễ unit test (phần tính kích thước hoàn toàn không phụ thuộc Android).
 */
object OutputImageUtils {

    /** Không giới hạn cạnh dài (giữ nguyên kích thước gốc). */
    const val RESIZE_ORIGINAL = 0

    /** 1 preset resize cho dropdown xuất ảnh: nhãn hiển thị + giới hạn cạnh dài (px). */
    data class ResizePreset(val label: String, val maxLongEdge: Int)

    /**
     * Preset resize cho dropdown xuất ảnh: preset theo px (giữ nguyên từ trước) cộng preset đặt
     * tên theo nền tảng mạng xã hội phổ biến. Chỉ giới hạn cạnh dài, KHÔNG crop — tỉ lệ khung
     * hình gốc luôn được giữ nguyên (xem [targetDimensions]).
     */
    val resizePresets: List<ResizePreset> = listOf(
        ResizePreset("Original", RESIZE_ORIGINAL),
        ResizePreset("1080", 1080),
        ResizePreset("2048", 2048),
        ResizePreset("4096", 4096),
        ResizePreset("Instagram (1080)", 1080),
        ResizePreset("Facebook (2048)", 2048),
        ResizePreset("Zalo (1600)", 1600)
    )

    /** Phần đuôi file theo định dạng nén. */
    fun extensionFor(format: Bitmap.CompressFormat): String = when (format) {
        Bitmap.CompressFormat.PNG -> "png"
        Bitmap.CompressFormat.JPEG -> "jpg"
        else -> "webp" // WEBP / WEBP_LOSSY / WEBP_LOSSLESS
    }

    /** MIME type ảnh theo định dạng nén. */
    fun mimeTypeFor(format: Bitmap.CompressFormat): String = "image/${extensionFor(format)}"

    /**
     * Tính kích thước đích sao cho cạnh dài không vượt [maxLongEdge], giữ nguyên tỉ lệ.
     * Trả về (width, height) gốc nếu [maxLongEdge] <= 0 hoặc ảnh đã nhỏ hơn giới hạn.
     * Hàm thuần — không đụng tới Android, test trực tiếp trên JVM.
     */
    fun targetDimensions(width: Int, height: Int, maxLongEdge: Int): Pair<Int, Int> {
        if (maxLongEdge <= RESIZE_ORIGINAL || width <= 0 || height <= 0) {
            return width to height
        }
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) {
            return width to height
        }
        val scale = maxLongEdge.toFloat() / longEdge
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return targetW to targetH
    }

    /**
     * Resize [bitmap] sao cho cạnh dài không vượt [maxLongEdge] (giữ tỉ lệ).
     * Trả về chính [bitmap] nếu không cần resize.
     */
    fun resizeIfNeeded(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
        val (targetW, targetH) = targetDimensions(bitmap.width, bitmap.height, maxLongEdge)
        if (targetW == bitmap.width && targetH == bitmap.height) {
            return bitmap
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    /**
     * FEAT-07: ước tính DUNG LƯỢNG output theo heuristic bits-per-pixel tuyến tính theo quality —
     * KHÔNG chính xác tuyệt đối (phụ thuộc nội dung ảnh thật, không nén thử thật sự), chỉ đủ để
     * user có khái niệm tương đối trước khi export cả batch lớn. Hàm thuần — test trực tiếp trên JVM.
     */
    fun estimateOutputBytes(width: Int, height: Int, format: Bitmap.CompressFormat, quality: Int): Long {
        val pixels = width.toLong() * height.toLong()
        val bitsPerPixel = if (format == Bitmap.CompressFormat.PNG) {
            PNG_ESTIMATED_BITS_PER_PIXEL
        } else {
            JPEG_MIN_BITS_PER_PIXEL + (quality.coerceIn(0, 100) / 100.0) * JPEG_QUALITY_BITS_PER_PIXEL_RANGE
        }
        return (pixels * bitsPerPixel / 8.0).toLong().coerceAtLeast(MIN_ESTIMATED_BYTES)
    }

    /** PNG lossless — ước lượng trung bình cho ảnh chụp thường (không phải icon/flat color). */
    private const val PNG_ESTIMATED_BITS_PER_PIXEL = 8.0

    /** JPEG/WEBP quality 0 → khoảng 0.1 bit/pixel (nén rất mạnh, vỡ hạt). */
    private const val JPEG_MIN_BITS_PER_PIXEL = 0.1

    /** JPEG/WEBP quality 0..100 trải trên khoảng 0.1..2.0 bit/pixel. */
    private const val JPEG_QUALITY_BITS_PER_PIXEL_RANGE = 1.9

    private const val MIN_ESTIMATED_BYTES = 1024L
}
