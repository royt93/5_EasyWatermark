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
 * FEAT-24: [WaterMarkRepository.updateIcon] phải đẩy uri lên đầu danh sách MRU (đọc lại qua
 * `WaterMark.recentIconUris`) — mới nhất đứng đầu, không nhân đôi khi chọn lại cùng 1 icon, giới
 * hạn [WaterMarkRepository.MAX_RECENT_ICONS]. Dùng DataStore cô lập (`newTestWaterMarkDataStore`)
 * — KHÔNG dùng singleton thật, tránh đúng lớp flaky đã ghi nhận ở `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryRecentIconsRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    private fun uri(name: String) = Uri.parse("content://media/$name")

    @Test
    fun updateIcon_freshRepo_hasNoRecentIcons() = runBlocking {
        val waterMark = repo.waterMark.first()
        assertThat(waterMark.recentIconUris).isEmpty()
    }

    @Test
    fun updateIcon_pushesNewUriToFront() = runBlocking {
        repo.updateIcon(uri("a"))
        repo.updateIcon(uri("b"))
        repo.updateIcon(uri("c"))

        val recents = repo.waterMark.first().recentIconUris
        assertThat(recents).containsExactly(uri("c"), uri("b"), uri("a")).inOrder()
    }

    @Test
    fun updateIcon_reselectingExistingUri_movesToFrontWithoutDuplicate() = runBlocking {
        repo.updateIcon(uri("a"))
        repo.updateIcon(uri("b"))
        repo.updateIcon(uri("c"))

        repo.updateIcon(uri("a")) // chọn lại icon đã dùng trước đó (không phải mới nhất)

        val recents = repo.waterMark.first().recentIconUris
        assertThat(recents).containsExactly(uri("a"), uri("c"), uri("b")).inOrder()
    }

    @Test
    fun updateIcon_beyondMaxRecentIcons_dropsOldest() = runBlocking {
        repeat(WaterMarkRepository.MAX_RECENT_ICONS + 3) { index ->
            repo.updateIcon(uri("icon_$index"))
        }

        val recents = repo.waterMark.first().recentIconUris
        assertThat(recents).hasSize(WaterMarkRepository.MAX_RECENT_ICONS)
        // Mới nhất (icon cuối cùng vừa gọi) phải ở đầu — không bị cắt nhầm phần đầu.
        assertThat(recents.first()).isEqualTo(uri("icon_${WaterMarkRepository.MAX_RECENT_ICONS + 2}"))
    }

    @Test
    fun updateIcon_alsoUpdatesActiveIconUri_sameAsBeforeFeat24() = runBlocking {
        repo.updateIcon(uri("a"))

        assertThat(repo.waterMark.first().iconUri).isEqualTo(uri("a"))
    }
}
