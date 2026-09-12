package com.mckimquyen.watermark.export

import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-01: [BatchExportEngine.generateList] PHẢI rethrow `CancellationException` (không bị
 * `catch (e: Exception)` nuốt) — bắt buộc để [BatchExportWorker] huỷ giữa batch (AC2) có tác dụng
 * thật: `CoroutineWorker.onStopped`/WorkManager cancel Job của `doWork()`, nếu bị nuốt thì batch
 * cứ chạy tiếp hết danh sách bất chấp lệnh huỷ.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineCancellationRoboTest {

    @Test
    fun generateList_jobCancelledMidBatch_rethrowsCancellationException_stopsRemainingImages() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        val waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        val engine = BatchExportEngine(context, ExportNaming())
        val infoList = listOf(
            ImageInfo(Uri.parse("content://does.not.exist/1.jpg")),
            ImageInfo(Uri.parse("content://does.not.exist/2.jpg")),
            ImageInfo(Uri.parse("content://does.not.exist/3.jpg"))
        )
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
        var caught: Throwable? = null
        var progressCount = 0

        runBlocking {
            val settings = BatchExportEngine.ExportSettings(
                config = waterMarkRepo.waterMark.first(),
                outputFormat = android.graphics.Bitmap.CompressFormat.JPEG,
                compressLevel = 90,
                maxOutputLongEdge = 0,
                copyright = "",
                outputNamePattern = "{filename}"
            )
            lateinit var job: Job
            job = launch {
                try {
                    engine.generateList(context.contentResolver, viewInfo, infoList, settings) { info ->
                        if (info != null) {
                            progressCount++
                            // Huỷ ngay sau progress đầu tiên (Ing của ảnh 1) — mô phỏng user bấm Cancel.
                            if (progressCount == 1) job.cancel()
                        }
                    }
                } catch (t: Throwable) {
                    caught = t
                }
            }
            job.join()
        }

        assertThat(caught).isInstanceOf(CancellationException::class.java)
        // Cả batch có 3 ảnh x tối đa 2 progress mỗi ảnh (Ing + Success/Failure) + 1 onProgress(null)
        // cuối = tối đa 7 lần gọi nếu chạy hết KHÔNG bị huỷ. Huỷ ngay sau progress đầu tiên phải
        // chặn đứng các ảnh còn lại — không thấy progress của ảnh 2/3.
        assertThat(progressCount).isEqualTo(1)
    }
}
