package com.mckimquyen.watermark.ui.dlg

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
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

    @org.junit.Before
    @org.junit.After
    fun resetFileProviderCache() {
        try {
            val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            field.isAccessible = true
            (field.get(null) as? java.util.Map<*, *>)?.clear()
        } catch (_: Exception) {}
    }

    @Test
    fun bug32_btnSaveClick_persistsOutputNamePatternImmediatelyWithoutBlur() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        dialog.binding.etOutputName.setText("my_custom_export_name")
        // Lưu ý: không gọi clearFocus() hay blur etOutputName, ô vẫn giữ focus

        dialog.binding.btnSave.performClick()
        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.outputNamePattern != "my_custom_export_name" && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

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

        // Cả 3 nút phải an toàn: không mở gallery, không share, không share zip
        assertThat(dialog.binding.btnSave.isEnabled).isFalse()
        assertThat(dialog.binding.btnOpenGallery.isShown).isFalse()
        assertThat(dialog.binding.btnShareZip.isShown).isFalse()

        // Thao tác thủ công nếu có sự kiện bấm cũng không được mở activity
        dialog.performOpenGallery()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()

        dialog.performOpenShare()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()

        dialog.performOpenShareZip()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun feat20_batchSuccess_displaysShareAndShareZipButtons() = runBlocking<Unit> {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val successResult = Result.success(Uri.parse("content://output/1.jpg"))
        val successItem = ImageInfo(Uri.parse("content://media/1")).copy(
            result = successResult,
            jobState = JobState.Success(successResult)
        )
        viewModel.waterMarkRepo.updateImageList(listOf(successItem))
        shadowOf(Looper.getMainLooper()).idle()

        dialog.performSetUpLoadingView(Result.success(null, code = MainViewModel.TYPE_JOB_FINISH))

        assertThat(dialog.binding.btnSave.isEnabled).isTrue()
        assertThat(dialog.binding.btnSave.text.toString()).isEqualTo(activity.getString(com.mckimquyen.watermark.R.string.share))
        assertThat(dialog.binding.btnOpenGallery.isShown).isTrue()
        assertThat(dialog.binding.btnShareZip.isShown).isTrue()
        assertThat(dialog.binding.btnShareZip.isEnabled).isTrue()
    }

    @Test
    fun feat20_batch3ImagesSuccess_openShare_shares3ExportedImages() = runBlocking<Unit> {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val res1 = Result.success(Uri.parse("content://output/1.jpg"))
        val res2 = Result.success(Uri.parse("content://output/2.jpg"))
        val res3 = Result.success(Uri.parse("content://output/3.jpg"))

        val item1 = ImageInfo(Uri.parse("content://media/1")).copy(result = res1, jobState = JobState.Success(res1))
        val item2 = ImageInfo(Uri.parse("content://media/2")).copy(result = res2, jobState = JobState.Success(res2))
        val item3 = ImageInfo(Uri.parse("content://media/3")).copy(result = res3, jobState = JobState.Success(res3))

        viewModel.waterMarkRepo.updateImageList(listOf(item1, item2, item3))
        shadowOf(Looper.getMainLooper()).idle()

        dialog.performOpenShare()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedActivity).isNotNull()
        assertThat(nextStartedActivity.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)

        val streamUris = nextStartedActivity.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        assertThat(streamUris).containsExactly(
            Uri.parse("content://output/1.jpg"),
            Uri.parse("content://output/2.jpg"),
            Uri.parse("content://output/3.jpg")
        )
    }

    @Test
    fun feat20_batch3ImagesSuccess_openShareZip_createsAndSharesZipWith3Images() = runBlocking<Unit> {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val tempDir = java.io.File(activity.cacheDir, "test_batch_zip_${System.currentTimeMillis()}").apply { mkdirs() }
        val f1 = java.io.File(tempDir, "exported_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val f2 = java.io.File(tempDir, "exported_2.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val f3 = java.io.File(tempDir, "exported_3.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        val res1 = Result.success(Uri.fromFile(f1))
        val res2 = Result.success(Uri.fromFile(f2))
        val res3 = Result.success(Uri.fromFile(f3))

        val item1 = ImageInfo(Uri.parse("content://media/1")).copy(result = res1, jobState = JobState.Success(res1))
        val item2 = ImageInfo(Uri.parse("content://media/2")).copy(result = res2, jobState = JobState.Success(res2))
        val item3 = ImageInfo(Uri.parse("content://media/3")).copy(result = res3, jobState = JobState.Success(res3))

        viewModel.waterMarkRepo.updateImageList(listOf(item1, item2, item3))
        shadowOf(Looper.getMainLooper()).idle()

        val zipDir = com.mckimquyen.watermark.utils.ExportZipHelper.getZipCacheDir(activity)
        val customZipFile = java.io.File(zipDir, "batch_3_images_${System.currentTimeMillis()}.zip")
        dialog.performOpenShareZip(zipFileOverride = customZipFile)

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedActivity).isNotNull()
        assertThat(nextStartedActivity.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(nextStartedActivity.type).isEqualTo("application/zip")

        // Kiểm tra file ZIP chứa đúng 3 file
        assertThat(customZipFile.exists()).isTrue()
        val entryNames = mutableListOf<String>()
        java.util.zip.ZipInputStream(java.io.FileInputStream(customZipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zipIn.nextEntry
            }
        }
        assertThat(entryNames).containsExactly("exported_1.jpg", "exported_2.jpg", "exported_3.jpg")
        customZipFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun feat20_batchPartialFailure_openShareZip_packagesOnlySuccessfulImages() = runBlocking<Unit> {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val tempDir = java.io.File(activity.cacheDir, "test_partial_zip_${System.currentTimeMillis()}").apply { mkdirs() }
        val f1 = java.io.File(tempDir, "exported_ok_1.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        val f2 = java.io.File(tempDir, "exported_ok_2.jpg").apply { writeBytes(byteArrayOf(3, 4)) }

        val resSuccess1 = Result.success(Uri.fromFile(f1))
        val resSuccess2 = Result.success(Uri.fromFile(f2))
        val resFail = Result.failure<Uri>(null, message = "Encode error")

        val item1 = ImageInfo(Uri.parse("content://media/1")).copy(result = resSuccess1, jobState = JobState.Success(resSuccess1))
        val item2 = ImageInfo(Uri.parse("content://media/2")).copy(result = resFail, jobState = JobState.Failure(resFail))
        val item3 = ImageInfo(Uri.parse("content://media/3")).copy(result = resSuccess2, jobState = JobState.Success(resSuccess2))

        viewModel.waterMarkRepo.updateImageList(listOf(item1, item2, item3))
        shadowOf(Looper.getMainLooper()).idle()

        val zipDir = com.mckimquyen.watermark.utils.ExportZipHelper.getZipCacheDir(activity)
        val customZipFile = java.io.File(zipDir, "partial_batch_${System.currentTimeMillis()}.zip")
        dialog.performOpenShareZip(zipFileOverride = customZipFile)

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertThat(nextStartedActivity).isNotNull()
        assertThat(nextStartedActivity.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(nextStartedActivity.type).isEqualTo("application/zip")

        // File zip chỉ chứa 2 ảnh thành công
        val entryNames = mutableListOf<String>()
        java.util.zip.ZipInputStream(java.io.FileInputStream(customZipFile)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zipIn.nextEntry
            }
        }
        assertThat(entryNames).containsExactly("exported_ok_1.jpg", "exported_ok_2.jpg")
        customZipFile.delete()
        tempDir.deleteRecursively()
    }

    @Test
    fun feat19_selectConflictPolicy_persistsPolicyToViewModel() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        // Ban đầu mặc định là KEEP_BOTH
        assertThat(viewModel.conflictPolicy).isEqualTo(com.mckimquyen.watermark.data.model.ConflictPolicy.KEEP_BOTH)

        // Chọn item index 1: RENAME_VERSION
        dialog.binding.atvConflictPolicy.performCompletion()
        dialog.binding.atvConflictPolicy.onItemClickListener?.onItemClick(null, null, 1, 1L)

        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.conflictPolicy != com.mckimquyen.watermark.data.model.ConflictPolicy.RENAME_VERSION && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.conflictPolicy).isEqualTo(com.mckimquyen.watermark.data.model.ConflictPolicy.RENAME_VERSION)

        // Chọn item index 2: OVERWRITE
        dialog.binding.atvConflictPolicy.onItemClickListener?.onItemClick(null, null, 2, 2L)
        val deadline2 = System.currentTimeMillis() + 3_000
        while (viewModel.conflictPolicy != com.mckimquyen.watermark.data.model.ConflictPolicy.OVERWRITE && System.currentTimeMillis() < deadline2) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(viewModel.conflictPolicy).isEqualTo(com.mckimquyen.watermark.data.model.ConflictPolicy.OVERWRITE)
    }

    @Test
    fun rvResult_usesDefaultItemAnimatorWithChangeAnimationsDisabled() {
        // Bug fix: itemAnimator = null tắt hẳn add/remove animation. Chỉ nên tắt change-animation
        // (tránh flicker khi preview cập nhật dồn dập — FEAT-17), giữ add/remove mượt cho skip ảnh.
        val (_, dialog) = setupDialog()

        val itemAnimator = dialog.binding.rvResult.itemAnimator
        assertThat(itemAnimator).isInstanceOf(DefaultItemAnimator::class.java)
        assertThat((itemAnimator as DefaultItemAnimator).supportsChangeAnimations).isFalse()
    }
}
