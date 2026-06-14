package com.mckimquyen.watermark.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

/**
 * Unit test thuần (JVM) cho Room [DateConverter].
 */
class DateConverterTest {

    private val converter = DateConverter()

    @Test
    fun fromTimestamp_null_returnsNull() {
        assertThat(converter.fromTimestamp(null)).isNull()
    }

    @Test
    fun dateToTimestamp_null_returnsNull() {
        assertThat(converter.dateToTimestamp(null)).isNull()
    }

    @Test
    fun roundTrip_preservesValue() {
        val millis = 1_700_000_000_000L
        val date = converter.fromTimestamp(millis)
        assertThat(date).isEqualTo(Date(millis))
        assertThat(converter.dateToTimestamp(date)).isEqualTo(millis)
    }
}
