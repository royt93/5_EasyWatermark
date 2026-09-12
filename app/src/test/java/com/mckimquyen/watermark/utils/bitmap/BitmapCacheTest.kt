package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test (Robolectric, cần Bitmap/Uri/LruCache thật) cho [BitmapCache] — bug NPE khi
 * decode ảnh lỗi trả về `bitmapValue = null` bị put thẳng vào LruCache (BUG-02).
 *
 * [BitmapCache] là `object` singleton (LruCache dùng chung xuyên suốt các test method
 * trong cùng class), nên mỗi test dùng [BitmapCache.BitmapInfo] với URI riêng để tránh
 * đụng cache của nhau.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapCacheTest {

    @Test
    fun addToCache_nullBitmapValue_doesNotThrow() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://test/null-value"), 100, 100)

        // Trước fix: LruCache.put(info, null) ném NullPointerException ngay tại đây.
        BitmapCache.addToCache(info, null)

        assertThat(BitmapCache.getFromCache(info)).isNull()
    }

    @Test
    fun addToCache_validBitmapValue_isRetrievableFromCache() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://test/valid-value"), 100, 100)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 1)

        BitmapCache.addToCache(info, value)

        assertThat(BitmapCache.getFromCache(info)).isEqualTo(value)
    }

    @Test
    fun getFromCache_missingKey_returnsNull() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://test/never-added"), 100, 100)

        assertThat(BitmapCache.getFromCache(info)).isNull()
    }

    @Test
    fun addToCache_nullThenValid_secondCallStillCachesNormally() {
        // Mô phỏng đúng luồng thật: decode lần đầu lỗi (null), lần sau (ảnh khác cùng key
        // do retry) decode thành công — cache phải hoạt động bình thường sau lần null.
        val info = BitmapCache.BitmapInfo(Uri.parse("content://test/retry-after-null"), 100, 100)
        BitmapCache.addToCache(info, null)

        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 2)
        BitmapCache.addToCache(info, value)

        assertThat(BitmapCache.getFromCache(info)).isEqualTo(value)
    }

    // ══ ENH-15 — reference counting: chỉ recycle() khi ĐÃ evict VÀ không còn consumer giữ ══

    @Test
    fun retainedBitmapValue_markedEvicted_doesNotRecycleUntilReleased() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 1)
        value.retain() // consumer đang dùng (vd WaterMarkImageView.iconBitmap đang được vẽ)

        value.markEvictedAndRecycleIfUnused() // LRU evict entry này trong lúc đang dùng

        assertThat(bitmap.isRecycled).isFalse() // chưa recycle — còn refCount > 0, tránh crash

        value.release() // consumer dùng xong (vd đổi sang ảnh/icon khác)

        assertThat(bitmap.isRecycled).isTrue() // giờ mới thật sự giải phóng
    }

    @Test
    fun unretainedBitmapValue_markedEvicted_recyclesImmediately() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 1)
        // Không ai retain() (không consumer nào đang giữ) — evict xong phải recycle NGAY, đúng
        // mục tiêu ticket (giải phóng bộ nhớ native heap nhanh hơn, không đợi GC/finalizer).
        value.markEvictedAndRecycleIfUnused()

        assertThat(bitmap.isRecycled).isTrue()
    }

    @Test
    fun releaseWithoutEviction_doesNotRecycle() {
        // Chưa bị evict (vẫn còn "sống" trong cache, có thể trả ra lần sau qua cache hit) —
        // release() một mình (refCount về 0) KHÔNG được tự ý recycle nếu chưa evict.
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 1)
        value.retain()
        value.release()

        assertThat(bitmap.isRecycled).isFalse()
    }

    @Test
    fun multipleConsumers_recycleOnlyAfterAllRelease() {
        // 2 nơi cùng giữ tham chiếu 1 lúc (vd main image + icon trùng đúng 1 BitmapInfo hiếm gặp)
        // — evict xong vẫn phải chờ ĐỦ 2 lần release mới recycle.
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap = bitmap, inSampleSize = 1)
        value.retain()
        value.retain()
        value.markEvictedAndRecycleIfUnused()

        value.release()
        assertThat(bitmap.isRecycled).isFalse() // còn 1 consumer nữa chưa release

        value.release()
        assertThat(bitmap.isRecycled).isTrue()
    }
}
