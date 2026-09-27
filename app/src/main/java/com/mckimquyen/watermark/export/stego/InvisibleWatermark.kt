package com.mckimquyen.watermark.export.stego

import android.graphics.Bitmap

/**
 * IDEA-02: cầu nối giữa [StegoCodec] (thuần JVM, làm việc trên `IntArray`) và `Bitmap` của Android.
 *
 * Tách riêng để phần thuật toán không dính Android và test được thẳng trên JVM; ở đây chỉ còn việc
 * lấy pixel ra, gọi codec, ghi pixel vào.
 */
object InvisibleWatermark {

    /**
     * Nhúng ID chủ sở hữu vào [source], trả về bitmap MỚI mang watermark ẩn, hoặc `null` nếu không
     * nhúng được (ảnh quá nhỏ, hết bộ nhớ).
     *
     * Trả bitmap mới thay vì sửa tại chỗ vì [source] có thể là bitmap bất biến (`resizeIfNeeded` trả
     * về bitmap từ `createScaledBitmap` không mutable). Phía gọi chịu trách nhiệm recycle bitmap cũ
     * và cập nhật `BitmapRecycleGuard`.
     *
     * Không bao giờ ném: watermark ẩn là tính năng phụ trợ, hỏng thì bỏ qua chứ không được làm đổ cả
     * lượt export.
     */
    fun embed(source: Bitmap, ownerId: Int): Bitmap? {
        val width = source.width
        val height = source.height
        if (StegoCodec.capacityBits(width, height) < StegoPayload.TOTAL_BITS) return null

        return try {
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            if (!StegoCodec.encode(pixels, width, height, StegoPayload.encode(ownerId))) return null

            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            // Ảnh 12MP tốn thêm ~48MB cho mảng pixel; máy yếu có thể không kham nổi.
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Đọc watermark ẩn từ [source]. Trả `null` khi ảnh quá nhỏ, không có watermark, payload hỏng,
     * hoặc độ tin cậy dưới [StegoPayload.MIN_CONFIDENCE].
     *
     * Ngưỡng tin cậy là tuyến phòng thủ thứ hai sau MAGIC+CRC: thà không đọc được còn hơn gán nhầm
     * chủ sở hữu cho ảnh của người khác.
     */
    fun extract(source: Bitmap): Result? {
        val width = source.width
        val height = source.height
        if (StegoCodec.capacityBits(width, height) < StegoPayload.TOTAL_BITS) return null

        return try {
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            val decoded = StegoCodec.decode(pixels, width, height, StegoPayload.TOTAL_BITS) ?: return null
            if (decoded.confidence < StegoPayload.MIN_CONFIDENCE) return null
            val payload = StegoPayload.decode(decoded.bits) ?: return null

            Result(ownerId = payload.ownerId, confidence = decoded.confidence, rounds = decoded.rounds)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class Result(
        val ownerId: Int,
        val confidence: Double,
        val rounds: Int
    ) {
        /** Dạng hiển thị cho người dùng — ID rút gọn 8 chữ số hex. */
        fun ownerIdHex(): String = "%08x".format(ownerId)
    }
}
