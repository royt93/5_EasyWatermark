package com.mckimquyen.watermark.ui.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ENH-16: `shouldRebuildShader()` — hàm thuần quyết định có rebuild shader (2 lần cấp phát
 * Bitmap) ở frame `onScale` hiện tại hay không, thay vì rebuild vô điều kiện mỗi frame (60-120
 * lần/giây khi pinch — nguyên nhân lag thật, xem ticket).
 */
class WaterMarkImageViewShaderThrottleTest {

    @Test
    fun elapsedLessThanThrottle_doesNotRebuild() {
        assertThat(WaterMarkImageView.shouldRebuildShader(nowMs = 1_000L, lastRebuildAtMs = 980L, throttleMs = 40L))
            .isFalse()
    }

    @Test
    fun elapsedExactlyThrottle_rebuilds() {
        // Ranh giới >= : đúng bằng throttle vẫn phải rebuild (không chờ thêm 1 frame nữa).
        assertThat(WaterMarkImageView.shouldRebuildShader(nowMs = 1_040L, lastRebuildAtMs = 1_000L, throttleMs = 40L))
            .isTrue()
    }

    @Test
    fun elapsedMoreThanThrottle_rebuilds() {
        assertThat(WaterMarkImageView.shouldRebuildShader(nowMs = 1_100L, lastRebuildAtMs = 1_000L, throttleMs = 40L))
            .isTrue()
    }

    @Test
    fun firstFrameOfGesture_lastRebuildAtZero_alwaysRebuilds() {
        // onScaleBegin() reset lastShaderRebuildAtMs = 0L — frame đầu tiên của phiên pinch mới
        // phải luôn rebuild ngay, không đợi throttle. `nowMs` mô phỏng SystemClock.elapsedRealtime()
        // thật (thời gian uptime thiết bị, luôn là số lớn — không phải mốc 0 lúc app khởi động).
        assertThat(WaterMarkImageView.shouldRebuildShader(nowMs = 123_456_789L, lastRebuildAtMs = 0L, throttleMs = 40L))
            .isTrue()
    }

    @Test
    fun rapidPinch_120fps_throttlesToAbout25fps() {
        // Mô phỏng 120 frame/giây (mỗi 8.33ms) trong 1 giây pinch liên tục — đếm số lần THẬT SỰ
        // rebuild với throttle 40ms, phải xấp xỉ 1000/40 = 25 lần (không phải 120 lần).
        var lastRebuildAtMs = 0L
        var rebuildCount = 0
        var frameTimeMs = 0.0
        while (frameTimeMs < 1_000.0) {
            val now = frameTimeMs.toLong()
            if (WaterMarkImageView.shouldRebuildShader(now, lastRebuildAtMs)) {
                rebuildCount++
                lastRebuildAtMs = now
            }
            frameTimeMs += 1000.0 / 120.0
        }

        assertThat(rebuildCount).isAtMost(30) // trần rộng rãi quanh mốc lý thuyết 25
        assertThat(rebuildCount).isLessThan(120) // điểm mấu chốt: giảm hẳn so với không throttle
    }
}
