package com.mckimquyen.watermark.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Interstitial sau khi lưu ảnh phải đủ trễ để ngón tay user rời khỏi nút "Lưu" (tránh click nhầm = invalid traffic). */
class InterstitialDelayTest {

    @Test
    fun interstitialDelay_isAtLeastTwoSeconds() {
        assertThat(MainActivity.INTERSTITIAL_DELAY_MS).isAtLeast(2_000L)
    }

    @Test
    fun interstitialDelay_staysBoundedSoAdStillFollowsTheAction() {
        // Quá dài thì ad không còn gắn với hành động vừa xong (user đã đi chỗ khác) → ad "bất ngờ".
        assertThat(MainActivity.INTERSTITIAL_DELAY_MS).isAtMost(5_000L)
    }
}
