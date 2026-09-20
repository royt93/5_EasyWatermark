package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-28: [WaterMarkImageView.reset()] (được MainActivity.resetView() gọi khi huỷ quay về LaunchMode)
 * phải release refcount của cả mainImageBitmapValue và iconBitmapValue giống onDetachedFromWindow().
 * Nếu không release, bitmap "mồ côi" giữ refCount > 0 và BitmapCache không thể recycle() khi bị evict.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewResetRoboTest {

    @Test
    fun reset_releasesMainImageAndIconBitmapRefCounts_andAllowsRecycleOnEviction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageView = WaterMarkImageView(context)

        val mainBmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val iconBmp = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)

        val mainValue = BitmapCache.BitmapValue(mainBmp, 1).apply { retain() }
        val iconValue = BitmapCache.BitmapValue(iconBmp, 1).apply { retain() }

        imageView.setMainImageBitmapValueForTesting(mainValue)
        imageView.setIconBitmapValueForTesting(iconValue)

        assertThat(mainValue.getRefCount()).isEqualTo(1)
        assertThat(iconValue.getRefCount()).isEqualTo(1)

        // Evict từ cache trước: do refCount > 0, chưa bị recycle
        mainValue.markEvictedAndRecycleIfUnused()
        iconValue.markEvictedAndRecycleIfUnused()
        assertThat(mainBmp.isRecycled).isFalse()
        assertThat(iconBmp.isRecycled).isFalse()

        // Khi reset() được gọi (người dùng bấm huỷ quay về LaunchMode):
        imageView.reset()

        // Phải được release về 0, đồng thời kích hoạt recycle do đã bị evict
        assertThat(imageView.getMainImageBitmapValue()).isNull()
        assertThat(imageView.getIconBitmapValue()).isNull()
        assertThat(mainValue.getRefCount()).isEqualTo(0)
        assertThat(iconValue.getRefCount()).isEqualTo(0)
        assertThat(mainBmp.isRecycled).isTrue()
        assertThat(iconBmp.isRecycled).isTrue()
    }

    @Test
    fun reset_repeatedMultipleTimes_doesNotAccumulateOrCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val imageView = WaterMarkImageView(context)

        // Mô phỏng lặp lại chọn ảnh -> huỷ 5 lần liên tiếp
        for (i in 1..5) {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val value = BitmapCache.BitmapValue(bmp, 1).apply { retain() }
            imageView.setMainImageBitmapValueForTesting(value)
            value.markEvictedAndRecycleIfUnused()

            imageView.reset()

            assertThat(value.getRefCount()).isEqualTo(0)
            assertThat(bmp.isRecycled).isTrue()
        }
    }
}
