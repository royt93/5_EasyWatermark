package com.mckimquyen.watermark.ui.adapter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatchHistoryAdapterCountUrisTest {

    @Test
    fun countUris_blank_isZero() {
        assertThat(BatchHistoryAdapter.countUris("")).isEqualTo(0)
        assertThat(BatchHistoryAdapter.countUris("   ")).isEqualTo(0)
    }

    @Test
    fun countUris_countsNonBlankLines() {
        assertThat(BatchHistoryAdapter.countUris("a\nb\nc")).isEqualTo(3)
    }

    @Test
    fun countUris_skipsBlankLines() {
        assertThat(BatchHistoryAdapter.countUris("a\n\nb")).isEqualTo(2)
    }
}
