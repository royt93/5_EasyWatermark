package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Looper
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkStyleHistoryRepository
import com.mckimquyen.watermark.export.BatchExportEngine
import com.mckimquyen.watermark.export.BatchExportWorker
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Bug "mất trạng thái xuất xong": sau khi export xong, dialog bị dựng lại (quay về từ app đích khi
 * Activity bị recreate, hoặc mở lại dialog) gọi [MainViewModel.reattachExportWorkIfRunning] — WorkManager
 * replay WorkInfo SUCCEEDED cũ cho observer mới. Trạng thái phải giữ nguyên JOB_FINISH, không rơi về null
 * (nút Chia sẻ/Chia sẻ nhanh biến mất, quay lại "Xuất vào bộ sưu tập").
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelExportStateRestoreRoboTest {

    /** FEAT-04: fake nhẹ — Room DAO test dành cho androidTest theo quy ước repo này. */
    private class NoopBatchHistoryDao : BatchHistoryDao {
        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: BatchHistoryEntity): Long = 0
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun trimOldest(keepCount: Int) = Unit
    }

    /** IDEA-12: fake nhẹ, mirror [NoopBatchHistoryDao]. */
    private class NoopWatermarkStyleHistoryDao : WatermarkStyleHistoryDao {
        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long = 0
        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> = emptyList()
        override suspend fun pruneKeepLatest(keep: Int) = Unit
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel
    private val savedState = SavedStateHandle()

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        val userRepo = UserConfigRepository(userDataStore)
        val styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao())
        viewModel = MainViewModel(
            appContext = context,
            userRepo = userRepo,
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = styleHistoryRepo,
            savedStateHandle = savedState
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
                    BatchExportWorker(appContext, workerParameters, waterMarkRepo, userRepo, engine, BatchHistoryRepository(NoopBatchHistoryDao()), styleHistoryRepo)
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

    private fun finishOneExport(): ImageInfo {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        val viewInfo = ViewInfo(100, 100, 0, 0, 0, 0, ImageView.ScaleType.FIT_CENTER, Matrix())
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.saveImage(context.contentResolver, viewInfo, listOf(original))
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && viewModel.saveResult.value?.code != MainViewModel.TYPE_JOB_FINISH) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        return original
    }

    @Test
    fun reattachAfterFinish_keepsJobFinishState() {
        finishOneExport()
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)

        viewModel.reattachExportWorkIfRunning()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    @Test
    fun reattachAfterFinish_calledRepeatedly_stillKeepsJobFinishState() {
        finishOneExport()
        repeat(3) {
            viewModel.reattachExportWorkIfRunning()
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    @Test
    fun freshViewModelAfterFinish_recoversJobFinishFromWorkManager() {
        finishOneExport()
        // ViewModel MỚI (Activity bị kill/recreate khi ở app khác) — chưa từng xử lý work này.
        val fresh = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao())
        )
        fresh.imageList.observeForever {}
        fresh.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        fresh.reattachExportWorkIfRunning()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fresh.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    /**
     * Phiên MỚI (tiến trình mới) với ảnh MỚI chưa xuất: WorkManager vẫn giữ WorkInfo SUCCEEDED của
     * phiên trước. ViewModel mới không được coi đó là "vừa xong" — nếu không dialog hiện nút "Chia sẻ"
     * bị vô hiệu (không ảnh nào có kết quả) và user không xuất được.
     */
    @Test
    fun freshViewModel_staleFinishedWork_doesNotMarkNewUnexportedImagesAsFinished() {
        finishOneExport()
        // Người dùng chọn ảnh MỚI (chưa có kết quả xuất) ở phiên sau.
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://does.not.exist/new.jpg")))) }
        shadowOf(Looper.getMainLooper()).idle()
        val fresh = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao())
        )
        fresh.imageList.observeForever {}
        fresh.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        fresh.reattachExportWorkIfRunning()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fresh.saveResult.value?.code).isNotEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    /**
     * CÙNG phiên, sau khi xuất thành công, dialog đóng rồi mở lại (hoặc user quay về từ app đích):
     * ViewModel vẫn giữ instance cũ. reattachExportWorkIfRunning() không được làm mất JOB_FINISH
     * và phải giữ nguyên danh sách ảnh có shareUri hợp lệ để nút Chia sẻ hoạt động.
     */
    @Test
    fun sameSession_reopenDialogAfterFinish_keepsJobFinishAndShareUris() {
        finishOneExport()
        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)

        // Mô phỏng mở lại dialog: reattach được gọi lại
        viewModel.reattachExportWorkIfRunning()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
        assertThat(waterMarkRepo.imageInfoList.any { it.result != null }).isTrue()
    }

    /**
     * Process death: Android kill process rồi dựng lại task. Repo (RAM) trống, WorkManager vẫn giữ
     * WorkInfo SUCCEEDED. ViewModel mới nhận lại cùng SavedStateHandle phải khôi phục được "đã xuất
     * xong" cùng danh sách ảnh với output URI để dialog hiện lại Chia sẻ + Chia sẻ nhanh.
     */
    @Test
    fun processDeath_restoresJobFinishAndOutputUrisFromSavedState() {
        val input = Uri.parse("content://media/external/images/media/1")
        val output = Uri.parse("content://media/external/images/media/99")
        val ok = Result.success(output)
        runBlocking {
            waterMarkRepo.updateImageList(listOf(ImageInfo(input).copy(result = ok, jobState = JobState.Success(ok))))
        }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        // Process mới: repo mới, ViewModel mới, cùng SavedStateHandle (Android khôi phục từ Bundle).
        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        val restored = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        restored.imageList.observeForever {}
        restored.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(restored.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
        val list = newRepo.imageInfoList
        assertThat(list.map { it.uri }).containsExactly(input)
        assertThat(list.single().shareUri).isEqualTo(output)
        assertThat(list.single().jobState).isInstanceOf(JobState.Success::class.java)
    }

    @Test
    fun processDeath_failedImagesAreRestoredAsFailureNotSuccess() {
        val ok = Result.success(Uri.parse("content://media/out/1"))
        val bad = Result.failure<Uri>(null, message = "decode")
        runBlocking {
            waterMarkRepo.updateImageList(
                listOf(
                    ImageInfo(Uri.parse("content://media/in/1")).copy(result = ok, jobState = JobState.Success(ok)),
                    ImageInfo(Uri.parse("content://media/in/2")).copy(result = bad, jobState = JobState.Failure(bad))
                )
            )
        }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        shadowOf(Looper.getMainLooper()).idle()

        val byUri = newRepo.imageInfoList.associateBy { it.uri.toString() }
        assertThat(byUri["content://media/in/1"]?.shareUri).isEqualTo(Uri.parse("content://media/out/1"))
        assertThat(byUri["content://media/in/2"]?.shareUri).isNull()
        assertThat(byUri["content://media/in/2"]?.jobState).isInstanceOf(JobState.Failure::class.java)
    }

    @Test
    fun processDeath_nothingPersisted_doesNotInventFinishedState() {
        val fresh = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = SavedStateHandle()
        )
        fresh.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(fresh.saveResult.value).isNull()
    }

    @Test
    fun processDeath_repoAlreadyHasImages_savedStateDoesNotOverwriteCurrentSession() {
        val ok = Result.success(Uri.parse("content://media/out/old"))
        runBlocking {
            waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/in/old")).copy(result = ok, jobState = JobState.Success(ok))))
        }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        // Repo mới ĐÃ có ảnh khác (user vừa chọn ảnh mới) — không được ghi đè bằng snapshot cũ.
        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        runBlocking { newRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/in/new")))) }
        MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(newRepo.imageInfoList.map { it.uri.toString() }).containsExactly("content://media/in/new")
    }

    /**
     * Thực tế trên máy: ngay sau khi khôi phục, observer `waterMark` của MainActivity luôn gọi
     * resetJobStatus() (vì cấu hình vừa được nạp lại). Việc đó KHÔNG được xoá trạng thái vừa khôi phục —
     * chỉ thay đổi cấu hình do người dùng mới được phép reset.
     */
    @Test
    fun processDeath_resetJobStatusRightAfterRestore_keepsRestoredFinishedState() {
        val ok = Result.success(Uri.parse("content://media/out/1"))
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/in/1")).copy(result = ok, jobState = JobState.Success(ok)))) }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        val restored = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        restored.imageList.observeForever {}
        restored.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        restored.resetJobStatus() // observer waterMark của Activity gọi ngay lần nạp đầu
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(restored.saveResult.value?.code).isEqualTo(MainViewModel.TYPE_JOB_FINISH)
        assertThat(newRepo.imageInfoList.single().shareUri).isEqualTo(Uri.parse("content://media/out/1"))
    }

    /** Người dùng đổi cấu hình SAU khi khôi phục → reset phải có tác dụng như cũ (không bị chặn mãi). */
    @Test
    fun processDeath_userChangesConfigAfterRestore_resetStillClearsFinishedState() {
        val ok = Result.success(Uri.parse("content://media/out/1"))
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/in/1")).copy(result = ok, jobState = JobState.Success(ok)))) }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        val restored = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        restored.imageList.observeForever {}
        restored.saveResult.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()

        restored.resetJobStatus() // lần nạp đầu — bị bỏ qua
        shadowOf(Looper.getMainLooper()).idle()
        restored.resetJobStatus() // người dùng đổi cấu hình — phải reset thật
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(restored.saveResult.value?.code).isNotEqualTo(MainViewModel.TYPE_JOB_FINISH)
    }

    /**
     * Editor chỉ chuyển từ launch sang chế độ chỉnh sửa khi `selectedImage` có giá trị. Sau khi khôi phục
     * ảnh đã xuất từ process death, nếu không chọn ảnh nào thì người dùng vẫn thấy màn hình chào.
     */
    @Test
    fun processDeath_restoredImagesAreSelectedSoEditorOpens() {
        val ok = Result.success(Uri.parse("content://media/out/1"))
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/in/1")).copy(result = ok, jobState = JobState.Success(ok)))) }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.persistFinishedExport()

        val newRepo = WaterMarkRepository(context, waterMarkDataStore)
        val restored = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = newRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = WatermarkStyleHistoryRepository(NoopWatermarkStyleHistoryDao()),
            savedStateHandle = savedState
        )
        restored.selectedImage.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && restored.selectedImage.value == null) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(restored.selectedImage.value?.uri).isEqualTo(Uri.parse("content://media/in/1"))
    }
}
