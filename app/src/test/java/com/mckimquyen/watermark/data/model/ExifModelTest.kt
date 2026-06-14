package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho [ExifModel] — logic định dạng/validate chuỗi.
 */
class ExifModelTest {

    @Test
    fun isEmpty_returnsTrue_forDefaultModel() {
        assertThat(ExifModel().isEmpty()).isTrue()
    }

    @Test
    fun isEmpty_returnsFalse_whenAnyFieldSet() {
        assertThat(ExifModel(iso = "100").isEmpty()).isFalse()
        assertThat(ExifModel(make = "Canon").isEmpty()).isFalse()
    }

    @Test
    fun getCameraName_prefersModel_thenMake_thenFallback() {
        assertThat(ExifModel(make = "Canon", model = "EOS R5").getCameraName()).isEqualTo("EOS R5")
        assertThat(ExifModel(make = "Canon", model = "").getCameraName()).isEqualTo("Canon")
        assertThat(ExifModel().getCameraName()).isEqualTo("Unknown Device")
    }

    @Test
    fun getCameraName_trimsWhitespace() {
        assertThat(ExifModel(model = "  Pixel 8  ").getCameraName()).isEqualTo("Pixel 8")
    }

    @Test
    fun getFormattedExif_joinsAvailablePartsInOrder() {
        val model = ExifModel(
            fNumber = "f/2.8",
            exposureTime = "1/200s",
            iso = "400",
            focalLength = "50mm"
        )
        assertThat(model.getFormattedExif()).isEqualTo("50mm  f/2.8  1/200s  ISO400")
    }

    @Test
    fun getFormattedExif_skipsEmptyParts() {
        val model = ExifModel(fNumber = "f/4", iso = "100")
        assertThat(model.getFormattedExif()).isEqualTo("f/4  ISO100")
    }

    @Test
    fun getFormattedExif_returnsEmpty_whenNoData() {
        assertThat(ExifModel().getFormattedExif()).isEmpty()
    }
}
