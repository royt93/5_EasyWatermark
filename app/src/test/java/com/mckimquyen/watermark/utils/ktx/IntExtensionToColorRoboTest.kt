package com.mckimquyen.watermark.utils.ktx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class IntExtensionToColorRoboTest {

    @Test
    fun toColor_animatorSurvivesGcAndReachesTargetColor() {
        val from = Color.rgb(12, 34, 56)
        val to = Color.rgb(200, 150, 100)
        var last = from
        var cancelCount = 0

        val animator = from.toColor(to, autoStart = false, duration = DURATION_MS) { last = it.animatedValue as Int }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                cancelCount++
            }
        })
        animator.start()
        // Int boxing không còn là target của ObjectAnimator -> GC không được phép tự huỷ animator.
        repeat(GC_ROUNDS) { System.gc() }
        shadowOf(Looper.getMainLooper()).idleFor(DURATION_MS * 2, TimeUnit.MILLISECONDS)

        // Robolectric hoàn tất animator trong 1 tick nên không assert isRunning giữa chừng.
        assertThat(cancelCount).isEqualTo(0)
        assertThat(animator.isRunning).isFalse()
        assertThat(last).isEqualTo(to)
    }

    @Test
    fun toColor_autoStartFalse_doesNotStart() {
        val animator = Color.RED.toColor(Color.BLUE, autoStart = false)

        assertThat(animator.isStarted).isFalse()
    }

    private companion object {
        const val DURATION_MS = 200L
        const val GC_ROUNDS = 5
    }
}
