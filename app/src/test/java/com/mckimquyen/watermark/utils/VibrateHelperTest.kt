package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-AUDIT-2026-09-29-R7: `VibrateHelper` trước đây không bao giờ cập nhật `latestVibration`
 * sau khi rung, khiến cooldown `cd=20ms` vô hiệu hoá.
 */
class VibrateHelperTest {

    @Test
    fun shouldVibrate_whenFirstTime_returnsTrue() {
        val now = 1_000L
        val lastVibration = 0L

        assertThat(VibrateHelper.shouldVibrate(now, lastVibration)).isTrue()
    }

    @Test
    fun shouldVibrate_withinCooldown_returnsFalse() {
        val lastVibration = 1_000L
        val now = 1_010L // chỉ cách 10ms (< 20ms cooldown)

        assertThat(VibrateHelper.shouldVibrate(now, lastVibration)).isFalse()
    }

    @Test
    fun shouldVibrate_exactlyAtCooldown_returnsFalse() {
        val lastVibration = 1_000L
        val now = 1_020L // đúng 20ms

        assertThat(VibrateHelper.shouldVibrate(now, lastVibration)).isFalse()
    }

    @Test
    fun shouldVibrate_afterCooldown_returnsTrue() {
        val lastVibration = 1_000L
        val now = 1_021L // 21ms (> 20ms cooldown)

        assertThat(VibrateHelper.shouldVibrate(now, lastVibration)).isTrue()
    }

    @Test
    fun shouldVibrate_withCustomCooldown_respectsCustomValue() {
        val lastVibration = 1_000L

        assertThat(VibrateHelper.shouldVibrate(now = 1_049L, lastVibration = lastVibration, cooldownMs = 50L)).isFalse()
        assertThat(VibrateHelper.shouldVibrate(now = 1_051L, lastVibration = lastVibration, cooldownMs = 50L)).isTrue()
    }
}
