package com.mckimquyen.watermark.utils.bitmap

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream

/**
 * ENH-14: `decodeBitmapFromUri(reqLongEdge = maxOutputLongEdge)` phải downsample NGAY lúc decode
 * khi user chọn resize output, thay vì decode full-res rồi resize sau khi vẽ watermark (peak
 * memory cao hơn cần thiết cho ảnh lớn). Không đo được bằng Android Studio Memory Profiler qua
 * CLI/ADB — thay bằng bằng chứng gián tiếp: kích thước bitmap trả về + `allocationByteCount`
 * (tỷ lệ thuận điểm ảnh × 4 byte/ARGB_8888) thực sự nhỏ hơn hẳn so với decode full-res cho CÙNG 1
 * ảnh, đo trực tiếp trên bitmap thật (không suy đoán).
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsDownsampleExportRoboTest {

    class SimpleProvider : ContentProvider() {
        lateinit var file: File
        override fun onCreate() = true
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Ảnh JPEG thật cạnh dài 3200 (không xoay — tránh nhiễu biến số, chỉ test riêng downsample). */
    private fun createLargeJpeg(): File {
        val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("wm_downsample_export", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    private fun setupProvider(authority: String, file: File): SimpleProvider {
        val provider = Robolectric.setupContentProvider(SimpleProvider::class.java, authority)
        provider.file = file
        return provider
    }

    @Test
    fun decodeBitmapFromUri_reqLongEdgeSet_returnsDownsampledBitmap_smallerAllocation() = runBlocking {
        val file = createLargeJpeg()
        val fullUri = Uri.parse("content://wm.downsample.full/test.jpg")
        setupProvider("wm.downsample.full", file)
        val downsampledUri = Uri.parse("content://wm.downsample.small/test.jpg")
        setupProvider("wm.downsample.small", file)

        val full = decodeBitmapFromUri(context, context.contentResolver, fullUri, reqLongEdge = 0)
        val downsampled = decodeBitmapFromUri(context, context.contentResolver, downsampledUri, reqLongEdge = 1080)

        assertThat(full.isFailure()).isFalse()
        assertThat(downsampled.isFailure()).isFalse()
        val fullBitmap = full.data!!.bitmap!!
        val downsampledBitmap = downsampled.data!!.bitmap!!

        // Full giữ nguyên kích thước gốc (Original — không đổi hành vi cũ, AC3).
        assertThat(fullBitmap.width).isEqualTo(3200)
        // Downsample theo cạnh dài, không vượt quá kích thước gốc và nhỏ hơn hẳn full.
        assertThat(downsampledBitmap.width).isLessThan(fullBitmap.width)
        assertThat(downsampledBitmap.width).isEqualTo(1600) // 3200 / inSampleSize=2

        // Bằng chứng gián tiếp giảm peak memory (thay Memory Profiler không đo được qua CLI/ADB):
        // allocationByteCount tỷ lệ thuận số điểm ảnh × 4 byte (ARGB_8888) — downsample cạnh dài
        // còn 1/2 → số điểm ảnh còn 1/4 → allocation giảm ~4 lần.
        val ratio = fullBitmap.allocationByteCount.toDouble() / downsampledBitmap.allocationByteCount
        assertThat(ratio).isAtLeast(3.5)

        fullBitmap.recycle()
        downsampledBitmap.recycle()
        Unit
    }

    @Test
    fun decodeBitmapFromUri_reqLongEdgeZero_behavesExactlyLikeBefore_fullResolution() = runBlocking {
        val file = createLargeJpeg()
        val uri = Uri.parse("content://wm.downsample.original/test.jpg")
        val provider = setupProvider("wm.downsample.original", file)

        val result = decodeBitmapFromUri(context, context.contentResolver, uri, reqLongEdge = 0)

        assertThat(result.isFailure()).isFalse()
        assertThat(result.data!!.bitmap!!.width).isEqualTo(3200)
        assertThat(result.data!!.bitmap!!.height).isEqualTo(1600)
        result.data?.bitmap?.recycle()
        Unit
    }
}
