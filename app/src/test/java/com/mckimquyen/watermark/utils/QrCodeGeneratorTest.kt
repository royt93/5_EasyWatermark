package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test (Robolectric — cần Bitmap) cho [QrCodeGenerator].
 */
@RunWith(RobolectricTestRunner::class)
class QrCodeGeneratorTest {

    @Test
    fun generate_validContent_returnsSquareBitmap() {
        val bitmap = QrCodeGenerator.generate("https://example.com", size = 256)
        assertThat(bitmap).isNotNull()
        assertThat(bitmap!!.width).isEqualTo(bitmap.height)
        assertThat(bitmap.width).isGreaterThan(0)
    }

    @Test
    fun generate_blankContent_returnsNull() {
        assertThat(QrCodeGenerator.generate("")).isNull()
        assertThat(QrCodeGenerator.generate("   ")).isNull()
    }

    @Test
    fun generate_clampsNonPositiveSizeButStillEncodes() {
        // size <= 0 bị ép tối thiểu 1; QR thực tế lớn hơn do số module tối thiểu.
        val bitmap = QrCodeGenerator.generate("hi", size = 0)
        assertThat(bitmap).isNotNull()
        assertThat(bitmap!!.width).isGreaterThan(0)
    }

    // --- BUG-66: saveToCache không được cấp URI cho file ghi thất bại/rỗng ---

    @Test
    fun keepFileIfWritten_compressFailed_deletesFileAndReturnsFalse() {
        val file = java.io.File.createTempFile("qr_bug66_", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val ok = QrCodeGenerator.keepFileIfWritten(file, compressSucceeded = false)

        assertThat(ok).isFalse()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun keepFileIfWritten_compressSucceededButEmptyFile_deletesAndReturnsFalse() {
        val file = java.io.File.createTempFile("qr_bug66_empty_", ".png")

        val ok = QrCodeGenerator.keepFileIfWritten(file, compressSucceeded = true)

        assertThat(ok).isFalse()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun keepFileIfWritten_validFile_keepsFileAndReturnsTrue() {
        val file = java.io.File.createTempFile("qr_bug66_ok_", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val ok = QrCodeGenerator.keepFileIfWritten(file, compressSucceeded = true)

        assertThat(ok).isTrue()
        assertThat(file.exists()).isTrue()
        file.delete()
    }

    @Test
    fun saveToCache_realBitmap_returnsUriAndWritesNonEmptyFile() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = QrCodeGenerator.generate("bug66", size = 128)!!

        val uri = QrCodeGenerator.saveToCache(context, bitmap, prefix = "qr_bug66_")

        assertThat(uri).isNotNull()
        val dir = java.io.File(context.cacheDir, "qrcodes")
        val written = dir.listFiles { f -> f.name.startsWith("qr_bug66_") }.orEmpty()
        assertThat(written).isNotEmpty()
        assertThat(written.all { it.length() > 0 }).isTrue()
        written.forEach { it.delete() }
    }
}
