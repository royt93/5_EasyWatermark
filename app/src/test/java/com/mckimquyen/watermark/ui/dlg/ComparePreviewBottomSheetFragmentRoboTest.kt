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

    /**
     * BUG-AUDIT-2026-09-29: `applyReveal()` khi width/height view còn 0 (chưa layout xong) tự
     * `post{}` lặp lại chính nó — nếu callback trễ đó chạy SAU khi `onDestroyView()` đã set
     * `binding` null (user đóng sheet trước khi callback chạy), truy cập `binding.ivWatermarked`
     * ném NPE. Test gọi thẳng `applyReveal()` (reflection, hàm private) SAU khi view đã huỷ hẳn —
     * mô phỏng đúng tình huống callback trễ, xác nhận guard `view == null` chặn được không crash.
     */
    @Test
    fun applyReveal_goiSauKhiViewDaHuy_khongNemNpe() {
        val (activity, fragment) = setup()
        activity.supportFragmentManager.beginTransaction().remove(fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(fragment.view).isNull()

        val method = ComparePreviewBottomSheetFragment::class.java.getDeclaredMethod("applyReveal", Float::class.java)
        method.isAccessible = true
        // Không throw (InvocationTargetException bọc NPE) tới đây là đã chứng minh guard hoạt động.
        method.invoke(fragment, 0.5f)
    }
}
