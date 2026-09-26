package com.mckimquyen.watermark.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Matrix
import android.os.Build
import android.os.PowerManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * ENH-01: chạy batch export qua WorkManager (sống sót khi app xuống nền, Doze/background-kill)
 * thay vì `viewModelScope` — logic export thật nằm ở [BatchExportEngine] (di chuyển nguyên vẹn từ
 * `MainViewModel`). Đọc danh sách ảnh/config TRỰC TIẾP từ repository (Hilt `@Singleton`, cùng
 * instance app đang dùng) thay vì serialize qua `Data` — chỉ [ViewInfo] (toàn số nguyên, không có
 * state phức tạp) cần truyền qua `inputData`.
 */
@HiltWorker
class BatchExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val waterMarkRepo: WaterMarkRepository,
    private val userRepo: UserConfigRepository,
    private val engine: BatchExportEngine,
    private val batchHistoryRepo: BatchHistoryRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): WorkResult {
        val infoList = waterMarkRepo.imageInfoList
        if (infoList.isEmpty()) {
            return WorkResult.failure()
        }
        val viewInfo = readViewInfo()
        val prefs = userRepo.userPreferences.first()
        val baseConfig = waterMarkRepo.waterMark.first()
        val settings = BatchExportEngine.ExportSettings(
            config = if (prefs.proofingMode) ProofingMode.overrideConfig(baseConfig) else baseConfig,
            outputFormat = prefs.outputFormat,
            compressLevel = prefs.compressLevel,
            maxOutputLongEdge = prefs.maxOutputLongEdge,
            copyright = prefs.copyright,
            outputNamePattern = prefs.outputNamePattern,
            conflictPolicy = prefs.conflictPolicy,
            outputDirectoryUri = prefs.outputDirectoryUri,
            proofingMode = prefs.proofingMode
        )
        val total = infoList.size
        var doneCount = 0
        setForeground(createForegroundInfo(doneCount, total))

        // Foreground service KHÔNG tự giữ CPU thức — nếu màn hình tắt giữa batch (nhiều ảnh, tốn
        // thời gian), thiết bị có thể đi ngủ sâu làm export chậm/dừng giữa chừng. Giữ PARTIAL wake
        // lock (chỉ CPU, không giữ sáng màn hình — màn hình đã có FLAG_KEEP_SCREEN_ON riêng ở
        // BaseActivity khi app đang mở) trong suốt doWork(), luôn release ở finally kể cả lỗi/huỷ.
        val wakeLock = acquireWakeLock()
        try {
            val result = engine.generateList(applicationContext.contentResolver, viewInfo, infoList, settings) { info ->
                if (info == null) return@generateList
                val stateOrdinal = when (info.jobState) {
                    is JobState.Ing -> STATE_ING
                    is JobState.Success -> STATE_SUCCESS
                    is JobState.Failure -> STATE_FAILURE
                    else -> return@generateList
                }
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS_URI to info.uri.toString(),
                        KEY_PROGRESS_STATE to stateOrdinal
                    )
                )
                if (stateOrdinal != STATE_ING) {
                    doneCount++
                    notifyProgress(doneCount, total)
                }
            }
            // WorkManager xoá progress Data ngay khi work chuyển sang trạng thái terminal — item cuối
            // cùng vừa cập nhật progress (vd Failure) có thể KHÔNG BAO GIỜ tới được observer nếu
            // race với lúc Worker return (đã thấy trong test: seenJobStates dừng ở Ing thay vì
            // Failure). Ghi thẳng danh sách cuối vào repo (nguồn sự thật bền, sống sót qua
            // backgrounding/process death — đúng mục tiêu AC1) để ViewModel đọc lại khi work xong,
            // thay vì chỉ dựa vào progress Data tạm thời.
            val finalList = result.data ?: infoList
            waterMarkRepo.updateImageList(finalList)
            if (settings.proofingMode) {
                val entries = finalList.mapIndexedNotNull { index, info ->
                    val outputUri = info.shareUri ?: return@mapIndexedNotNull null
                    if (info.isSkippedInExport) return@mapIndexedNotNull null
                    val fileName = com.mckimquyen.watermark.utils.ExportZipHelper.queryDisplayName(
                        applicationContext.contentResolver,
                        outputUri
                    ) ?: return@mapIndexedNotNull null
                    ProofingMode.Entry(sequence = index + 1, fileName = fileName)
                }
                ProofingMode.writeIndex(applicationContext, entries, settings.outputDirectoryUri)
            }
            recordHistory(infoList, finalList, settings)
            return if (result.isFailure()) WorkResult.failure() else WorkResult.success()
        } finally {
            if (wakeLock?.isHeld == true) wakeLock.release()
        }
    }

    /**
     * Trả `null` nếu không lấy được [PowerManager] hoặc `newWakeLock` ném lỗi — export vẫn chạy
     * tiếp, chỉ mất bảo vệ CPU-sleep. Tag chứa `packageName` thật (không hardcode) — đúng khuyến
     * nghị Android `<package>:<tag>` (thiếu tiền tố package sẽ bị `PowerManager` log cảnh báo).
     */
    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return try {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${applicationContext.packageName}:$WAKE_LOCK_TAG").apply {
                acquire(MAX_WAKE_LOCK_DURATION_MS)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * FEAT-04: ghi 1 entry lịch sử sau mỗi batch export chạy xong (kể cả lỗi 1 phần) — dùng
     * [infoList] gốc (trước export) làm `inputUris` để khôi phục đúng nguyên batch khi "chạy lại",
     * KHÔNG dùng [finalList] vì ảnh bị skip (FEAT-17) giữ nguyên `jobState = Ready` trong cả 2 list.
     */
    private suspend fun recordHistory(
        infoList: List<ImageInfo>,
        finalList: List<ImageInfo>,
        settings: BatchExportEngine.ExportSettings
    ) {
        val outputUris = finalList.mapNotNull { it.shareUri }
        val failedInputUris = finalList.filter { it.jobState is JobState.Failure }.map { it.uri }
        batchHistoryRepo.record(
            inputUris = infoList.map { it.uri },
            outputUris = outputUris,
            failedInputUris = failedInputUris,
            settings = settings
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, waterMarkRepo.imageInfoList.size)

    private fun readViewInfo(): ViewInfo = ViewInfo(
        width = inputData.getInt(KEY_VIEW_WIDTH, 0),
        height = inputData.getInt(KEY_VIEW_HEIGHT, 0),
        paddingLeft = inputData.getInt(KEY_PADDING_LEFT, 0),
        paddingTop = inputData.getInt(KEY_PADDING_TOP, 0),
        paddingRight = inputData.getInt(KEY_PADDING_RIGHT, 0),
        paddingBottom = inputData.getInt(KEY_PADDING_BOTTOM, 0),
        // adjustMatrix() trong BatchExportEngine luôn dùng Matrix() mới (identity), không đọc
        // field này — xem MainViewModel.generateImage() gốc, giữ nguyên hành vi, không cần truyền
        // qua Data (Matrix không phải kiểu nguyên thuỷ WorkManager hỗ trợ).
        scaleType = ImageView.ScaleType.entries.getOrElse(inputData.getInt(KEY_SCALE_TYPE, 0)) { ImageView.ScaleType.FIT_CENTER },
        matrix = Matrix()
    )

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.export_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = buildNotification(done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(done: Int, total: Int) = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
        .setContentTitle(applicationContext.getString(R.string.export_notification_title))
        .setContentText(applicationContext.getString(R.string.export_notification_progress, done, total))
        .setSmallIcon(R.drawable.ic_watermark)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(total.coerceAtLeast(1), done, false)
        .addAction(
            0,
            applicationContext.getString(R.string.dialog_save_cancel),
            WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        )
        .build()

    private fun notifyProgress(done: Int, total: Int) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        // Không cần quyền POST_NOTIFICATIONS bổ sung: đã dùng cho notification foreground service
        // này ngay từ setForeground() ở doWork(), quyền đã được xin trước đó nếu Android 13+.
        manager.notify(NOTIFICATION_ID, buildNotification(done, total))
    }

    companion object {
        const val UNIQUE_WORK_NAME = "batch_export"

        private const val WAKE_LOCK_TAG = "BatchExportWorker:wake_lock"

        /** Trần an toàn cho wake lock — export dù rất nhiều ảnh cũng khó vượt 30 phút; tránh giữ CPU thức vô hạn nếu có bug treo. */
        private const val MAX_WAKE_LOCK_DURATION_MS = 30 * 60 * 1000L

        private const val KEY_VIEW_WIDTH = "view_width"
        private const val KEY_VIEW_HEIGHT = "view_height"
        private const val KEY_PADDING_LEFT = "padding_left"
        private const val KEY_PADDING_TOP = "padding_top"
        private const val KEY_PADDING_RIGHT = "padding_right"
        private const val KEY_PADDING_BOTTOM = "padding_bottom"
        private const val KEY_SCALE_TYPE = "scale_type"

        const val KEY_PROGRESS_URI = "progress_uri"
        const val KEY_PROGRESS_STATE = "progress_state"
        const val STATE_ING = 0
        const val STATE_SUCCESS = 1
        const val STATE_FAILURE = 2

        private const val NOTIFICATION_CHANNEL_ID = "batch_export"
        private const val NOTIFICATION_ID = 4201

        fun buildRequest(viewInfo: ViewInfo) = OneTimeWorkRequestBuilder<BatchExportWorker>()
            .setInputData(
                Data.Builder()
                    .putInt(KEY_VIEW_WIDTH, viewInfo.width)
                    .putInt(KEY_VIEW_HEIGHT, viewInfo.height)
                    .putInt(KEY_PADDING_LEFT, viewInfo.paddingLeft)
                    .putInt(KEY_PADDING_TOP, viewInfo.paddingTop)
                    .putInt(KEY_PADDING_RIGHT, viewInfo.paddingRight)
                    .putInt(KEY_PADDING_BOTTOM, viewInfo.paddingBottom)
                    .putInt(KEY_SCALE_TYPE, viewInfo.scaleType.ordinal)
                    .build()
            )
            .build()

        fun enqueue(context: Context, viewInfo: ViewInfo) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest(viewInfo)
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
