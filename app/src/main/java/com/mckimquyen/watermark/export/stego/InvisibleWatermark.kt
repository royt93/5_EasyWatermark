package com.mckimquyen.watermark.export.stego

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build

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

            // ENH-41: overload nhận int[] pixel luôn gắn sRGB mặc định — ảnh Display P3 (camera/iPhone)
            // bị đọc nhầm màu. API 26+ dựng bitmap trống giữ đúng ColorSpace gốc rồi setPixels() vào;
            // API 24-25 không có overload này nên chấp nhận giới hạn nền tảng (giữ hành vi cũ).
            // QUAN TRỌNG: `Bitmap.getColorSpace()` chỉ tồn tại từ API 26 — phải check SDK_INT TRƯỚC,
            // không được gọi `source.colorSpace` vô điều kiện (NoSuchMethodError thật trên API 24-25).
            // Chỉ đi nhánh dựng bitmap mới khi THẬT SỰ cần (khác sRGB, model RGB hợp lệ cho overload
            // 5-tham-số) — ảnh sRGB (đa số ảnh thực tế) đi thẳng nhánh cũ, khỏi tốn thêm 1 lần alloc +
            // setPixels() vô ích mỗi lần export.
            val colorSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) source.colorSpace else null
            if (
                colorSpace != null &&
                colorSpace.model == ColorSpace.Model.RGB &&
                colorSpace != ColorSpace.get(ColorSpace.Named.SRGB)
            ) {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, source.hasAlpha(), colorSpace).apply {
                    setPixels(pixels, 0, width, 0, 0, width, height)
                }
            } else {
                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }
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
     *
     * ENH-38: "tuyến thứ hai" đó chỉ có tác dụng ở `rounds >= 2`. Ảnh vừa đúng 1 vòng payload (nhỏ
     * nhất được chấp nhận) luôn cho `confidence = 1.0` dù sạch hay có watermark — tuyến phòng thủ
     * THẬT ở cỡ ảnh này là MAGIC+CRC bên trong [StegoPayload], không phải ngưỡng này.
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
