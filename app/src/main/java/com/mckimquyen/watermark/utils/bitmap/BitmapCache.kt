package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple Bitmap cache.
 * @author roy.mobile.dev@gmail.com
 */
object BitmapCache {
    private val memoryCache: LruCache<BitmapInfo, BitmapValue> by lazy {
        object : LruCache<BitmapInfo, BitmapValue>(cacheSize) {
            override fun sizeOf(key: BitmapInfo?, value: BitmapValue?): Int {
                return if (value?.bitmap == null) {
                    super.sizeOf(key, value)
                } else {
                    value.bitmap.allocationByteCount / 1024
                }
            }

            // ENH-15: khi LRU đầy (batch nhiều ảnh) evict 1 entry, đánh dấu evicted trên chính
            // BitmapValue đó — CHƯA recycle ngay nếu vẫn còn consumer giữ tham chiếu (refCount >
            // 0, xem WaterMarkImageView.iconBitmap/mainImageBitmapValue), tránh crash "trying to
            // use a recycled bitmap" khi consumer đó vẽ frame tiếp theo.
            override fun entryRemoved(
                evicted: Boolean,
                key: BitmapInfo?,
                oldValue: BitmapValue?,
                newValue: BitmapValue?
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                if (evicted && oldValue != null && oldValue !== newValue) {
                    oldValue.markEvictedAndRecycleIfUnused()
                }
            }
        }
    }

    private val maxMemory by lazy { (Runtime.getRuntime().maxMemory() / 1024).toInt() }

    val cacheSize = maxMemory / 8

    fun getFromCache(info: BitmapInfo): BitmapValue? {
        return memoryCache.get(info)
    }

    fun addToCache(info: BitmapInfo, bitmapValue: BitmapValue?) {
        // LruCache.put() ném NullPointerException nếu value null (decode ảnh lỗi trả về null).
        if (bitmapValue != null) {
            memoryCache.put(info, bitmapValue)
        }
    }

    data class BitmapInfo(
        val uri: Uri,
        val reqWidth: Int,
        val reqHeight: Int,
    )

    data class BitmapValue(
        val bitmap: Bitmap?,
        val inSampleSize: Int,
        var exifModel: com.mckimquyen.watermark.data.model.ExifModel? = null
    ) {
        // ENH-15: reference counting tối thiểu — chỉ recycle() bitmap khi ĐÃ bị evict khỏi cache
        // VÀ không còn consumer nào đang giữ tham chiếu (refCount về 0). 2 điều kiện đều cần vì
        // thứ tự xảy ra trước/sau không cố định (có thể evict trước rồi release sau, hoặc release
        // hết trước rồi mới bị evict).
        private val refCount = AtomicInteger(0)
        private val evictedFromCache = AtomicBoolean(false)

        /** Gọi khi bắt đầu dùng (giữ tham chiếu dài hạn, vd gán vào field của View/ViewModel). */
        fun retain() {
            refCount.incrementAndGet()
        }

        /** Gọi khi dùng xong (thay bằng bitmap khác, hoặc consumer bị huỷ/detach). */
        fun release() {
            if (refCount.decrementAndGet() <= 0 && evictedFromCache.get()) {
                recycleNow()
            }
        }

        internal fun markEvictedAndRecycleIfUnused() {
            evictedFromCache.set(true)
            if (refCount.get() <= 0) {
                recycleNow()
            }
        }

        private fun recycleNow() {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }
}
