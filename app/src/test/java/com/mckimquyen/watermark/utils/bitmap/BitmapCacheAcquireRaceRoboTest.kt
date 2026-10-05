package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-73 (giả thuyết cần tái hiện, mô phỏng TẤT ĐỊNH — không dựa may rủi thread): `getFromCache()` trả
 * `BitmapValue` khi `refCount` còn 0; caller chỉ `retain()` SAU khi hàm suspend trả về. Nếu giữa 2 bước
 * có `clearCache()` (kết thúc batch / onTrimMemory) hoặc evict, `markEvictedAndRecycleIfUnused()` thấy
 * refCount 0 và recycle bitmap → caller retain một value mà bitmap đã bị recycle.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapCacheAcquireRaceRoboTest {

    @After
    fun tearDown() = BitmapCache.clearCache()

    private fun newValue(): BitmapCache.BitmapValue =
        BitmapCache.BitmapValue(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888), inSampleSize = 1)

    /**
     * Luồng CŨ (get rồi retain muộn) là nguồn gốc BUG-73 — test này MÔ TẢ khe hở để chứng minh `getFromCache` thô
     * không an toàn (đây là lý do production không còn dùng nó giữa get và retain). Giữ lại làm tài liệu sống.
     */
    @Test
    fun legacyGetThenLateRetain_isUnsafe_whichIsWhyProductionUsesAcquire() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://race/legacy"), 100, 100)
        BitmapCache.addToCache(info, newValue())

        val fetched = BitmapCache.getFromCache(info)!! // KHÔNG retain ngay
        BitmapCache.clearCache() // xen vào giữa
        fetched.retain()

        assertThat(fetched.bitmap!!.isRecycled).isTrue() // chứng minh khe hở của API thô
    }

    /** Luồng MỚI (BUG-73): acquire nguyên tử — clearCache xen vào KHÔNG được recycle value đã acquire. */
    @Test
    fun acquireFromCache_thenClearCache_bitmapIsNotRecycled_andRecycledOnlyAfterRelease() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://race/acquire"), 100, 100)
        BitmapCache.addToCache(info, newValue())

        val acquired = BitmapCache.acquireFromCache(info)!!
        BitmapCache.clearCache()

        assertThat(acquired.getRefCount()).isEqualTo(1)
        assertThat(acquired.bitmap!!.isRecycled).isFalse()
        acquired.release()
        assertThat(acquired.bitmap!!.isRecycled).isTrue()
    }

    @Test
    fun addToCacheAndAcquire_thenClearCache_bitmapIsNotRecycled() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://race/add-acquire"), 100, 100)

        val acquired = BitmapCache.addToCacheAndAcquire(info, newValue())
        BitmapCache.clearCache()

        assertThat(acquired.bitmap!!.isRecycled).isFalse()
        acquired.release()
        assertThat(acquired.bitmap!!.isRecycled).isTrue()
    }

    @Test
    fun acquireFromCache_missingKey_returnsNull() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://race/missing"), 100, 100)

        assertThat(BitmapCache.acquireFromCache(info)).isNull()
    }

    @Test
    fun valueFetchedFromCache_thenEvictedByNewEntry_beforeRetain_bitmapMustNotBeRecycled() {
        val first = BitmapCache.BitmapInfo(Uri.parse("content://race/evict-a"), 100, 100)
        BitmapCache.addToCache(first, newValue())
        val fetched = BitmapCache.getFromCache(first)!!

        // Ép evict: nhét nhiều entry lớn cho tới khi `first` bị đẩy khỏi LruCache.
        var i = 0
        while (BitmapCache.getFromCache(first) != null && i < MAX_FILL) {
            val big = BitmapCache.BitmapValue(Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888), 1)
            BitmapCache.addToCache(BitmapCache.BitmapInfo(Uri.parse("content://race/fill-$i"), 100, 100), big)
            i++
        }
        fetched.retain()

        assertThat(fetched.bitmap!!.isRecycled).isFalse()
    }

    @Test
    fun control_valueRetainedBeforeClearCache_isNotRecycled_andRecycledAfterRelease() {
        val info = BitmapCache.BitmapInfo(Uri.parse("content://race/control"), 100, 100)
        BitmapCache.addToCache(info, newValue())
        val fetched = BitmapCache.getFromCache(info)!!
        fetched.retain() // retain TRƯỚC khi clear — luồng đúng

        BitmapCache.clearCache()
        assertThat(fetched.bitmap!!.isRecycled).isFalse()

        fetched.release()
        assertThat(fetched.bitmap!!.isRecycled).isTrue()
    }

    private companion object {
        const val MAX_FILL = 400
    }
}
