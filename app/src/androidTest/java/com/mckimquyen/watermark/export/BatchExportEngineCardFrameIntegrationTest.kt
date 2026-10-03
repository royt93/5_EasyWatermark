package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
import com.mckimquyen.watermark.utils.bitmap.CardFrameRenderer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * FEAT-28: khung thẻ (bo góc + đổ bóng + nền) phải có trong FILE EXPORT THẬT — export qua
 * [BatchExportEngine.generateList], decode lại file đã ghi bằng [BitmapFactory] thật.
 */
@RunWith(AndroidJUnit4::class)
class BatchExportEngineCardFrameIntegrationTest {

    private companion object {
        const val SRC_W = 600
        const val SRC_H = 400
        val BG = Color.rgb(240, 240, 250)
        const val CORNER = 0.2f
        const val SHADOW = 0.04f
        const val MAX_CHANNEL_DELTA = 12 // JPEG nén có mất mát nhẹ
    }

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

    private fun createTestJpeg(): Uri {
        val bitmap = Bitmap.createBitmap(SRC_W, SRC_H, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(200, 30, 30))
        val file = File.createTempFile("feat28_card", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        createdTestFiles.add(file)
        return Uri.fromFile(file)
    }

    private fun settings(cardFrame: Boolean, maxLongEdge: Int = 0) = BatchExportEngine.ExportSettings(
        config = WaterMark(
            // text rỗng: layer chính bị skip → vùng giữa ảnh giữ nguyên màu gốc, dễ kiểm pixel.
            text = "",
            textSize = 20f,
            textColor = Color.WHITE,
            textStyle = TextPaintStyle.Fill,
            textTypeface = TextTypeface.Normal,
            alpha = 255,
            degree = 0f,
            hGap = 0,
            vGap = 0,
            iconUri = Uri.EMPTY,
            markMode = WaterMarkRepository.MarkMode.Text,
            enableBounds = false,
            cardFrameEnabled = cardFrame,
            cardCornerRadiusPercent = CORNER,
            cardShadowPercent = SHADOW,
            cardBackgroundColor = BG
        ),
        outputFormat = Bitmap.CompressFormat.JPEG,
        compressLevel = 95,
        maxOutputLongEdge = maxLongEdge,
        copyright = "",
        outputNamePattern = "feat28_{index}"
    )

    private fun viewInfo() = ViewInfo(
        width = 1080,
        height = 1920,
        paddingLeft = 0,
        paddingTop = 0,
        paddingRight = 0,
        paddingBottom = 0,
        scaleType = ImageView.ScaleType.FIT_CENTER,
        matrix = Matrix()
    )

    private fun export(settings: BatchExportEngine.ExportSettings): Bitmap {
        val result = runBlocking {
            BatchExportEngine(context, ExportNaming()).generateList(
                contentResolver = context.contentResolver,
                viewInfo = viewInfo(),
                infoList = listOf(ImageInfo(createTestJpeg())),
                settings = settings,
                onProgress = {}
            )
        }
        assertThat(result.type).isEqualTo(Result.Type.Success)
        val state = result.data.orEmpty().single().jobState
        assertThat(state).isInstanceOf(JobState.Success::class.java)
        val uri = (state as JobState.Success).result.data as Uri
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertThat(bitmap).isNotNull()
        return bitmap
    }

    private fun assertClose(actual: Int, expected: Int) {
        assertThat(Math.abs(Color.red(actual) - Color.red(expected))).isAtMost(MAX_CHANNEL_DELTA)
        assertThat(Math.abs(Color.green(actual) - Color.green(expected))).isAtMost(MAX_CHANNEL_DELTA)
        assertThat(Math.abs(Color.blue(actual) - Color.blue(expected))).isAtMost(MAX_CHANNEL_DELTA)
    }

    @Test
    fun cardFrameOn_exportedFileIsLargerByPadding_andCornerIsBackground() {
        val layout = CardFrameRenderer.computeLayout(SRC_W, SRC_H, CORNER, SHADOW)

        val out = export(settings(cardFrame = true))

        assertThat(out.width).isEqualTo(layout.canvasWidth)
        assertThat(out.height).isEqualTo(layout.canvasHeight)
        assertClose(out.getPixel(0, 0), BG) // góc xa = màu nền thẻ
        assertClose(out.getPixel(layout.padding, layout.padding), BG) // góc ảnh bị bo mất
        assertClose(out.getPixel(out.width / 2, out.height / 2), Color.rgb(200, 30, 30)) // tâm giữ ảnh
        out.recycle()
    }

    @Test
    fun cardFrameOff_exportedFileKeepsOriginalSize_noPadding() {
        val out = export(settings(cardFrame = false))

        assertThat(out.width).isEqualTo(SRC_W)
        assertThat(out.height).isEqualTo(SRC_H)
        out.recycle()
    }

    @Test
    fun cardFrameOn_withMaxLongEdge_resizesWholeCardAfterFraming() {
        val maxLongEdge = 300

        val out = export(settings(cardFrame = true, maxLongEdge = maxLongEdge))

        // resize chạy SAU khung thẻ → cạnh dài của file cuối = đúng giới hạn (cả thẻ, gồm nền).
        assertThat(maxOf(out.width, out.height)).isEqualTo(maxLongEdge)
        out.recycle()
    }
}
