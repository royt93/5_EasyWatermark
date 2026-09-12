package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Looper
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.export.BatchExportEngine
import com.mckimquyen.watermark.export.BatchExportWorker
import com.mckimquyen.watermark.export.ExportNaming
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-08: `ImageInfo` bây giờ bất biến — `saveImage()`/`BatchExportEngine.generateList()` KHÔNG
 * được mutate object `ImageInfo` gốc do caller truyền vào. Verify bằng URI không tồn tại (decode
 * thất bại ngay, không cần bitmap thật) — đủ để đi qua toàn bộ luồng `jobState` Ready → Ing →
 * Failure mà không chạm nhánh vẽ watermark thật.
 *
 * ENH-01: `saveImage()` giờ enqueue qua `BatchExportWorker` (WorkManager) thay vì chạy trực tiếp
 * trong `viewModelScope` — test dùng `WorkManagerTestInitHelper` với `SynchronousExecutor` (chạy
 * work đồng bộ ngay khi enqueue, không cần thread pool thật) + 1 `WorkerFactory` thủ công (không
 * qua Hilt, vì test này không có Hilt component) để tạo `BatchExportWorker` với đúng
 * repo/engine test đang dùng.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelSaveImageImmutabilityRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        val userRepo = UserConfigRepository(context.userDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = userRepo,
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        viewModel.waterMark.observeForever {}
        viewModel.imageList.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        val engine = BatchExportEngine(context, ExportNaming())
        val testWorkerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return if (workerClassName == BatchExportWorker::class.java.name) {
                    BatchExportWorker(appContext, workerParameters, waterMarkRepo, userRepo, engine)
                } else {
                    null
                }
            }
        }
        val workConfig = Configuration.Builder()
            .setWorkerFactory(testWorkerFactory)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, workConfig)
    }

    private fun awaitJobFinished(timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.saveResult.value?.code != MainViewModel.TYPE_SAVING) return
            Thread.sleep(20)
        }
    }

    @Test
    fun saveImage_decodeFailure_neverMutatesOriginalImageInfo_postsImmutableCopies() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        val seenJobStates = mutableListOf<JobState>()
        viewModel.saveProcess.observeForever { it?.let { info -> seenJobStates.add(info.jobState) } }

        viewModel.saveImage(context.contentResolver, viewInfo, listOf(original))
        awaitJobFinished()

        // Object GỐC do test truyền vào không hề bị đổi — mọi update đi qua copy() tạo instance mới.
        assertThat(original.jobState).isEqualTo(JobState.Ready)
        assertThat(original.result).isNull()

        // saveProcess nhận đủ các bước tiến trình: Ing rồi tới Failure (decode URI không tồn tại thất bại).
        assertThat(seenJobStates).contains(JobState.Ing)
        assertThat(seenJobStates.last()).isInstanceOf(JobState.Failure::class.java)
        // Batch có 1 ảnh, ảnh đó lỗi decode → cả batch coi là "xong" (JOB_FINISH), không phải lỗi
        // tổng thể — đúng hành vi cũ: generateList() vẫn Result.success() dù từng ảnh có thể Failure.
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    /** ENH-01 AC2: `cancelSaveImage()` huỷ work qua WorkManager → `saveResult` chuyển sang lỗi CANCELLED. */
    @Test
    fun cancelSaveImage_afterEnqueue_marksWorkCancelled_postsErrorCancelledResult() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.saveImage(context.contentResolver, viewInfo, listOf(original))
        // Huỷ ngay sau enqueue (trước khi Worker kịp chạy xong) — verify cancelSaveImage() gọi
        // đúng WorkManager.cancelUniqueWork() và ViewModel bridge đúng trạng thái CANCELLED (không
        // rơi vào nhánh else generic FILE_NOT_FOUND).
        viewModel.cancelSaveImage()
        awaitJobFinished()

        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_ERROR_CANCELLED)
    }

    /**
     * ENH-01 AC1: app bị kill giữa lúc export rồi mở lại — MainViewModel MỚI (chưa từng gọi
     * saveImage()) phải tự bắt lại đúng trạng thái batch_export đang chạy/đã chạy nền từ trước.
     */
    @Test
    fun reattachExportWorkIfRunning_onFreshViewModel_picksUpWorkEnqueuedBySomeoneElse() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        // Enqueue trực tiếp qua Worker (không qua viewModel.saveImage()) — mô phỏng: instance
        // MainViewModel cũ đã enqueue rồi process bị kill, giờ app mở lại tạo ViewModel mới.
        BatchExportWorker.enqueue(context, viewInfo)

        val freshViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        freshViewModel.reattachExportWorkIfRunning()

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val code = freshViewModel.saveResult.value?.code
            if (code != null && code != MainViewModel.TYPE_SAVING) break
            Thread.sleep(20)
        }

        // Batch có 1 ảnh, ảnh lỗi decode → cả batch vẫn JOB_FINISH — nếu reattach KHÔNG hoạt động,
        // saveResult sẽ giữ null mãi (chưa từng gọi saveImage() trên instance này).
        assertThat(freshViewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    /** R5: `exportWorkObserver` dùng `observeForever` — `onCleared()` phải gỡ, không rò rỉ observer. */
    @Test
    fun onCleared_removesExportWorkObserver_soLaterWorkUpdatesDoNotLeakIntoDeadViewModel() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = ImageView.ScaleType.FIT_CENTER,
            matrix = Matrix()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.saveImage(context.contentResolver, viewInfo, listOf(original))
        // onCleared() là protected — dùng ViewModelStore.clear() (cách chuẩn androidx test) thay vì
        // đổi visibility production code chỉ để phục vụ test.
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        store.clear()
        val valueRightAfterClear = viewModel.saveResult.value

        // Work vẫn chạy tiếp thật (onCleared() không huỷ Worker, chỉ gỡ observer phía ViewModel) —
        // idle() nhiều lần để chắc chắn work đã có cơ hội chạy xong nếu observer còn sống sẽ bắt được.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        // saveResult trên viewModel ĐÃ bị onCleared() không được đổi thêm sau đó — chứng minh
        // observer thật sự đã gỡ, không còn ghi vào LiveData của instance đã chết.
        assertThat(viewModel.saveResult.value?.code).isEqualTo(valueRightAfterClear?.code)
    }
}
