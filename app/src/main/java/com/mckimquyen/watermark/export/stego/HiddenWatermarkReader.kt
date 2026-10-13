package com.mckimquyen.watermark.export.stego

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * IDEA-02: đọc watermark ẩn từ một ảnh bất kỳ trên đĩa.
 *
 * Tách khỏi `AuthenticityVerifier` (IDEA-03) một cách có chủ đích: con dấu EXIF và lớp ẩn là hai
 * tầng ĐỘC LẬP, và ca đáng giá nhất là khi tầng EXIF đã chết (mạng xã hội re-encode xoá sạch
 * metadata) mà lớp ẩn vẫn còn. Gộp chung vào một hàm sẽ khiến kết quả của tầng này che mất tầng kia.
 */
object HiddenWatermarkReader {

    /**
     * BUG-85: phân biệt "không có watermark" với "không thể kiểm tra vì ảnh quá lớn". Trước đây cả hai cùng
     * trả `null` nên ảnh 48MP+ (OOM) bị báo như sạch — âm tính giả trong công cụ truy nguồn rò rỉ.
     */
    sealed class Outcome {
        data class Found(val result: InvisibleWatermark.Result) : Outcome()
        data object NotFound : Outcome()
        data object TooLarge : Outcome()
    }

    /** Bitmap ARGB_8888 (4 byte/pixel) + `IntArray` pixel mà `InvisibleWatermark.extract()` cấp thêm (4 byte/pixel). */
    private const val PEAK_BYTES_PER_PIXEL = 8L

    /** Chỉ dùng tối đa phần này của heap cho lượt đọc, chừa chỗ cho phần còn lại của app. */
    private const val HEAP_BUDGET_PERCENT = 60L
    private const val PERCENT = 100L

    /**
     * `true` khi decode full-res [width]x[height] dự kiến nằm trong ngân sách heap [maxMemoryBytes]. Hàm thuần
     * để test ranh giới; tính bằng `Long` vì `width * height` của ảnh cực lớn tràn `Int` và lọt qua guard.
     */
    internal fun fitsInMemory(width: Int, height: Int, maxMemoryBytes: Long): Boolean {
        if (width <= 0 || height <= 0) return false
        val peakBytes = width.toLong() * height.toLong() * PEAK_BYTES_PER_PIXEL
        return peakBytes <= maxMemoryBytes * HEAP_BUDGET_PERCENT / PERCENT
    }

    /** `null` khi không đọc được ảnh, ảnh quá lớn, hoặc ảnh không mang watermark ẩn đáng tin. */
    fun read(contentResolver: ContentResolver, uri: Uri): InvisibleWatermark.Result? =
        (readOutcome(contentResolver, uri) as? Outcome.Found)?.result

    fun readOutcome(
        contentResolver: ContentResolver,
        uri: Uri,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory()
    ): Outcome {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return Outcome.NotFound
        if (!fitsInMemory(bounds.outWidth, bounds.outHeight, maxMemoryBytes)) return Outcome.TooLarge

        var bitmap: Bitmap? = null
        return try {
            bitmap = contentResolver.openInputStream(uri)?.use {
                // Review pass 12: BitmapFactory.Options KHÔNG an toàn dùng chung giữa nhiều lần
                // decode đồng thời (BitmapFactory ghi outWidth/outHeight/outConfig ngược lại vào
                // đúng instance được truyền) — trước đây là field `object` (singleton) dùng chung
                // cho MỌI lần gọi read(), 2 lần verify chạy chồng (user chọn ảnh xác thực nhanh
                // liên tiếp trước khi lượt trước xong, mỗi lượt chạy trên Dispatchers.IO riêng)
                // có thể ghi đè lẫn nhau. Tạo instance MỚI mỗi lần gọi — rẻ, và loại hẳn race.
                //
                // Ảnh phải decode ĐÚNG kích thước gốc: lớp ẩn nằm trên lưới 8x8 của ảnh, downsample
                // là lệch lưới và mất sạch. Vì vậy KHÔNG dùng `inSampleSize` ở đây, đánh đổi bằng RAM.
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                }
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return Outcome.NotFound
            InvisibleWatermark.extract(bitmap)?.let { Outcome.Found(it) } ?: Outcome.NotFound
        } catch (e: OutOfMemoryError) {
            // Lưới cuối: guard dự đoán sai (heap bị phân mảnh/app khác chiếm) vẫn không được báo như "sạch".
            e.printStackTrace()
            Outcome.TooLarge
        } catch (e: Exception) {
            e.printStackTrace()
            Outcome.NotFound
        } finally {
            bitmap?.recycle()
        }
    }
}
