package com.mckimquyen.watermark.ui

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Sau process death Android dựng lại task với CHÍNH intent ACTION_SEND ban đầu. Nếu repo đã có ảnh
 * (vừa khôi phục từ SavedStateHandle), Activity không được nạp đè lại ảnh share đó — nếu không kết quả
 * xuất (Chia sẻ / Chia sẻ nhanh) vừa khôi phục bị xoá ngay.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityRestoredExportRoboTest {

    private val shared = Uri.parse("content://media/external/images/media/777/shared.jpg")
    private val output = Uri.parse("content://media/external/images/media/888/out.jpg")

    private fun sendIntent() = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, shared)
    }

    private fun finishedImage(): ImageInfo {
        val ok = Result.success(output)
        return ImageInfo(shared).copy(result = ok, jobState = JobState.Success(ok))
    }

    @Test
    fun startWithShareIntent_whenRepoAlreadyHoldsExportedImages_doesNotReimportAndKeepsResult() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, sendIntent()).create()
        val activity = controller.get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        runBlocking { viewModel.waterMarkRepo.updateImageList(listOf(finishedImage())) }
        shadowOf(Looper.getMainLooper()).idle()

        controller.start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.waterMarkRepo.imageInfoList.single().shareUri).isEqualTo(output)
    }

    @Test
    fun startWithShareIntent_whenRepoEmpty_stillImportsSharedImage() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, sendIntent()).create().start().resume()
        val viewModel = ViewModelProvider(controller.get())[MainViewModel::class.java]
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && viewModel.waterMarkRepo.imageInfoList.isEmpty()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        // Share thật vẫn phải nạp được ảnh (guard không được chặn luồng bình thường).
        assertThat(viewModel.waterMarkRepo.imageInfoList.map { it.uri }).contains(shared)
    }

    /**
     * CÙNG phiên: người dùng vừa xuất xong rồi mở ảnh MỚI từ app khác (onNewIntent). Ảnh share mới
     * phải được nạp bình thường — chỉ intent dựng lại sau process death mới bị bỏ qua.
     */
    @Test
    fun onNewIntent_withNewSharedImageAfterExport_stillImportsNewImage() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        runBlocking { viewModel.waterMarkRepo.updateImageList(listOf(finishedImage())) }
        shadowOf(Looper.getMainLooper()).idle()

        val another = Uri.parse("content://media/external/images/media/999/another.jpg")
        val newIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, another)
        }
        controller.newIntent(newIntent)
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && viewModel.waterMarkRepo.imageInfoList.none { it.uri == another }) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.waterMarkRepo.imageInfoList.map { it.uri }).contains(another)
    }
}
