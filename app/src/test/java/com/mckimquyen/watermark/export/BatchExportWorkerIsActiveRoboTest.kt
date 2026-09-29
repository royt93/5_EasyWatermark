package com.mckimquyen.watermark.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * BUG-AUDIT-2026-09-29: [BatchExportWorker.enqueue] dùng `ExistingWorkPolicy.REPLACE` — nếu 1
 * batch đang chạy dở, enqueue lại sẽ âm thầm huỷ nó. [BatchExportWorker.isActive] cho UI hỏi xác
 * nhận trước khi làm vậy. Dùng 1 [Worker] CHẶN bằng [CountDownLatch] (không phải [BatchExportWorker]
 * thật) để có cửa sổ RUNNING xác định, tránh flaky do CoroutineWorker thật chạy quá nhanh.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportWorkerIsActiveRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val blockLatch = CountDownLatch(1)
    private val startedLatch = CountDownLatch(1)

    class BlockingWorker(
        appContext: Context,
        params: WorkerParameters,
        private val started: CountDownLatch,
        private val block: CountDownLatch
    ) : Worker(appContext, params) {
        override fun doWork(): Result {
            started.countDown()
            block.await(5, TimeUnit.SECONDS)
            return Result.success()
        }
    }

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? = if (workerClassName == BlockingWorker::class.java.name) {
                BlockingWorker(appContext, workerParameters, startedLatch, blockLatch)
            } else {
                null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setWorkerFactory(factory)
                .setExecutor(Executors.newSingleThreadExecutor())
                .build()
        )
    }

    @Test
    fun isActive_falseBeforeEnqueue_trueWhileRunning_falseAfterFinished() {
        assertThat(BatchExportWorker.isActive(context)).isFalse()

        WorkManager.getInstance(context).enqueueUniqueWork(
            BatchExportWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BlockingWorker>().build()
        )
        assertThat(startedLatch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(BatchExportWorker.isActive(context)).isTrue()

        blockLatch.countDown()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && BatchExportWorker.isActive(context)) {
            Thread.sleep(20)
        }
        assertThat(BatchExportWorker.isActive(context)).isFalse()
    }
}
