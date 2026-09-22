package com.mckimquyen.watermark.export

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-18: [BatchExportEngine.generateCompareBitmaps] — cặp bitmap gốc/đã-watermark cho màn so
 * sánh trước/sau trong preview batch. Cùng giới hạn môi trường đã ghi nhận ở
 * `BatchExportEnginePreviewRoboTest`: Robolectric shadow `BitmapFactory` decode MỌI uri thành
 * bitmap giả 100x100, không mô phỏng được decode-thất-bại thật — verify hành vi hàm (2 bitmap độc
 * lập, không recycle, đúng kích thước) thay vì pixel thật.
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineCompareRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun newRepo(): WaterMarkRepository = WaterMarkRepository(context, newTestWaterMarkDataStore(context))

    @Test
    fun generateCompareBitmaps_defaultConfig_returnsTwoIndependentUnrecycledBitmaps() {
        val waterMarkRepo = newRepo()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generateCompareBitmaps(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
        assertThat(result!!.original).isNotSameInstanceAs(result.watermarked)
        assertThat(result.original.isRecycled).isFalse()
        assertThat(result.watermarked.isRecycled).isFalse()
        assertThat(result.original.width).isEqualTo(result.watermarked.width)
        assertThat(result.original.height).isEqualTo(result.watermarked.height)
    }

    @Test
    fun generateCompareBitmaps_textModeBlankText_stillReturnsBothBitmaps() {
        val waterMarkRepo = newRepo()
        runBlocking { waterMarkRepo.updateText("") }
        shadowOf(Looper.getMainLooper()).idle()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generateCompareBitmaps(context.contentResolver, imageInfo, config, index = 0)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
        assertThat(result!!.original.isRecycled).isFalse()
        assertThat(result.watermarked.isRecycled).isFalse()
    }

    @Test
    fun generateCompareBitmaps_differentIndex_stillReturnsValidResult() {
        val waterMarkRepo = newRepo()
        val engine = BatchExportEngine(context, ExportNaming())
        val imageInfo = ImageInfo(Uri.parse("content://media/2.jpg"))

        val result = runBlocking {
            val config = waterMarkRepo.waterMark.first()
            engine.generateCompareBitmaps(context.contentResolver, imageInfo, config, index = 5)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(result).isNotNull()
    }
}
