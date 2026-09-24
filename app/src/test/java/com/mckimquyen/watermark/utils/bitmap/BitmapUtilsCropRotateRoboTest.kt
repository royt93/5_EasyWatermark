package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-16: [applyCropAndRotate] — dùng chung cho preview ([com.mckimquyen.watermark.ui.widget.WaterMarkImageView])
 * lẫn export ([com.mckimquyen.watermark.export.BatchExportEngine]). Trọng tâm test: (1) fast-path
 * không copy khi không chỉnh gì, (2) kích thước đúng sau crop/rotate, (3) KHÔNG BAO GIỜ recycle
 * bitmap [src] truyền vào — bug thật từng phát hiện lúc code review: `WaterMarkImageView` truyền
 * vào bitmap đang được `BitmapCache` giữ refcount, recycle nhầm bitmap đó sẽ phá cache.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsCropRotateRoboTest {

    private fun bitmap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    @Test
    fun applyCropAndRotate_noChange_returnsSameInstance_noCopy() {
        val src = bitmap(100, 200)

        val result = applyCropAndRotate(src, rotationDegrees = 0f, cropRect = null)

        assertThat(result).isSameInstanceAs(src)
        assertThat(src.isRecycled).isFalse()
    }

    @Test
    fun applyCropAndRotate_cropOnly_returnsCroppedDimensions_srcNeverRecycled() {
        val src = bitmap(100, 200)
        val cropRect = RectF(0.25f, 0.25f, 0.75f, 0.75f)

        val result = applyCropAndRotate(src, rotationDegrees = 0f, cropRect = cropRect)

        assertThat(result.width).isEqualTo(50)
        assertThat(result.height).isEqualTo(100)
        assertThat(src.isRecycled).isFalse()
    }

    @Test
    fun applyCropAndRotate_rotation90_swapsDimensions_srcNeverRecycled() {
        val src = bitmap(100, 200)

        val result = applyCropAndRotate(src, rotationDegrees = 90f, cropRect = null)

        assertThat(result.width).isEqualTo(200)
        assertThat(result.height).isEqualTo(100)
        assertThat(src.isRecycled).isFalse()
    }

    @Test
    fun applyCropAndRotate_rotationThenCrop_cropAppliesAgainstRotatedBitmap_srcNeverRecycled() {
        val src = bitmap(100, 200)
        // Sau khi xoay 90 độ, kích thước làm việc là 200x100 — cropRect tính theo khung ĐÃ xoay.
        val cropRect = RectF(0f, 0f, 0.5f, 1f)

        val result = applyCropAndRotate(src, rotationDegrees = 90f, cropRect = cropRect)

        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(100)
        assertThat(src.isRecycled).isFalse()
    }

    @Test
    fun applyCropAndRotate_cropRectOutOfBounds_clampsWithoutCrashing() {
        val src = bitmap(100, 200)
        val cropRect = RectF(-0.5f, -0.5f, 1.5f, 1.5f)

        val result = applyCropAndRotate(src, rotationDegrees = 0f, cropRect = cropRect)

        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(200)
        assertThat(src.isRecycled).isFalse()
    }
}
