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
    /** BUG-73: bảo vệ cặp get+retain / put+retain khỏi xen kẽ với clearCache()/evict. */
    private val cacheLock = Any()

    private val memoryCache: LruCache<BitmapInfo, BitmapValue> by lazy {
        object : LruCache<BitmapInfo, BitmapValue>(cacheSize) {
            override fun sizeOf(key: BitmapInfo?, value: BitmapValue?): Int {
                // BUG-76: dùng kích thước đã chốt lúc tạo value; đọc lại allocationByteCount của bitmap đã
                // recycle() cho giá trị khác lúc put → LruCache ném "reporting inconsistent results".
                return value?.sizeKb ?: super.sizeOf(key, value)
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

    private const val BYTES_PER_KB = 1024

    private val maxMemory by lazy { (Runtime.getRuntime().maxMemory() / BYTES_PER_KB).toInt() }

    val cacheSize = maxMemory / 8

    fun getFromCache(info: BitmapInfo): BitmapValue? {
        return memoryCache.get(info)
    }

    /**
     * BUG-73: lấy value VÀ `retain()` trong cùng một đoạn đồng bộ với [clearCache] — trước đây caller `get()`
     * rồi mới `retain()` ở bước sau, khoảng hở đó cho phép `clearCache()`/evict thấy `refCount == 0` và recycle
     * bitmap ngay dưới chân caller. Trả `null` nếu không có trong cache; value trả về LUÔN đã được retain nên
     * caller PHẢI `release()` đúng 1 lần khi dùng xong.
     */
    fun acquireFromCache(info: BitmapInfo): BitmapValue? = synchronized(cacheLock) {
        memoryCache.get(info)?.also { it.retain() }
    }

    /** BUG-73: cho phép [acquireFromCache] retain value vừa decode TRƯỚC khi nó có thể bị evict. */
    fun addToCacheAndAcquire(info: BitmapInfo, bitmapValue: BitmapValue): BitmapValue = synchronized(cacheLock) {
        bitmapValue.retain()
        memoryCache.put(info, bitmapValue)
        bitmapValue
    }

    fun addToCache(info: BitmapInfo, bitmapValue: BitmapValue?) {
        // LruCache.put() ném NullPointerException nếu value null (decode ảnh lỗi trả về null).
        if (bitmapValue != null) {
            memoryCache.put(info, bitmapValue)
        }
    }

    /** Xoá toàn bộ bitmap trong cache khi hệ thống cảnh báo bộ nhớ thấp hoặc kết thúc batch export. */
    fun clearCache() {
        synchronized(cacheLock) { memoryCache.evictAll() }
    }

    /** Dung lượng cache hiện tại tính theo KB. */
    fun currentSize(): Int = memoryCache.size()

    data class BitmapInfo(
        val uri: Uri,
        val reqWidth: Int,
        val reqHeight: Int
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
        /** BUG-76: KB chốt một lần lúc tạo; bitmap null hoặc đã recycle sẵn tính 1KB như mặc định của LruCache. */
        internal val sizeKb: Int = runCatching { bitmap?.allocationByteCount?.div(BYTES_PER_KB) }.getOrNull() ?: 1

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

        @androidx.annotation.VisibleForTesting
        internal fun getRefCount(): Int = refCount.get()

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
