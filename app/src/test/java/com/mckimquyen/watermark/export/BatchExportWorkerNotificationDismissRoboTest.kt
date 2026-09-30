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
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkStyleHistoryRepository
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
 * P1 review pass 8: `notifyProgress()` dùng `setOngoing(true)` nhưng KHÔNG nơi nào gọi
 * `NotificationManager.cancel()` khi `doWork()` kết thúc (xong/lỗi/huỷ) — notification "n/total"
 * treo vĩnh viễn, user không vuốt bỏ được (ongoing). `finally` phải luôn dọn, giống pattern
 * wake lock đã có (`doWork_afterCompletion_releasesWakeLock_noLeak`).
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportWorkerNotificationDismissRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var userRepo: UserConfigRepository

    private class FakeBatchHistoryDao : BatchHistoryDao {
        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: BatchHistoryEntity): Long = 1L
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun trimOldest(keepCount: Int) = Unit
    }

    private class FakeWatermarkStyleHistoryDao : WatermarkStyleHistoryDao {
        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long = 1L
        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> = emptyList()
        override suspend fun pruneKeepLatest(keep: Int) = Unit
    }

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        userRepo = UserConfigRepository(userDataStore)
        val batchHistoryRepo = BatchHistoryRepository(FakeBatchHistoryDao())
        val styleHistoryRepo = WatermarkStyleHistoryRepository(FakeWatermarkStyleHistoryDao())
        val engine = BatchExportEngine(context, ExportNaming())
        val testWorkerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return if (workerClassName == BatchExportWorker::class.java.name) {
                    BatchExportWorker(appContext, workerParameters, waterMarkRepo, userRepo, engine, batchHistoryRepo, styleHistoryRepo)
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
    fun doWork_afterCompletion_dismissesProgressNotification() {
        org.robolectric.shadows.ShadowPowerManager.clearWakeLocks()
        val original = ImageInfo(Uri.parse("content://does.not.exist/fake.jpg"))
        runBlocking { waterMarkRepo.updateImageList(listOf(original)) }
        shadowOf(Looper.getMainLooper()).idle()

        val viewInfo = ViewInfo(
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

        // 4201 mirror NOTIFICATION_ID private trong BatchExportWorker (giống test notification khác).
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        assertThat(shadowOf(manager).getNotification(4201)).isNull()
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
