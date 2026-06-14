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
    fun mimeTypeFor_returnsImageMime() {
        assertThat(OutputImageUtils.mimeTypeFor(Bitmap.CompressFormat.JPEG)).isEqualTo("image/jpg")
        assertThat(OutputImageUtils.mimeTypeFor(Bitmap.CompressFormat.PNG)).isEqualTo("image/png")
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
