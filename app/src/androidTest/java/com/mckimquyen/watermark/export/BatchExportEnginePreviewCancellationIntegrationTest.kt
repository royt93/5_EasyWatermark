package com.mckimquyen.watermark.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.utils.bitmap.BitmapCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * ENH-50: preview grid phải huỷ được thật trên engine thật (decode Skia + Palette thật). Cuộn nhanh
 * → job cũ bị huỷ (ném CancellationException), job cuối vẫn ra bitmap hợp lệ, không ô nào chạy trọn
 * rồi bị bỏ.
 */
@RunWith(AndroidJUnit4::class)
class BatchExportEnginePreviewCancellationIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        BitmapCache.clearCache()
    }

    @After
    fun tearDown() {
        createdFiles.forEach { it.delete() }
        BitmapCache.clearCache()
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    private fun syntheticJpeg(index: Int): Uri {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(index * COLOR_STEP % MAX_CHANNEL, COLOR_STEP, MAX_CHANNEL / 2))
        val file = File.createTempFile("enh50_$index", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        createdFiles.add(file)
        return Uri.fromFile(file)
    }

    @Test
    fun fling_cancelledPreviewsThrowAndLastOneStillSucceeds() {
        runBlocking { flingScenario() }
    }

    private suspend fun flingScenario() = kotlinx.coroutines.coroutineScope {
        val engine = BatchExportEngine(context, ExportNaming())
        val config = WaterMarkRepository(context, context.waterMarkDataStore).waterMark.first()
        val uris = List(FLING_COUNT) { syntheticJpeg(it) }

        val jobs = uris.mapIndexed { index, uri ->
            async(start = CoroutineStart.LAZY) {
                engine.generatePreviewBitmap(context.contentResolver, ImageInfo(uri), config, index)
            }
        }
        jobs.forEach { it.start() }
        // Giả lập fling: huỷ mọi ô trừ ô cuối ngay khi vừa khởi chạy.
        jobs.dropLast(1).forEach { it.cancel() }

        val cancelledCount = jobs.dropLast(1).count { job ->
            runCatching { job.await() }.exceptionOrNull() is CancellationException
        }
        val last = jobs.last().await()

        assertThat(cancelledCount).isEqualTo(FLING_COUNT - 1)
        assertThat(last).isInstanceOf(BatchExportEngine.PreviewResult.Success::class.java)
        assertThat(last.bitmap?.isRecycled).isFalse()
        last.bitmap?.recycle()
    }

    private companion object {
        const val FLING_COUNT = 12
        const val IMAGE_WIDTH = 1600
        const val IMAGE_HEIGHT = 1200
        const val JPEG_QUALITY = 80
        const val COLOR_STEP = 20
        const val MAX_CHANNEL = 255
    }
}
