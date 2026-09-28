package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-40: token EXIF ({exposure}→"1/125s", {fnumber}→"f/2.8") sinh ra `/` — nối thẳng vào tên file
 * MediaStore/legacy path làm export thất bại. [ExportNaming.sanitizeFileName] là hàm thuần, JVM
 * test không cần Robolectric.
 */
class ExportNamingSanitizeTest {

    @Test
    fun sanitizeFileName_slashFromExposureToken_replacedWithUnderscore() {
        assertThat(ExportNaming.sanitizeFileName("photo_1/125s")).isEqualTo("photo_1_125s")
    }

    @Test
    fun sanitizeFileName_slashFromFNumberToken_replacedWithUnderscore() {
        assertThat(ExportNaming.sanitizeFileName("photo_f/2.8")).isEqualTo("photo_f_2.8")
    }

    @Test
    fun sanitizeFileName_allForbiddenChars_eachReplaced() {
        assertThat(ExportNaming.sanitizeFileName("a/b\\c?d%e*f:g|h\"i<j>k")).isEqualTo("a_b_c_d_e_f_g_h_i_j_k")
    }

    @Test
    fun sanitizeFileName_noForbiddenChars_unchanged() {
        assertThat(ExportNaming.sanitizeFileName("my_photo_2026")).isEqualTo("my_photo_2026")
    }

    @Test
    fun sanitizeFileName_emptyString_returnsEmpty() {
        assertThat(ExportNaming.sanitizeFileName("")).isEmpty()
    }

    @Test
    fun sanitizeFileName_onlyForbiddenChars_replacedNotBlank() {
        assertThat(ExportNaming.sanitizeFileName("///")).isEqualTo("___")
    }
}
