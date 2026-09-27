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
     * Ảnh phải decode ĐÚNG kích thước gốc: lớp ẩn nằm trên lưới 8x8 của ảnh, downsample là lệch lưới
     * và mất sạch. Vì vậy KHÔNG dùng `inSampleSize` ở đây, đánh đổi bằng RAM.
     */
    private val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inMutable = false
    }

    /** `null` khi không đọc được ảnh, hoặc ảnh không mang watermark ẩn đáng tin. */
    fun read(contentResolver: ContentResolver, uri: Uri): InvisibleWatermark.Result? {
        var bitmap: Bitmap? = null
        return try {
            bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null
            InvisibleWatermark.extract(bitmap)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            bitmap?.recycle()
        }
    }
}
