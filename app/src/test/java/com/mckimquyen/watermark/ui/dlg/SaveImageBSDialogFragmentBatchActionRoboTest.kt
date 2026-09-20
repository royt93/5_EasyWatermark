package com.mckimquyen.watermark.ui.dlg

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-32: Bấm nút Export lưu ngay outputNamePattern từ etOutputName mà không cần blur ô nhập.
 * BUG-33: Xử lý nút mở Gallery và Share an toàn khi batch có ảnh lỗi hoặc toàn bộ batch lỗi.
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentBatchActionRoboTest {

    private fun setupDialog(): Pair<MainActivity, SaveImageBSDialogFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val dialogFragment = SaveImageBSDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(dialogFragment, "SaveImageBSDialogFragmentTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun bug32_btnSaveClick_persistsOutputNamePatternImmediatelyWithoutBlur() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        dialog.binding.etOutputName.setText("my_custom_export_name")
        // Lưu ý: không gọi clearFocus() hay blur etOutputName, ô vẫn giữ focus

        dialog.binding.btnSave.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.outputNamePattern).isEqualTo("my_custom_export_name")
    }

    @Test
    fun bug33_batchWithFirstImageFailed_openGalleryOpensFirstSuccessfulImage() = runBlocking {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val failResult = Result.failure<Uri>(null, message = "File not found")
        val successResult = Result.success(Uri.parse("content://output/2.jpg"))

        val failedItem = ImageInfo(Uri.parse("content://media/1")).copy(
            result = failResult,
            jobState = JobState.Failure(failResult)
        )
        val successItem = ImageInfo(Uri.parse("content://media/2")).copy(
            result = successResult,
            jobState = JobState.Success(successResult)
        )

        viewModel.waterMarkRepo.updateImageList(listOf(failedItem, successItem))
        shadowOf(Looper.getMainLooper()).idle()

        dialog.performOpenGallery()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedActivity).isNotNull()
        assertThat(nextStartedActivity.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(nextStartedActivity.data).isEqualTo(Uri.parse("content://output/2.jpg"))
    }

    @Test
    fun bug33_batchWithFirstImageFailed_openShareSharesOnlySuccessfulImages() = runBlocking {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val failResult = Result.failure<Uri>(null, message = "File not found")
        val successResult = Result.success(Uri.parse("content://output/2.jpg"))

        val failedItem = ImageInfo(Uri.parse("content://media/1")).copy(
            result = failResult,
            jobState = JobState.Failure(failResult)
        )
        val successItem = ImageInfo(Uri.parse("content://media/2")).copy(
            result = successResult,
            jobState = JobState.Success(successResult)
        )

        viewModel.waterMarkRepo.updateImageList(listOf(failedItem, successItem))
        shadowOf(Looper.getMainLooper()).idle()

        dialog.performOpenShare()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedActivity).isNotNull()
        // Chỉ có 1 ảnh thành công -> ACTION_SEND (thay vì ACTION_SEND_MULTIPLE rỗng)
        assertThat(nextStartedActivity.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(nextStartedActivity.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isEqualTo(Uri.parse("content://output/2.jpg"))
    }

    @Test
    fun bug33_batchAllFailed_disablesShareButtonAndHidesGalleryButton() = runBlocking {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val failResult1 = Result.failure<Uri>(null, message = "Error 1")
        val failResult2 = Result.failure<Uri>(null, message = "Error 2")

        val failedItem1 = ImageInfo(Uri.parse("content://media/1")).copy(
            result = failResult1,
            jobState = JobState.Failure(failResult1)
        )
        val failedItem2 = ImageInfo(Uri.parse("content://media/2")).copy(
            result = failResult2,
            jobState = JobState.Failure(failResult2)
        )
        viewModel.waterMarkRepo.updateImageList(listOf(failedItem1, failedItem2))
        shadowOf(Looper.getMainLooper()).idle()

        dialog.performSetUpLoadingView(Result.success(null, code = MainViewModel.TYPE_JOB_FINISH))

        // Cả 2 nút phải an toàn: không mở gallery, không share
        assertThat(dialog.binding.btnSave.isEnabled).isFalse()
        assertThat(dialog.binding.btnOpenGallery.isShown).isFalse()

        // Thao tác thủ công nếu có sự kiện bấm cũng không được mở activity
        dialog.performOpenGallery()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()

        dialog.performOpenShare()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }
}
