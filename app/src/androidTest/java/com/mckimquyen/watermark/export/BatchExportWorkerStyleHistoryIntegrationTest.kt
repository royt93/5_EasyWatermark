package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.BatchHistoryDatabase
import com.mckimquyen.watermark.data.db.WatermarkProfileDatabase
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkStyleHistoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * BUG-45 (code review 2026-09-28, phát hiện lúc audit lại IDEA-12): [BatchExportWorker.recordHistory]
 * KHÔNG được ghi style history khi Client Proofing Mode bật — `settings.config` lúc đó đã bị
 * `ProofingMode.overrideConfig()` ghi đè `alpha`/`markMode`, không phải "gu" thật user dùng.
 *
 * Instrumentation test (bitmap thật, decode thật qua `file://` — mirror `BatchWatermarkE2EAndroidTest`)
 * vì `BatchExportWorkerRoboTest` (Robolectric) chỉ có nhánh decode-fail (0 ảnh thành công), không đủ
 * để verify nhánh THÀNH CÔNG thật — đúng nhánh mà proofing-mode ghi đè config cần verify. Đi qua
 * đúng `BatchExportWorker.enqueue(context, viewInfo)` thật (không tự dựng `Data` tay — 2 key
 * `KEY_VIEW_WIDTH`/`KEY_VIEW_HEIGHT` là `private`) qua `WorkManagerTestInitHelper`, mirror
 * `BatchExportWorkerRoboTest`.
 */
@RunWith(AndroidJUnit4::class)
class BatchExportWorkerStyleHistoryIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdFiles = mutableListOf<File>()
    private var styleHistoryDb: WatermarkProfileDatabase? = null
    private var batchHistoryDb: BatchHistoryDatabase? = null

    @After
    fun tearDown() {
        createdFiles.forEach { it.delete() }
        styleHistoryDb?.close()
        batchHistoryDb?.close()
    }

    private fun isolatedDataStore(prefix: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                File.createTempFile("${prefix}_${UUID.randomUUID()}", ".preferences_pb", context.cacheDir).apply { deleteOnExit() }
            }
        )

    private fun createTestJpeg(): Uri {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)
        val file = File.createTempFile("style_history_worker_test", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        createdFiles.add(file)
        return Uri.fromFile(file)
    }

    private fun awaitTerminalWorkInfo(timeoutMs: Long = 15_000): WorkInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val info = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(BatchExportWorker.UNIQUE_WORK_NAME).get().firstOrNull()
            if (info != null && info.state.isFinished) return info
            Thread.sleep(50)
        }
        return null
    }

    /** Chạy batch export THẬT (1 ảnh, viewInfo thật khớp kích thước màn hình) qua đúng WorkManager thật. */
    private fun runRealExportOnce(proofingMode: Boolean): WatermarkStyleHistoryRepository = runBlocking {
        val waterMarkRepo = WaterMarkRepository(context, isolatedDataStore("wm"))
        val userRepo = UserConfigRepository(isolatedDataStore("user"))
        userRepo.updateProofingMode(proofingMode)
        waterMarkRepo.updateImageList(listOf(ImageInfo(createTestJpeg())))

        val batchDb = Room.inMemoryDatabaseBuilder(context, BatchHistoryDatabase::class.java).allowMainThreadQueries().build()
        batchHistoryDb = batchDb
        val styleDb = Room.inMemoryDatabaseBuilder(context, WatermarkProfileDatabase::class.java).allowMainThreadQueries().build()
        styleHistoryDb = styleDb
        val batchHistoryRepo = BatchHistoryRepository(batchDb.batchHistoryDao())
        val styleHistoryRepo = WatermarkStyleHistoryRepository(styleDb.watermarkStyleHistoryDao())
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

        val viewInfo = ViewInfo(
            width = 1080,
            height = 1920,
            paddingLeft = 12,
            paddingTop = 12,
            paddingRight = 12,
            paddingBottom = 12,
            scaleType = ImageView.ScaleType.MATRIX,
            matrix = Matrix()
        )
        BatchExportWorker.enqueue(context, viewInfo)

        val info = awaitTerminalWorkInfo()
        assertThat(info?.state).isEqualTo(WorkInfo.State.SUCCEEDED)
        styleHistoryRepo
    }

    @Test
    fun proofingModeOff_realSuccessfulExport_recordsOneStyleHistoryRow() {
        val repo = runRealExportOnce(proofingMode = false)

        val recent = runBlocking { repo.recent() }

        assertThat(recent).hasSize(1)
    }

    @Test
    fun proofingModeOn_realSuccessfulExport_doesNotRecordStyleHistory() {
        val repo = runRealExportOnce(proofingMode = true)

        val recent = runBlocking { repo.recent() }

        assertThat(recent).isEmpty()
    }
}
