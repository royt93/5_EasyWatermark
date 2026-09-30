package com.mckimquyen.watermark.utils.bitmap

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

/**
 * FEAT-25 Unit Test: kiểm thử hàm thuần [parseExifDateTime] phân tích cú pháp ngày giờ từ EXIF.
 */
class BitmapUtilsExifDateTest {

    @Test
    fun parseExifDateTime_standardExifFormat_parsesCorrectly() {
        val raw = "2026:09:29 14:30:22"
        val timestamp = parseExifDateTime(raw)

        assertThat(timestamp).isNotNull()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp!! }
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.SEPTEMBER)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(29)
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(14)
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(30)
        assertThat(calendar.get(Calendar.SECOND)).isEqualTo(22)
    }

    @Test
    fun parseExifDateTime_dashSeparatedFormat_parsesCorrectly() {
        val raw = "2026-09-29 14:30:22"
        val timestamp = parseExifDateTime(raw)

        assertThat(timestamp).isNotNull()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp!! }
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.SEPTEMBER)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(29)
    }

    @Test
    fun parseExifDateTime_slashSeparatedFormat_parsesCorrectly() {
        val raw = "2026/09/29 14:30:22"
        val timestamp = parseExifDateTime(raw)

        assertThat(timestamp).isNotNull()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp!! }
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.SEPTEMBER)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(29)
    }

    @Test
    fun parseExifDateTime_withoutSeconds_parsesCorrectly() {
        val raw = "2026:09:29 14:30"
        val timestamp = parseExifDateTime(raw)

        assertThat(timestamp).isNotNull()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp!! }
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(14)
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(30)
    }

    @Test
    fun parseExifDateTime_dateOnly_parsesCorrectly() {
        val raw = "2026:09:29"
        val timestamp = parseExifDateTime(raw)

        assertThat(timestamp).isNotNull()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp!! }
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2026)
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(29)
    }

    @Test
    fun parseExifDateTime_emptyOrBlankString_returnsNull() {
        assertThat(parseExifDateTime("")).isNull()
        assertThat(parseExifDateTime("   ")).isNull()
    }

    @Test
    fun parseExifDateTime_malformedString_returnsNullSafely() {
        assertThat(parseExifDateTime("not a date")).isNull()
        assertThat(parseExifDateTime("2026:99:99 99:99:99")).isNull()
        assertThat(parseExifDateTime("abcd:ef:gh")).isNull()
    }
}
