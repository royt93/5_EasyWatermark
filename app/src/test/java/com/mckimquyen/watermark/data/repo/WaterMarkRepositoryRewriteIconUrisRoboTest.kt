package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-64: [WaterMarkRepository.rewriteIconUris] migrate URI icon đã persist (icon hiện tại + MRU) trong
 * MỘT lần ghi: URI còn sống được thay, URI chết bị loại, mọi cấu hình khác (kể cả QR động) giữ nguyên.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryRewriteIconUrisRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun uri(name: String) = Uri.parse("content://x/$name")

    @Test
    fun rewrite_replacesCurrentIconAndRecents_dropsDeadOnes() = runBlocking {
        repo.updateIcon(uri("live"))
        repo.updateIcon(uri("dead"))
        repo.updateIcon(uri("keep"))

        repo.rewriteIconUris { u ->
            when (u.lastPathSegment) {
                "live" -> uri("live-new")
                "dead" -> null
                else -> u
            }
        }

        val mark = repo.waterMark.first()
        assertThat(mark.iconUri).isEqualTo(uri("keep"))
        assertThat(mark.recentIconUris).containsExactly(uri("keep"), uri("live-new")).inOrder()
    }

    @Test
    fun rewrite_deadCurrentIcon_becomesEmpty() = runBlocking {
        repo.updateIcon(uri("dead"))

        repo.rewriteIconUris { null }

        val mark = repo.waterMark.first()
        assertThat(mark.iconUri.toString()).isEmpty()
        assertThat(mark.recentIconUris).isEmpty()
    }

    @Test
    fun rewrite_keepsQrDynamicConfig() = runBlocking {
        repo.updateQrDynamicConfig(uri("qr-dyn"), template = "{filename}", portfolioLink = "https://a.b")

        repo.rewriteIconUris { it }

        val mark = repo.waterMark.first()
        assertThat(mark.qrDynamicEnabled).isTrue()
        assertThat(mark.qrContentTemplate).isEqualTo("{filename}")
        assertThat(mark.qrPortfolioLink).isEqualTo("https://a.b")
    }

    @Test
    fun rewrite_mergedDuplicates_collapsedAndCappedAtMax() = runBlocking {
        repeat(WaterMarkRepository.MAX_RECENT_ICONS) { repo.updateIcon(uri("i$it")) }

        repo.rewriteIconUris { uri("same") }

        assertThat(repo.waterMark.first().recentIconUris).containsExactly(uri("same"))
        Unit
    }

    @Test
    fun rewrite_freshRepo_doesNotThrow() = runBlocking {
        repo.rewriteIconUris { null }

        assertThat(repo.waterMark.first().recentIconUris).isEmpty()
    }
}
