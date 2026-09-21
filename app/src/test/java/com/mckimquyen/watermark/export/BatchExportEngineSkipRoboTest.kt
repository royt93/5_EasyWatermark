package com.mckimquyen.watermark.export

import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-17: ảnh bị đánh dấu [ImageInfo.isSkippedInExport] phải hoàn toàn không đụng tới trong
 * [BatchExportEngine.generateList] — giữ nguyên `JobState.Ready`, không render/ghi file, không
 * tính vào tiến trình (AC "chỉ ảnh active được xuất, ảnh bị skip không có trong kết quả").
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineSkipRoboTest {

    @Test
    fun generateList_skipsMarkedImages_leavesThemUntouched_stillProcessesOthers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val engine = BatchExportEngine(context, ExportNaming())
        val infoList = listOf(
            ImageInfo(Uri.parse("content://does.not.exist/1.jpg")),
            ImageInfo(Uri.parse("content://does.not.exist/2.jpg"), isSkippedInExport = true),
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
        val settings = BatchExportEngine.ExportSettings(
            config = waterMarkRepo.waterMark.first(),
            outputFormat = android.graphics.Bitmap.CompressFormat.JPEG,
            compressLevel = 90,
            maxOutputLongEdge = 0,
            copyright = "",
            outputNamePattern = "{filename}"
        )
        val progressedUris = mutableListOf<Uri>()

        val result = engine.generateList(context.contentResolver, viewInfo, infoList, settings) { info ->
            if (info != null) progressedUris.add(info.uri)
        }

        val updated = result.data!!
        // Ảnh bị skip: hoàn toàn không đổi (cùng object reference, vẫn Ready).
        assertThat(updated[1]).isSameInstanceAs(infoList[1])
        assertThat(updated[1].jobState).isEqualTo(JobState.Ready)
        // 2 ảnh còn lại VẪN được xử lý (uri không tồn tại → Failure, nhưng chứng minh có chạy qua generateImage).
        assertThat(updated[0].jobState).isInstanceOf(JobState.Failure::class.java)
        assertThat(updated[2].jobState).isInstanceOf(JobState.Failure::class.java)
        // Không có progress nào (Ing/Success/Failure) phát ra cho uri của ảnh bị skip.
        assertThat(progressedUris).doesNotContain(Uri.parse("content://does.not.exist/2.jpg"))
        assertThat(progressedUris).contains(Uri.parse("content://does.not.exist/1.jpg"))
        assertThat(progressedUris).contains(Uri.parse("content://does.not.exist/3.jpg"))
    }

    @Test
    fun generateList_allImagesSkipped_returnsSuccessWithAllUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val engine = BatchExportEngine(context, ExportNaming())
        val infoList = listOf(
            ImageInfo(Uri.parse("content://does.not.exist/1.jpg"), isSkippedInExport = true),
            ImageInfo(Uri.parse("content://does.not.exist/2.jpg"), isSkippedInExport = true)
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
        val settings = BatchExportEngine.ExportSettings(
            config = waterMarkRepo.waterMark.first(),
            outputFormat = android.graphics.Bitmap.CompressFormat.JPEG,
            compressLevel = 90,
            maxOutputLongEdge = 0,
            copyright = "",
            outputNamePattern = "{filename}"
        )

        val result = engine.generateList(context.contentResolver, viewInfo, infoList, settings) {}

        assertThat(result.isFailure()).isFalse()
        assertThat(result.data).isEqualTo(infoList)
    }
}
