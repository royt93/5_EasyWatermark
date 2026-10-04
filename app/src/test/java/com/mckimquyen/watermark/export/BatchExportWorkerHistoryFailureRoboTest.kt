package com.mckimquyen.watermark.export

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-59: `batchHistoryRepo.record()` (Room insert) không được làm cả batch báo FAILED — ảnh đã
 * export xong thật, ghi lịch sử chỉ là phụ (cùng lý do `styleHistoryRepo.record` đã bọc runCatching).
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportWorkerHistoryFailureRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class ThrowingBatchHistoryDao : BatchHistoryDao {
        var insertCalls = 0
        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: BatchHistoryEntity): Long {
            insertCalls++
            throw IllegalStateException("database is locked")
        }
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun trimOldest(keepCount: Int) = Unit
    }

    private class NoopStyleHistoryDao : WatermarkStyleHistoryDao {
        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long = 1L
        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> = emptyList()
        override suspend fun pruneKeepLatest(keep: Int) = Unit
    }

    @Test
    fun doWork_batchHistoryInsertThrows_workStillSucceeds_notFailed() {
        val waterMarkDataStore = newTestWaterMarkDataStore(context)
        val userDataStore = newTestUserDataStore(context)
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            userDataStore.edit { it.clear() }
        }
        val waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        val userRepo = UserConfigRepository(userDataStore)
        val throwingDao = ThrowingBatchHistoryDao()
        val batchHistoryRepo = BatchHistoryRepository(throwingDao)
        val styleHistoryRepo = WatermarkStyleHistoryRepository(NoopStyleHistoryDao())
        val engine = BatchExportEngine(context, ExportNaming())
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == BatchExportWorker::class.java.name) {
                BatchExportWorker(appContext, workerParameters, waterMarkRepo, userRepo, engine, batchHistoryRepo, styleHistoryRepo)
            } else {
                null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(factory)
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://does.not.exist/fake.jpg")))) }
        shadowOf(Looper.getMainLooper()).idle()

        BatchExportWorker.enqueue(
            context,
            ViewInfo(
                width = 100,
                height = 100,
                paddingLeft = 0,
                paddingTop = 0,
                paddingRight = 0,
                paddingBottom = 0,
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER,
                matrix = android.graphics.Matrix()
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        val deadline = System.currentTimeMillis() + 5_000
        var info: WorkInfo? = null
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            info = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BatchExportWorker.UNIQUE_WORK_NAME).get().firstOrNull()
            if (info != null && info.state.isFinished) break
            Thread.sleep(20)
        }

        assertThat(throwingDao.insertCalls).isEqualTo(1)
        assertThat(info?.state).isEqualTo(WorkInfo.State.SUCCEEDED)
    }
}
