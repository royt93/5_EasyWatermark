package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Batch không được sập vì 1 ảnh ném `Error` (không phải `Exception`) ngoài các nhánh bắt riêng
 * (vd `NoClassDefFoundError`, `StackOverflowError`): ảnh đó -> Failure(TYPE_ERROR_SAVE_UNKNOWN),
 * ảnh kế tiếp vẫn chạy. `OutOfMemoryError` có nhánh riêng -> TYPE_ERROR_SAVE_OOM.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineThrowableIsolationRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val viewInfo = ViewInfo(100, 100, 0, 0, 0, 0, ImageView.ScaleType.FIT_CENTER, Matrix())

    private fun images() = listOf(
        ImageInfo(Uri.parse("content://does.not.exist/1.jpg")),
        ImageInfo(Uri.parse("content://does.not.exist/2.jpg"))
    )

    private fun runBatch(firstImageError: Throwable): List<ImageInfo> {
        val repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val engine = spyk(BatchExportEngine(context, ExportNaming()))
        coEvery { engine.generateImage(any(), any(), any(), 0, any()) } throws firstImageError
        coEvery { engine.generateImage(any(), any(), any(), 1, any()) } returns Result.success(Uri.parse("content://out/2.jpg"))
        return runBlocking {
            val settings = BatchExportEngine.ExportSettings(
                config = repo.waterMark.first(),
                outputFormat = Bitmap.CompressFormat.JPEG,
                compressLevel = 90,
                maxOutputLongEdge = 0,
                copyright = "",
                outputNamePattern = "{filename}"
            )
            val result = engine.generateList(context.contentResolver, viewInfo, images(), settings) { }
            checkNotNull(result.data) { "generateList phải trả list, không ném" }
        }
    }

    @Test
    fun generateList_errorNotException_marksOnlyThatImageFailedAndContinues() {
        val out = runBatch(NoClassDefFoundError("forced"))

        val first = out[0].jobState as JobState.Failure
        assertThat(first.result.code).isEqualTo(MainViewModel.TYPE_ERROR_SAVE_UNKNOWN)
        assertThat(out[1].jobState).isInstanceOf(JobState.Success::class.java)
    }

    @Test
    fun generateList_outOfMemory_marksOomAndContinues() {
        val out = runBatch(OutOfMemoryError("forced"))

        val first = out[0].jobState as JobState.Failure
        assertThat(first.result.code).isEqualTo(MainViewModel.TYPE_ERROR_SAVE_OOM)
        assertThat(out[1].jobState).isInstanceOf(JobState.Success::class.java)
    }
}
