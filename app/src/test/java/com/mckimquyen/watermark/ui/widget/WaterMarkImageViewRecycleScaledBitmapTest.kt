package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-AUDIT-2026-09-29-R6: `buildIconBitmapShader()` trước đây không recycle bitmap trung gian
 * `scaleBitmap` (từ `Bitmap.createScaledBitmap()`) sau khi vẽ xong — leak 1 bitmap mỗi lần đổi
 * icon/pinch-scale. `recycleScaledBitmapIfDistinct()` tách guard riêng: chỉ recycle khi bitmap
 * scale KHÁC bitmap nguồn (createScaledBitmap có thể trả về chính nguồn khi cùng kích thước).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewRecycleScaledBitmapTest {

    @Test
    fun recycleScaledBitmapIfDistinct_differentInstance_recyclesScaled() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888)

        WaterMarkImageView.recycleScaledBitmapIfDistinct(scaled, source)

        assertThat(scaled.isRecycled).isTrue()
        assertThat(source.isRecycled).isFalse()
    }

    @Test
    fun recycleScaledBitmapIfDistinct_sameInstance_doesNotRecycleCallerOwnedSource() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

        // Mô phỏng trường hợp createScaledBitmap() trả về CHÍNH source (không copy).
        WaterMarkImageView.recycleScaledBitmapIfDistinct(source, source)

        assertThat(source.isRecycled).isFalse()
    }
}
