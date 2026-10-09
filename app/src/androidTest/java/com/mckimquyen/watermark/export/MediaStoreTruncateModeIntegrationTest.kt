package com.mckimquyen.watermark.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

/**
 * BUG-60: OVERWRITE ghi lên row MediaStore CÓ SẴN. Mode "w" không đảm bảo truncate từ Android 10 —
 * ghi ảnh nhỏ đè ảnh lớn sẽ để đuôi rác. Test này ghi ảnh lớn rồi ảnh nhỏ vào CÙNG URI bằng đúng
 * [BatchExportEngine.WRITE_TRUNCATE_MODE] trên MediaStore thật của máy, rồi đo file thật:
 * kích thước phải bằng đúng ảnh nhỏ và decode lại được.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreTruncateModeIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver
    private var rowUri: Uri? = null

    @After
    fun tearDown() {
        rowUri?.let { runCatching { resolver.delete(it, null, null) } }
    }

    private fun jpegBytes(size: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun write(uri: Uri, data: ByteArray) {
        val pfd = checkNotNull(resolver.openFileDescriptor(uri, BatchExportEngine.WRITE_TRUNCATE_MODE, null)) {
            "openFileDescriptor trả null"
        }
        pfd.use { FileOutputStream(it.fileDescriptor).write(data) }
    }

    private fun sizeOf(uri: Uri): Long = resolver.openFileDescriptor(uri, "r")!!.use { it.statSize }

    @Test
    fun overwriteSmallerImageOnSameUri_leavesNoTrailingBytes() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "bug60_truncate_${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WaterMarkCreatorTest")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "MediaStore insert thất bại"
        }
        rowUri = uri

        val big = jpegBytes(size = 1200, color = Color.RED)
        val small = jpegBytes(size = 64, color = Color.BLUE)
        assertThat(big.size).isGreaterThan(small.size)

        write(uri, big)
        assertThat(sizeOf(uri)).isEqualTo(big.size.toLong())

        write(uri, small)

        assertThat(sizeOf(uri)).isEqualTo(small.size.toLong())
        val decoded = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertThat(decoded).isNotNull()
        assertThat(decoded.width).isEqualTo(64)
        // JPEG nén có sai số nhẹ -> chỉ cần kênh xanh áp đảo (ảnh MỚI, không phải đỏ của ảnh cũ).
        val px = decoded.getPixel(32, 32)
        assertThat(Color.blue(px)).isGreaterThan(200)
        assertThat(Color.red(px)).isLessThan(60)
        decoded.recycle()
    }
}
