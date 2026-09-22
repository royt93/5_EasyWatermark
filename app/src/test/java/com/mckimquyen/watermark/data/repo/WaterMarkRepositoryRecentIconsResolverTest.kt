package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-24: hàm thuần bên dưới [WaterMarkRepository.updateIcon] — `Uri.parse()` cần Android stub
 * nên chạy Robolectric (không cần DataStore/Context thật, chỉ test logic list thuần).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryRecentIconsResolverTest {

    @Test
    fun pushToFrontOfRecentIcons_newUri_prepended() {
        val result = WaterMarkRepository.pushToFrontOfRecentIcons(
            current = listOf("b", "a"),
            newUri = "c",
            maxSize = 8
        )
        assertThat(result).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun pushToFrontOfRecentIcons_existingUri_movedToFront_noDuplicate() {
        val result = WaterMarkRepository.pushToFrontOfRecentIcons(
            current = listOf("c", "b", "a"),
            newUri = "b",
            maxSize = 8
        )
        assertThat(result).containsExactly("b", "c", "a").inOrder()
    }

    @Test
    fun pushToFrontOfRecentIcons_exceedsMaxSize_dropsOldest() {
        val result = WaterMarkRepository.pushToFrontOfRecentIcons(
            current = listOf("c", "b", "a"),
            newUri = "d",
            maxSize = 3
        )
        assertThat(result).containsExactly("d", "c", "b").inOrder()
    }

    @Test
    fun pushToFrontOfRecentIcons_emptyCurrent_singleEntry() {
        val result = WaterMarkRepository.pushToFrontOfRecentIcons(current = emptyList(), newUri = "a", maxSize = 8)
        assertThat(result).containsExactly("a")
    }

    @Test
    fun parseRecentIconUris_nullOrBlank_returnsEmpty() {
        assertThat(WaterMarkRepository.parseRecentIconUris(null)).isEmpty()
        assertThat(WaterMarkRepository.parseRecentIconUris("")).isEmpty()
        assertThat(WaterMarkRepository.parseRecentIconUris("   ")).isEmpty()
    }

    @Test
    fun parseRecentIconUris_roundTripsWithSerialize() {
        val uris = listOf("content://media/a", "content://media/b")
        val serialized = uris.joinToString("\n")

        val parsed = WaterMarkRepository.parseRecentIconUris(serialized)

        assertThat(parsed).containsExactly(Uri.parse("content://media/a"), Uri.parse("content://media/b")).inOrder()
    }

    @Test
    fun parseRecentIconUris_skipsBlankLines() {
        val parsed = WaterMarkRepository.parseRecentIconUris("content://media/a\n\ncontent://media/b")
        assertThat(parsed).containsExactly(Uri.parse("content://media/a"), Uri.parse("content://media/b")).inOrder()
    }
}
