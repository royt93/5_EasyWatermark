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
}
