package com.mckimquyen.watermark.utils.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Integration test (instrumented, decode ảnh THẬT trên thiết bị): kiểm chứng refactor gỡ
 * `MyApplication.instance` (doc/todo.md) — `decodeBitmapFromUri`/`decodeSampledBitmapFromResource`
 * nay nhận `context` qua tham số vẫn đọc đúng EXIF orientation/interChangeSize từ `context`
 * được truyền vào (không còn phụ thuộc static field đã xóa).
 */
@RunWith(AndroidJUnit4::class)
class BitmapUtilsContextThreadingIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var tempFile: File? = null

    /** Tạo JPEG thật 60x30 kèm EXIF orientation=90 để buộc code đi qua nhánh xoay/đảo chiều. */
    private fun createRotatedJpeg(): Uri {
        val bitmap = Bitmap.createBitmap(60, 30, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("wm_context_test", ".jpg", context.cacheDir)
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        bitmap.recycle()
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        tempFile = file
        return Uri.fromFile(file)
    }

    @After
    fun tearDown() {
        tempFile?.delete()
    }

    @Test
    fun decodeBitmapFromUri_readsExifOrientation_fromProvidedContext() = runBlocking {
        val uri = createRotatedJpeg()

        val result = decodeBitmapFromUri(context, context.contentResolver, uri)

        assertThat(result.isFailure()).isFalse()
        val bitmap = result.data?.bitmap
        assertThat(bitmap).isNotNull()
        // Nguồn 60x30 + EXIF rotate 90 -> ma trận xoay đảo chiều thành 30x60.
        assertThat(bitmap!!.width).isEqualTo(30)
        assertThat(bitmap.height).isEqualTo(60)
        bitmap.recycle()
    }

    @Test
    fun decodeSampledBitmapFromResource_interChangesSize_usingProvidedContext() = runBlocking {
        val uri = createRotatedJpeg()

        val result = decodeSampledBitmapFromResource(
            context = context,
            resolver = context.contentResolver,
            uri = uri,
            reqWidth = 1000,
            reqHeight = 1000
        )

        assertThat(result.data?.bitmap).isNotNull()
        val bitmap = result.data!!.bitmap!!
        assertThat(bitmap.width).isEqualTo(30)
        assertThat(bitmap.height).isEqualTo(60)
        bitmap.recycle()
    }
}
