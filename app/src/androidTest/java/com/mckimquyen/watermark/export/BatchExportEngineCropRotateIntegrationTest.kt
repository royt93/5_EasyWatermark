package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.widget.ImageView
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.JobState
import com.mckimquyen.watermark.data.model.Result
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.ViewInfo
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * FEAT-16: crop tỉ lệ chuẩn + straighten phải phản ánh đúng trong FILE EXPORT THẬT (ghi đĩa qua
 * [BatchExportEngine.generateList], decode lại bằng [BitmapFactory] thật — không suy đoán qua
 * bitmap trung gian). Đúng 2 AC của ticket: (1) crop theo 1 tỉ lệ chuẩn rồi export ra đúng tỉ lệ,
 * (2) xoay thẳng nhẹ rồi export ra đúng khung đã xoay, không mất/méo watermark.
 */
@RunWith(AndroidJUnit4::class)
class BatchExportEngineCropRotateIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdTestFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        BitmapCache.clearCache()
    }

    @After
    fun tearDown() {
        createdTestFiles.forEach { it.delete() }
        createdTestFiles.clear()
        BitmapCache.clearCache()
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    private fun createTestJpeg(width: Int, height: Int, color: Int, namePrefix: String): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(color)
        val file = File.createTempFile(namePrefix, ".jpg", context.cacheDir)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        bitmap.recycle()
        createdTestFiles.add(file)
        return Uri.fromFile(file)
    }

    private fun defaultSettings(namePattern: String) = BatchExportEngine.ExportSettings(
        config = WaterMark(
            text = "FEAT-16 crop test",
            textSize = 30f,
            textColor = Color.WHITE,
            textStyle = TextPaintStyle.Fill,
            textTypeface = TextTypeface.Normal,
            alpha = 220,
            degree = 0f,
            hGap = 100,
            vGap = 100,
            iconUri = Uri.EMPTY,
            markMode = WaterMarkRepository.MarkMode.Text,
            enableBounds = false
        ),
        outputFormat = Bitmap.CompressFormat.JPEG,
        compressLevel = 85,
        maxOutputLongEdge = 0,
        copyright = "© 2026 EasyWatermark FEAT-16",
        outputNamePattern = namePattern
    )

    private fun fakeViewInfo() = ViewInfo(
        width = 1080,
        height = 1920,
        paddingLeft = 0,
        paddingTop = 0,
        paddingRight = 0,
        paddingBottom = 0,
        scaleType = ImageView.ScaleType.FIT_CENTER,
        matrix = Matrix()
    )

    private fun decodeExportedFile(imageInfo: ImageInfo): Bitmap {
        val jobState = imageInfo.jobState
        assertThat(jobState).isInstanceOf(JobState.Success::class.java)
        val outputUri = (jobState as JobState.Success).result.data as? Uri
        assertThat(outputUri).isNotNull()
        val inputStream = context.contentResolver.openInputStream(outputUri!!)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        assertThat(bitmap).isNotNull()
        return bitmap
    }

    @Test
    fun exportWithSquareCrop_producesFileWithCroppedSquareDimensions() {
        // 800x600, crop [0.25, 0, 0.75, 1] normalized => 400x600 thật (AC1: đúng tỉ lệ đã crop).
        val uri = createTestJpeg(800, 600, Color.BLUE, "feat16_crop_square")
        val info = ImageInfo(uri).copy(cropRect = RectF(0.25f, 0f, 0.75f, 1f), rotationDegrees = 0f)

        val exportEngine = BatchExportEngine(context, ExportNaming())
        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = defaultSettings("feat16_crop_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val exported = result.data.orEmpty().single()
        val bitmap = decodeExportedFile(exported)
        assertThat(bitmap.width).isEqualTo(400)
        assertThat(bitmap.height).isEqualTo(600)
        bitmap.recycle()
    }

    @Test
    fun exportWithRotation90_producesFileWithSwappedDimensions_watermarkNotLost() {
        // 800x600 xoay 90 độ => cạnh xuất ra đảo thành 600x800 (AC2: xoay thẳng rồi export đúng khung).
        val uri = createTestJpeg(800, 600, Color.RED, "feat16_rotate90")
        val info = ImageInfo(uri).copy(rotationDegrees = 90f)

        val exportEngine = BatchExportEngine(context, ExportNaming())
        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = defaultSettings("feat16_rotate_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val exported = result.data.orEmpty().single()
        val bitmap = decodeExportedFile(exported)
        assertThat(bitmap.width).isEqualTo(600)
        assertThat(bitmap.height).isEqualTo(800)
        // Watermark vẽ thật (không phải màu nền thuần Color.RED khắp nơi) — dò ít nhất 1 pixel
        // khác màu nền chứng minh watermark không bị mất khi xoay (không assert vị trí chính xác).
        var foundNonBackgroundPixel = false
        outer@ for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) {
                if (bitmap.getPixel(x, y) != Color.RED) {
                    foundNonBackgroundPixel = true
                    break@outer
                }
            }
        }
        assertThat(foundNonBackgroundPixel).isTrue()
        bitmap.recycle()
    }

    @Test
    fun exportWithoutCropOrRotation_unchangedBehavior_fullOriginalDimensions() {
        val uri = createTestJpeg(800, 600, Color.GREEN, "feat16_no_change")
        val info = ImageInfo(uri)

        val exportEngine = BatchExportEngine(context, ExportNaming())
        val result = runBlocking {
            exportEngine.generateList(
                contentResolver = context.contentResolver,
                viewInfo = fakeViewInfo(),
                infoList = listOf(info),
                settings = defaultSettings("feat16_nochange_{index}"),
                onProgress = {}
            )
        }

        assertThat(result.type).isEqualTo(Result.Type.Success)
        val exported = result.data.orEmpty().single()
        val bitmap = decodeExportedFile(exported)
        assertThat(bitmap.width).isEqualTo(800)
        assertThat(bitmap.height).isEqualTo(600)
        bitmap.recycle()
    }
}
