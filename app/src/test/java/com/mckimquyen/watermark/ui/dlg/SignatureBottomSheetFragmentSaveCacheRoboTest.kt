package com.mckimquyen.watermark.ui.dlg

import android.graphics.Bitmap
import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * ENH-28: [SignatureBottomSheetFragment.saveBitmapToCache] dùng `.use {}` đảm bảo không leak file
 * descriptor kể cả khi compress() ném Exception.
 * ENH-29: [SignatureBottomSheetFragment.saveBitmapToCache] dọn dẹp các file `sig_temp_*.png` cũ,
 * giữ tối đa 3 file gần nhất để tránh tích luỹ rác vô hạn trong cacheDir.
 */
@RunWith(RobolectricTestRunner::class)
class SignatureBottomSheetFragmentSaveCacheRoboTest {

    private fun launchFragment(): SignatureBottomSheetFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = SignatureBottomSheetFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, SignatureBottomSheetFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun saveBitmapToCache_validBitmap_savesFileAndReturnsUri() {
        val fragment = launchFragment()
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)

        val uri = fragment.saveBitmapToCache(bitmap)

        assertThat(uri).isNotNull()
        val cacheDir = File(fragment.requireContext().cacheDir, "signatures")
        val files = cacheDir.listFiles()?.filter { it.name.startsWith("sig_temp_") } ?: emptyList()
        assertThat(files).isNotEmpty()
    }

    @Test
    fun saveBitmapToCache_recycledBitmap_returnsNullGracefullyWithoutCrashing() {
        val fragment = launchFragment()
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        bitmap.recycle() // Làm bitmap bị lỗi khi compress()

        val uri = fragment.saveBitmapToCache(bitmap)

        // ENH-28: compress ném exception nhưng .use{} đảm bảo stream đóng và hàm trả về null an toàn
        assertThat(uri).isNull()
    }

    @Test
    fun saveBitmapToCache_multipleTimes_prunesOldFilesKeepingWithinLimit() {
        val fragment = launchFragment()
        val cacheDir = File(fragment.requireContext().cacheDir, "signatures")
        cacheDir.mkdirs()

        // Tạo sẵn 5 file chữ ký cũ
        for (i in 1..5) {
            val oldFile = File(cacheDir, "sig_temp_$i.png")
            oldFile.writeText("fake content $i")
            oldFile.setLastModified(1000L * i)
        }
        assertThat(cacheDir.listFiles()?.filter { it.name.contains("_temp_") }?.size).isEqualTo(5)

        // Lưu 1 chữ ký mới
        val validBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val uri = fragment.saveBitmapToCache(validBitmap)

        assertThat(uri).isNotNull()
        // ENH-29: Sau khi dọn dẹp (maxRetainedFiles = 3) và ghi file mới, số file trong cache tối đa là 4 (3 file cũ mới nhất + 1 file vừa tạo)
        val remaining = cacheDir.listFiles()?.filter { it.name.contains("_temp_") } ?: emptyList()
        assertThat(remaining.size).isAtMost(4)
    }
}
