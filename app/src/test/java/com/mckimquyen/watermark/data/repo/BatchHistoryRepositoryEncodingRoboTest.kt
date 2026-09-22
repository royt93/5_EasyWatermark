package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-04: hàm thuần encode/decode danh sách Uri của [BatchHistoryRepository] — `Uri.parse()` cần
 * Android stub nên chạy Robolectric, không cần DAO/DB thật.
 */
@RunWith(RobolectricTestRunner::class)
class BatchHistoryRepositoryEncodingRoboTest {

    @Test
    fun decodeUriList_blank_returnsEmpty() {
        assertThat(BatchHistoryRepository.decodeUriList("")).isEmpty()
        assertThat(BatchHistoryRepository.decodeUriList("   ")).isEmpty()
    }

    @Test
    fun encodeUriList_thenDecode_roundTrips() {
        val uris = listOf(Uri.parse("content://media/a"), Uri.parse("content://media/b"))

        val encoded = BatchHistoryRepository.encodeUriList(uris)
        val decoded = BatchHistoryRepository.decodeUriList(encoded)

        assertThat(decoded).containsExactly(Uri.parse("content://media/a"), Uri.parse("content://media/b")).inOrder()
    }

    @Test
    fun encodeUriList_emptyList_returnsEmptyString() {
        assertThat(BatchHistoryRepository.encodeUriList(emptyList())).isEmpty()
    }

    @Test
    fun decodeUriList_skipsBlankLines() {
        val decoded = BatchHistoryRepository.decodeUriList("content://media/a\n\ncontent://media/b")
        assertThat(decoded).containsExactly(Uri.parse("content://media/a"), Uri.parse("content://media/b")).inOrder()
    }
}
