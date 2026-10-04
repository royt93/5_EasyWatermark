package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * BUG-61: `embedImages` trước đây `readBytes()` + base64 ẢNH FULL-RES cho từng ảnh vào RAM → batch
 * 50-100 ảnh ~1GB+ heap → OOM ở bước cuối, sau khi ảnh đã ghi xong. Giờ phải nhúng THUMBNAIL nhỏ
 * (cạnh dài <= [ProofingMode.PROOF_THUMBNAIL_LONG_EDGE]) — chống OOM mà index vẫn tự chứa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ProofingModeEmbedThumbnailRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    private fun decodeEmbedded(dataUri: String): Bitmap {
        val base64 = dataUri.substringAfter("base64,")
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Test
    fun embedImages_largeImage_embedsDownsampledThumbnail_notFullResBytes() {
        val uri = Uri.parse("content://media/external/images/media/1001")
        val fullRes = jpegBytes(3000, 2000)
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(fullRes))

        val result = ProofingMode.embedImages(context.contentResolver, listOf(ProofingMode.Entry(1, "big.jpg", uri = uri)))

        val src = result.single().imageSrc
        assertThat(src).startsWith("data:image/jpeg;base64,")
        val embedded = decodeEmbedded(src)
        assertThat(maxOf(embedded.width, embedded.height)).isAtMost(ProofingMode.PROOF_THUMBNAIL_LONG_EDGE)
        // Thumbnail nhúng phải nhỏ hơn hẳn bytes gốc (chống phình heap theo số ảnh).
        val embeddedBytes = Base64.decode(src.substringAfter("base64,"), Base64.NO_WRAP).size
        assertThat(embeddedBytes).isLessThan(fullRes.size)
    }

    @Test
    fun embedImages_smallImage_isNotUpscaled() {
        val uri = Uri.parse("content://media/external/images/media/1002")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(jpegBytes(200, 100)))

        val result = ProofingMode.embedImages(context.contentResolver, listOf(ProofingMode.Entry(1, "small.jpg", uri = uri)))

        val embedded = decodeEmbedded(result.single().imageSrc)
        assertThat(embedded.width).isAtMost(200)
        assertThat(embedded.height).isAtMost(100)
    }
}
