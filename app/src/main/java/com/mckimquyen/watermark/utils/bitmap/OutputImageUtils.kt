package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap

/**
 * Tiện ích thuần liên quan tới định dạng & kích thước ảnh xuất.
 * Tách riêng để dễ unit test (phần tính kích thước hoàn toàn không phụ thuộc Android).
 */
object OutputImageUtils {

    /** Không giới hạn cạnh dài (giữ nguyên kích thước gốc). */
    const val RESIZE_ORIGINAL = 0

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
}
