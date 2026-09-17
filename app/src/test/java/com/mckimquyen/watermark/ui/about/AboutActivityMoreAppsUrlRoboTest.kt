package com.mckimquyen.watermark.ui.about

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-30: nút "More Apps" từng dùng `store/apps/developer?id=SAIGON PHANTOM LABS` — trang này yêu
 * cầu Developer ID SỐ (không phải tên công ty), và khoảng trắng chưa được URL-encode. Đổi sang
 * `store/search?q=pub:` (tìm theo tên publisher, đúng cách chuẩn khi không có ID số) + encode.
 */
@RunWith(RobolectricTestRunner::class)
class AboutActivityMoreAppsUrlRoboTest {

    @Test
    fun buildMoreAppsUrl_encodesSpacesInDeveloperName() {
        val url = AboutActivity.buildMoreAppsUrl("SAIGON PHANTOM LABS")

        assertThat(url).doesNotContain(" ")
        assertThat(url).contains("SAIGON%20PHANTOM%20LABS")
    }

    @Test
    fun buildMoreAppsUrl_usesSearchPubQuery_notDeveloperIdPath() {
        val url = AboutActivity.buildMoreAppsUrl("SAIGON PHANTOM LABS")

        assertThat(url).startsWith("https://play.google.com/store/search?q=pub:")
        assertThat(url).doesNotContain("apps/developer?id=")
    }
}
