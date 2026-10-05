package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test (Robolectric) cho phần [OutputImageUtils] cần kiểu/đối tượng Android (CompressFormat, Bitmap).
 */
@RunWith(RobolectricTestRunner::class)
class ImageFormatRoboTest {

    @Test
    fun extensionFor_mapsEachFormat() {
        assertThat(OutputImageUtils.extensionFor(Bitmap.CompressFormat.JPEG)).isEqualTo("jpg")
        assertThat(OutputImageUtils.extensionFor(Bitmap.CompressFormat.PNG)).isEqualTo("png")
        @Suppress("DEPRECATION")
        assertThat(OutputImageUtils.extensionFor(Bitmap.CompressFormat.WEBP)).isEqualTo("webp")
    }

    @Test
    fun mimeTypeFor_returnsStandardImageMime() {
        // BUG-72: MIME chuẩn của JPEG là image/jpeg (KHÔNG phải image/jpg suy từ đuôi file ".jpg").
        assertThat(OutputImageUtils.mimeTypeFor(Bitmap.CompressFormat.JPEG)).isEqualTo("image/jpeg")
        assertThat(OutputImageUtils.mimeTypeFor(Bitmap.CompressFormat.PNG)).isEqualTo("image/png")
        @Suppress("DEPRECATION")
        assertThat(OutputImageUtils.mimeTypeFor(Bitmap.CompressFormat.WEBP)).isEqualTo("image/webp")
    }

    @Test
    fun exportEngine_neverBuildsMimeFromFileExtension() {
        // BUG-72: 2 đường tạo file (MediaStore + SAF) từng ghép "image/" + đuôi file → JPEG ra "image/jpg".
        val src = java.io.File("src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/mckimquyen/watermark/export/BatchExportEngine.kt")
        val text = src.readText()
        assertThat(text).doesNotContain("\"image/${'$'}{exportNaming.trapOutputExtension")
        assertThat(Regex("OutputImageUtils\\.mimeTypeFor\\(settings\\.outputFormat\\)").findAll(text).count())
            .isAtLeast(2)
    }

    @Test
    fun resizeIfNeeded_downscalesLongEdge() {
        val src = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        val resized = OutputImageUtils.resizeIfNeeded(src, 50)
        assertThat(resized.width).isEqualTo(50)
        assertThat(resized.height).isEqualTo(25)
    }

    @Test
    fun resizeIfNeeded_zeroLimit_returnsSameInstance() {
        val src = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        assertThat(OutputImageUtils.resizeIfNeeded(src, 0)).isSameInstanceAs(src)
    }
}
