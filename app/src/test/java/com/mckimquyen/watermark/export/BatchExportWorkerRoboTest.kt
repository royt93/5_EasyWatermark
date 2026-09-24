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
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
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
 * ENH-01: [BatchExportWorker] đọc trực tiếp từ repository (không qua `Data` serialize) và ghi lại
 * danh sách kết quả cuối vào repo — verify 2 điều WorkManager progress Data KHÔNG đảm bảo được
 * (progress bị xoá ngay khi work chuyển terminal, xem `doWork()`): (1) batch decode-fail vẫn trả
 * `WorkResult.failure()` đúng theo `Result.isFailure()`, (2) repo được cập nhật list cuối cùng
 * (bền, sống sót qua backgrounding — đúng mục tiêu AC1) dù `WorkInfo.progress` không còn dữ liệu.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportWorkerRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var userRepo: UserConfigRepository
    private lateinit var fakeHistoryDao: FakeBatchHistoryDao
    private lateinit var batchHistoryRepo: BatchHistoryRepository

    /** FEAT-04: fake nhẹ thay vì Room thật — Room DAO test dành cho androidTest theo quy ước repo này. */
    private class FakeBatchHistoryDao : BatchHistoryDao {
        val inserted = mutableListOf<BatchHistoryEntity>()
        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: BatchHistoryEntity): Long {
            inserted.add(entity)
            return inserted.size.toLong()
        }
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun trimOldest(keepCount: Int) = Unit
    }

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        userRepo = UserConfigRepository(userDataStore)
        fakeHistoryDao = FakeBatchHistoryDao()
        batchHistoryRepo = BatchHistoryRepository(fakeHistoryDao)

        val engine = BatchExportEngine(context, ExportNaming())
        val testWorkerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return if (workerClassName == BatchExportWorker::class.java.name) {
                    BatchExportWorker(appContext, workerParameters, waterMarkRepo, userRepo, engine, batchHistoryRepo)
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
        org.robolectric.shadows.ShadowPowerManager.clearWakeLocks()
        BatchExportWorker.enqueue(context, viewInfo)
        shadowOf(Looper.getMainLooper()).idle()

        val info = awaitTerminalWorkInfo()
        assertThat(info?.state).isEqualTo(WorkInfo.State.FAILED)
        // FEAT-04: infoList rỗng return sớm TRƯỚC recordHistory() — không có batch thật nào chạy,
        // không đáng ghi lịch sử.
        assertThat(fakeHistoryDao.inserted).isEmpty()
        // infoList rỗng return sớm TRƯỚC acquireWakeLock() — không tốn wake lock cho việc không làm gì.
        assertThat(org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()).isNull()
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

        // FEAT-04 AC1: 1 entry lịch sử mới xuất hiện sau batch, kể cả khi ảnh trong batch lỗi hết.
        assertThat(fakeHistoryDao.inserted).hasSize(1)
        val entry = fakeHistoryDao.inserted.single()
        assertThat(BatchHistoryRepository.decodeUriList(entry.inputUris)).containsExactly(original.uri)
        assertThat(BatchHistoryRepository.decodeUriList(entry.outputUris)).isEmpty()
        assertThat(BatchHistoryRepository.decodeUriList(entry.failedInputUris)).containsExactly(original.uri)
    }

    /**
     * Wake lock giữ CPU thức trong `doWork()` (xem comment ở `BatchExportWorker.doWork`) phải được
     * release ở `finally` — kể cả khi batch chạy xong (dù có ảnh lỗi decode bên trong, worker vẫn
     * trả SUCCEEDED). Không release = leak wake lock, giữ CPU thức vô thời hạn sau khi export xong.
     */
    @Test
    fun doWork_afterCompletion_releasesWakeLock_noLeak() {
        org.robolectric.shadows.ShadowPowerManager.clearWakeLocks()
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
        awaitTerminalWorkInfo()

        val wakeLock = org.robolectric.shadows.ShadowPowerManager.getLatestWakeLock()
        assertThat(wakeLock).isNotNull()
        assertThat(wakeLock!!.isHeld).isFalse()
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
