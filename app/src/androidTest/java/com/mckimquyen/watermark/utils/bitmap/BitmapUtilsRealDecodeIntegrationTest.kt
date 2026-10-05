package com.mckimquyen.watermark.utils.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Decode ảnh THẬT bằng Skia trên thiết bị (file:// qua ContentResolver). Robolectric mô phỏng
 * `BitmapFactory` khác máy thật — BUG-61 là ví dụ: `decodeStream(..., inJustDecodeBounds=true)` luôn
 * trả null trên Android thật nhưng test Robolectric vẫn xanh. Phủ các nhánh decode còn lại của
 * [decodeBitmapFromUri] / [decodeSampledBitmapFromResourceSync] mà unit test không tin cậy được.
 */
@RunWith(AndroidJUnit4::class)
class BitmapUtilsRealDecodeIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "real_decode_${System.nanoTime()}").apply { mkdirs() }
        BitmapCache.clearCache()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        BitmapCache.clearCache()
    }

    private fun writeJpeg(name: String, width: Int, height: Int, exifOrientation: Int? = null): Uri {
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (exifOrientation != null) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    @Test
    fun decodeBitmapFromUri_largeImage_downsamplesToRequestedLongEdge() = runBlocking {
        val uri = writeJpeg("large.jpg", 2000, 1000)

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 500)

        assertThat(result.isFailure()).isFalse()
        val value = result.data!!
        assertThat(value.inSampleSize).isGreaterThan(1)
        val longEdge = maxOf(value.bitmap!!.width, value.bitmap!!.height)
        assertThat(longEdge).isLessThan(2000)
        assertThat(longEdge).isAtLeast(500)
    }

    @Test
    fun decodeBitmapFromUri_smallImage_originalSize_keepsFullResolution() = runBlocking {
        val uri = writeJpeg("small.jpg", 320, 240)

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 0)

        assertThat(result.isFailure()).isFalse()
        val bitmap = result.data!!.bitmap!!
        assertThat(result.data!!.inSampleSize).isEqualTo(1)
        assertThat(bitmap.width).isEqualTo(320)
        assertThat(bitmap.height).isEqualTo(240)
    }

    @Test
    fun decodeBitmapFromUri_garbageBytes_doesNotCrash_returnsNullBitmapOrFailure() = runBlocking {
        val file = File(dir, "garbage.jpg").apply { writeText("this is definitely not an image") }

        val result = decodeBitmapFromUri(context, context.contentResolver, Uri.fromFile(file), reqLongEdge = 500)

        // Skia thật: bounds = 0 → không có bitmap hợp lệ. Chấp nhận Failure HOẶC BitmapValue không bitmap,
        // nhưng KHÔNG được ném ngoại lệ ra caller (lifecycleScope sẽ crash app).
        assertThat(result.data?.bitmap).isNull()
    }

    @Test
    fun decodeBitmapFromUri_exifRotated90_swapsWidthAndHeight() = runBlocking {
        // Ảnh lưu 400x200 nhưng EXIF orientation=6 (xoay 90°) → hiển thị đúng phải là 200x400.
        val uri = writeJpeg("rotated.jpg", 400, 200, exifOrientation = ExifInterface.ORIENTATION_ROTATE_90)

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 0)

        assertThat(result.isFailure()).isFalse()
        val bitmap = result.data!!.bitmap!!
        assertThat(bitmap.width).isEqualTo(200)
        assertThat(bitmap.height).isEqualTo(400)
    }

    @Test
    fun decodeSampledBitmapFromResourceSync_realImage_decodesWithReasonableSampleSize() {
        val uri = writeJpeg("sampled.jpg", 1600, 1200)

        val result = decodeSampledBitmapFromResourceSync(context, context.contentResolver, uri, reqWidth = 400, reqHeight = 300)

        assertThat(result.isFailure()).isFalse()
        val value = result.data!!
        assertThat(value.inSampleSize).isGreaterThan(1)
        assertThat(value.bitmap!!.width).isAtMost(1600)
        assertThat(value.bitmap!!.width).isAtLeast(400)
    }

    @Test
    fun decodeSampledBitmapFromResourceSync_garbageBytes_doesNotCrash() {
        val file = File(dir, "garbage2.jpg").apply { writeText("not an image") }

        val result = decodeSampledBitmapFromResourceSync(
            context,
            context.contentResolver,
            Uri.fromFile(file),
            reqWidth = 100,
            reqHeight = 100
        )

        assertThat(result.data?.bitmap).isNull()
    }
}
