package com.mckimquyen.watermark.ui.dlg

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-38: `ComparePreviewBottomSheetFragment` không recycle 2 bitmap compare — leak mỗi lần mở.
 * Robolectric shadow `BitmapFactory` decode MỌI uri thành bitmap giả (không rasterize thật, xem
 * cùng giới hạn môi trường đã ghi ở `BatchExportEngineCompareRoboTest`) nên `originalBitmap`/
 * `watermarkedBitmap` LUÔN có giá trị thật để assert `isRecycled`, không cần mock thêm.
 */
@RunWith(RobolectricTestRunner::class)
class ComparePreviewBottomSheetFragmentRoboTest {

    private fun setup(): Pair<MainActivity, ComparePreviewBottomSheetFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val fragment = ComparePreviewBottomSheetFragment().apply {
            setShowsDialog(false)
            arguments = Bundle().apply {
                putString("arg_uri", Uri.parse("content://test/1").toString())
                putInt("arg_index", 0)
            }
        }
        activity.supportFragmentManager.beginTransaction()
            .add(fragment, ComparePreviewBottomSheetFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun huyView_saKhiRenderXong_recycleCa2Bitmap() {
        val (_, fragment) = setup()
        val original = fragment.originalBitmap
        val watermarked = fragment.watermarkedBitmap
        assertThat(original).isNotNull()
        assertThat(watermarked).isNotNull()
        assertThat(original!!.isRecycled).isFalse()
        assertThat(watermarked!!.isRecycled).isFalse()

        fragment.parentFragmentManager.beginTransaction().remove(fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.view).isNull()
        assertThat(original.isRecycled).isTrue()
        assertThat(watermarked.isRecycled).isTrue()
    }

    @Test
    fun huyViewNgayLapTuc_truocKhiRenderXong_khongCrashVaKhongSetVaoViewDaHuy() {
        val (activity, fragment) = setup()

        // Huỷ ngay trong cùng 1 lượt idle main looper — mô phỏng user đóng sheet sớm trong lúc
        // coroutine generateCompareBitmaps() còn chạy.
        activity.supportFragmentManager.beginTransaction().remove(fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.view).isNull()
        // Không crash tới đây là đã chứng minh AC3 (mở/đóng liên tục không "trying to use a
        // recycled bitmap"); field không bị set giá trị mồ côi sau khi view đã huỷ.
        assertThat(fragment.originalBitmap).isNull()
        assertThat(fragment.watermarkedBitmap).isNull()
    }

    @Test
    fun moDongLienTuc3Lan_khongCrash() {
        repeat(3) {
            val (activity, fragment) = setup()
            activity.supportFragmentManager.beginTransaction().remove(fragment).commit()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(fragment.view).isNull()
        }
    }
}
