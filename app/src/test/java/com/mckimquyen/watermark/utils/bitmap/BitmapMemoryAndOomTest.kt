package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapMemoryAndOomTest {

    @Test
    fun computeMaxSafeDimension_normalImageStandardHeap_preservesOriginal() {
        // 4000x3000 = 12 Megapixels. Trên thiết bị 256MB heap:
        // 256MB * 0.35 / 4B = 22.4 Megapixels.
        // 12MP < 22.4MP -> Không cần hạ độ phân giải, giữ nguyên kích thước gốc.
        val heap256MB = 256L * 1024L * 1024L
        val safeDim = computeMaxSafeDimension(
            width = 4000,
            height = 3000,
            reqLongEdge = 0,
            maxHeapBytes = heap256MB
        )
        assertThat(safeDim).isEqualTo(4000)
    }

    @Test
    fun computeMaxSafeDimension_extreme108MPImage_downscalesSafelyToPreventOOM() {
        // 12000x9000 = 108 Megapixels (ảnh 108MP từ camera cao cấp).
        // 108MP ARGB_8888 = 432MB RAM -> Chắc chắn gây OOM trên heap 256MB!
        val heap256MB = 256L * 1024L * 1024L
        val safeDim = computeMaxSafeDimension(
            width = 12000,
            height = 9000,
            reqLongEdge = 0,
            maxHeapBytes = heap256MB
        )
        // safeDim phải được giới hạn để tránh crash
        assertThat(safeDim).isLessThan(12000)
        assertThat(safeDim).isAtLeast(1080)

        // Kiểm tra inSampleSize tương ứng phải >= 2
        val inSample = calculateInSampleSizeForLongEdge(longEdge = 12000, reqLongEdge = safeDim)
        assertThat(inSample).isAtLeast(2)
    }

    @Test
    fun computeMaxSafeDimension_userSpecifiedResize_respectsUserChoice() {
        val heap256MB = 256L * 1024L * 1024L
        val safeDim = computeMaxSafeDimension(
            width = 4000,
            height = 3000,
            reqLongEdge = 1080,
            maxHeapBytes = heap256MB
        )
        assertThat(safeDim).isEqualTo(1080)
    }

    @Test
    fun computeMaxSafeDimension_lowMemoryDevice64MB_downscales12MPPhoto() {
        // Thiết bị cấu hình thấp (heap 64MB): giới hạn kích thước an toàn nhỏ hơn ảnh gốc
        val heap64MB = 64L * 1024L * 1024L
        val safeDim = computeMaxSafeDimension(
            width = 4000,
            height = 3000,
            reqLongEdge = 0,
            maxHeapBytes = heap64MB
        )
        assertThat(safeDim).isLessThan(4000)
        assertThat(safeDim).isAtLeast(1080)
    }

    @Test
    fun computeMaxSafeDimension_criticalMemoryDevice32MB_inSampleIsAtLeastTwo() {
        // Thiết bị bộ nhớ cực thấp (heap 32MB): inSampleSize phải tăng lên ít nhất là 2 để tránh crash
        val heap32MB = 32L * 1024L * 1024L
        val safeDim = computeMaxSafeDimension(
            width = 4000,
            height = 3000,
            reqLongEdge = 0,
            maxHeapBytes = heap32MB
        )
        assertThat(safeDim).isLessThan(4000)
        val inSample = calculateInSampleSizeForLongEdge(longEdge = 4000, reqLongEdge = safeDim)
        assertThat(inSample).isAtLeast(2)
    }

    @Test
    fun computeMaxSafeDimension_invalidDimensions_returnsRequestedEdgeSafely() {
        assertThat(computeMaxSafeDimension(width = 0, height = 0, reqLongEdge = 1080)).isEqualTo(1080)
        assertThat(computeMaxSafeDimension(width = -1, height = 1000, reqLongEdge = 0)).isEqualTo(0)
    }

    @Test
    fun bitmapCache_clearCache_evictsAllEntries() {
        val key = BitmapCache.BitmapInfo(Uri.parse("content://media/test/1"), 100, 100)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val value = BitmapCache.BitmapValue(bitmap, 1)

        BitmapCache.addToCache(key, value)
        assertThat(BitmapCache.getFromCache(key)).isNotNull()

        BitmapCache.clearCache()
        assertThat(BitmapCache.getFromCache(key)).isNull()
        assertThat(BitmapCache.currentSize()).isEqualTo(0)
    }

    @Test
    fun bitmapRecycleGuard_whenOwned_recyclesBitmapOnCleanUp() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val guard = BitmapRecycleGuard(bitmap)
        assertThat(bitmap.isRecycled).isFalse()

        guard.recycleIfOwned()
        assertThat(bitmap.isRecycled).isTrue()
    }

    @Test
    fun bitmapRecycleGuard_whenReleased_doesNotRecycleBitmap() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val guard = BitmapRecycleGuard(bitmap)

        guard.release()
        guard.recycleIfOwned()
        assertThat(bitmap.isRecycled).isFalse()
        bitmap.recycle()
    }

    @Test
    fun bitmapRecycleGuard_replace_switchesTrackedBitmap() {
        val bitmap1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val bitmap2 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)

        val guard = BitmapRecycleGuard(bitmap1)
        bitmap1.recycle() // Giả lập đã recycle bitmap cũ

        guard.replace(bitmap2)
        guard.recycleIfOwned()

        assertThat(bitmap2.isRecycled).isTrue()
    }
}
