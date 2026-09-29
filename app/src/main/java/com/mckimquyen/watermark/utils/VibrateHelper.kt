package com.mckimquyen.watermark.utils

import android.view.HapticFeedbackConstants
import android.view.View

class VibrateHelper private constructor() {

    private var latestVibration: Long = 0L
    private var cd: Long = DEFAULT_COOLDOWN_MS

    fun doVibrate(view: View) {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
            return
        }
        val now = System.currentTimeMillis()
        if (!shouldVibrate(now, latestVibration, cd)) {
            return
        }
        // BUG-AUDIT-2026-09-29-R7: trước đây latestVibration KHÔNG bao giờ được gán lại
        // sau khi rung -> cooldown 20ms vô tác dụng, gọi liên tục đều rung mọi lần.
        latestVibration = now
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 20L

        /**
         * Hàm thuần để test logic throttle thời gian mà không cần mock View/Haptic.
         */
        @androidx.annotation.VisibleForTesting
        internal fun shouldVibrate(now: Long, lastVibration: Long, cooldownMs: Long = DEFAULT_COOLDOWN_MS): Boolean {
            return now - lastVibration > cooldownMs
        }

        fun get(): VibrateHelper {
            return VibrateHelper()
        }
    }
}
