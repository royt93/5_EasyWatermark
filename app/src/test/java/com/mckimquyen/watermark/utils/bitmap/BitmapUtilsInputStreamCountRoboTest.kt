package com.mckimquyen.watermark.utils.bitmap

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
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
 * ENH-06: decode 1 ảnh (bounds + EXIF + pixel data) trước đây mở tới 5 `InputStream` riêng biệt
 * cho cùng 1 Uri (bounds, decode thật, orientation×2, exif data) — đo bằng `ContentProvider` giả
 * đếm số lần `openAssetFile()` được gọi (đây là nơi `ContentResolver.openInputStream()` thật sự
 * uỷ quyền tới cho content:// Uri).
 *
 * Mỗi test dùng 1 authority riêng (không tái dùng Uri giữa các test) để tránh `BitmapCache`
 * (singleton `object`, sống suốt JVM fork) trả cache hit làm sai lệch số đếm.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsInputStreamCountRoboTest {

    class CountingProvider : ContentProvider() {
        var openCount = 0
        lateinit var file: File

        override fun onCreate() = true

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            openCount++
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Ảnh JPEG thật kèm EXIF orientation=90 (buộc code đi qua nhánh xoay + đọc EXIF thật). */
    private fun createRotatedJpeg(): File {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("wm_stream_count", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        return file
    }

    private fun setupProvider(authority: String, file: File): CountingProvider {
        val provider = Robolectric.setupContentProvider(CountingProvider::class.java, authority)
        provider.file = file
        return provider
    }

    @Test
    fun decodeSampledBitmapFromResource_opensAtMostThreeStreams_perUri() = runBlocking {
        val file = createRotatedJpeg()
        val provider = setupProvider("wm.stream.count.sampled", file)
        val uri = Uri.parse("content://wm.stream.count.sampled/test.jpg")

        val result = decodeSampledBitmapFromResource(context, context.contentResolver, uri, 1000, 1000)

        assertThat(result.isFailure()).isFalse()
        // Trước fix: 5 (bounds + decode + orientation×2 + exif). Sau fix: bounds + exif(gộp
        // orientation+model) + decode = 3.
        assertThat(provider.openCount).isEqualTo(3)
        result.data?.bitmap?.recycle()
        Unit
    }

    @Test
    fun decodeBitmapFromUri_opensAtMostTwoStreams_perUri() = runBlocking {
        val file = createRotatedJpeg()
        val provider = setupProvider("wm.stream.count.fromuri", file)
        val uri = Uri.parse("content://wm.stream.count.fromuri/test.jpg")

        val result = decodeBitmapFromUri(context, context.contentResolver, uri)

        assertThat(result.isFailure()).isFalse()
        // Trước fix: 3 (decode + orientation + exif). Sau fix: decode + exif(gộp) = 2.
        assertThat(provider.openCount).isEqualTo(2)
        result.data?.bitmap?.recycle()
        Unit
    }
}
