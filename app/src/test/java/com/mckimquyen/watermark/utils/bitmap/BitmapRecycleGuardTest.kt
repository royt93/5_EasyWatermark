package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-21: `generateImage()` phải recycle bitmap đã cấp phát ở MỌI nhánh early-return (lỗi
 * config/icon/MediaStore), không chỉ nhánh thành công (đã xử lý ở BUG-05). [BitmapRecycleGuard]
 * là cơ chế chung đảm bảo điều này — test trực tiếp cơ chế (không cần ContentResolver/MediaStore
 * thật) để chứng minh đúng behavior của guard, độc lập với luồng generateImage() cụ thể.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapRecycleGuardTest {

    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun recycleIfOwned_recyclesInitialBitmap_whenNeverReplacedOrReleased() {
        val bmp = bitmap()
        val guard = BitmapRecycleGuard(bmp)

        guard.recycleIfOwned()

        assertThat(bmp.isRecycled).isTrue()
    }

    @Test
    fun replace_tracksNewBitmap_recycleIfOwnedOnlyAffectsLatest() {
        val old = bitmap()
        val new = bitmap()
        val guard = BitmapRecycleGuard(old)

        // Giả lập luồng thật: bitmap cũ tự recycle thủ công trước khi chuyển giao (như EXIF
        // border / resize trong generateImage()).
        old.recycle()
        guard.replace(new)
        guard.recycleIfOwned()

        assertThat(new.isRecycled).isTrue()
    }

    @Test
    fun release_thenRecycleIfOwned_doesNotTouchBitmap() {
        // Mô phỏng nhánh THÀNH CÔNG: bitmap đã được compress + recycle thủ công, guard.release()
        // được gọi trước khi hàm return — finally không được đụng vào bitmap (đã bị recycle rồi,
        // gọi lại .recycle() 2 lần sẽ ném IllegalStateException trên Android thật).
        val bmp = bitmap()
        val guard = BitmapRecycleGuard(bmp)

        bmp.recycle()
        guard.release()
        guard.recycleIfOwned() // không được ném exception, không recycle lại

        assertThat(bmp.isRecycled).isTrue()
    }

    @Test
    fun recycleIfOwned_calledTwice_doesNotThrow() {
        val bmp = bitmap()
        val guard = BitmapRecycleGuard(bmp)

        guard.recycleIfOwned()
        guard.recycleIfOwned() // an toàn khi finally chạy nhiều lần / gọi lặp

        assertThat(bmp.isRecycled).isTrue()
    }

    @Test
    fun simulatedEarlyReturnAfterAllocation_bitmapStillRecycled() {
        // Tái hiện đúng bug BUG-21: bitmap được cấp phát, sau đó luồng xử lý "return sớm" (ở đây
        // mô phỏng bằng exception) trước khi tới nhánh recycle thành công — finally vẫn phải dọn.
        val bmp = bitmap()
        val guard = BitmapRecycleGuard(bmp)

        try {
            try {
                error("simulate lỗi config/icon/MediaStore giữa chừng")
            } finally {
                guard.recycleIfOwned()
            }
        } catch (_: IllegalStateException) {
            // expected — chỉ quan tâm bitmap có được recycle hay không
        }

        assertThat(bmp.isRecycled).isTrue()
    }
}
