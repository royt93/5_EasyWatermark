package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUG-76: bitmap đã `recycle()` mà vẫn nằm trong [BitmapCache] làm `allocationByteCount` về 0, nên
 * `LruCache` thấy `sizeOf` lúc remove khác lúc put và ném "reporting inconsistent results". Chỉ tái hiện
 * được trên máy thật (Robolectric không mô phỏng đúng `allocationByteCount` của bitmap recycled).
 */
@RunWith(AndroidJUnit4::class)
class BitmapCacheRecycledEntryIntegrationTest {

    @After
    fun tearDown() {
        runCatching { BitmapCache.clearCache() }
    }

    @Test
    fun clearCache_afterCachedBitmapRecycledBehindCacheBack_doesNotThrow() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val info = BitmapCache.BitmapInfo(Uri.parse("file:///bug76/recycled.jpg"), SIZE, SIZE)
        BitmapCache.addToCache(info, BitmapCache.BitmapValue(bitmap, 1))

        bitmap.recycle() // vi phạm hợp đồng: bitmap còn trong cache nhưng đã bị recycle

        BitmapCache.clearCache()

        assertThat(BitmapCache.currentSize()).isEqualTo(0)
    }

    private companion object {
        const val SIZE = 256
    }
}
