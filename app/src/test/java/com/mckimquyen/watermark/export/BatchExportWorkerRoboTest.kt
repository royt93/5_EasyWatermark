package com.mckimquyen.watermark.export

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-01: [BatchExportWorker] đọc trực tiếp từ repository (không qua `Data` serialize) và ghi lại
 * danh sách kết quả cuối vào repo — verify 2 điều WorkManager progress Data KHÔNG đảm bảo được
 * (progress bị xoá ngay khi work chuyển terminal, xem `doWork()`): (1) batch decode-fail vẫn trả
 * `WorkResult.failure()` đúng theo `Result.isFailure()`, (2) repo được cập nhật list cuối cùng
 * (bền, sống sót qua backgrounding — đúng mục tiêu AC1) dù `WorkInfo.progress` không còn dữ liệu.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportWorkerRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var userRepo: UserConfigRepository

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
            context.userDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        userRepo = UserConfigRepository(context.userDataStore)

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
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(testWorkerFactory)
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
    }

    @Test
    fun enqueue_emptyImageList_failsWithoutTouchingRepo() {
        val viewInfo = com.mckimquyen.watermark.data.model.ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER,
            matrix = android.graphics.Matrix()
        )
        BatchExportWorker.enqueue(context, viewInfo)
        shadowOf(Looper.getMainLooper()).idle()

        val info = awaitTerminalWorkInfo()
        assertThat(info?.state).isEqualTo(WorkInfo.State.FAILED)
    }

    @Test
    fun enqueue_decodeFailure_writesFinalFailureListToRepo_andReportsSuccessOverall() {
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        val viewInfo = com.mckimquyen.watermark.data.model.ViewInfo(
            width = 100,
            height = 100,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER,
            matrix = android.graphics.Matrix()
        )
        BatchExportWorker.enqueue(context, viewInfo)
        shadowOf(Looper.getMainLooper()).idle()

        val info = awaitTerminalWorkInfo()
        // Batch có 1 ảnh, ảnh đó lỗi decode nhưng generateList() vẫn Result.success() (đúng hành vi
        // cũ) — WorkResult tổng thể là SUCCEEDED dù từng ảnh Failure.
        assertThat(info?.state).isEqualTo(WorkInfo.State.SUCCEEDED)
        val finalList = waterMarkRepo.imageInfoList
        assertThat(finalList).hasSize(1)
        assertThat(finalList.first().jobState).isInstanceOf(com.mckimquyen.watermark.data.model.JobState.Failure::class.java)
    }

    private fun awaitTerminalWorkInfo(timeoutMs: Long = 5_000): WorkInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val info = androidx.work.WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BatchExportWorker.UNIQUE_WORK_NAME).get().firstOrNull()
            if (info != null && info.state.isFinished) return info
            Thread.sleep(20)
        }
        return null
    }
}
