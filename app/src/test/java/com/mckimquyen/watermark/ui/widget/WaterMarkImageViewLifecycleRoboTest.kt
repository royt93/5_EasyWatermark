package com.mckimquyen.watermark.ui.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-AUDIT-2026-09-29-R6: WaterMarkImageView phải huỷ mọi resource lifecycle-bound khi detach:
 * - `bgJob` của applyBg() (trước đây launch fire-and-forget, Palette xong vẫn callback Activity cũ),
 * - `drawableAlphaAnimator`,
 * - `animator` của backToCenter().
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewLifecycleRoboTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    @Test
    fun onDetachedFromWindow_cancelsBgJobAndBothAnimators() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = WaterMarkImageView(themedContext)
        root.addView(view, ViewGroup.LayoutParams(200, 200))
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(view.isAttachedToWindow).isTrue()

        val bgJob = Job()
        view.setBgJobForTesting(bgJob)

        // Lazy animator của drawable: force init + start để mô phỏng animation đang chạy.
        val alphaDelegateField = WaterMarkImageView::class.java.getDeclaredField("drawableAlphaAnimator\$delegate")
        alphaDelegateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val alphaAnimator = (alphaDelegateField.get(view) as Lazy<ValueAnimator>).value
        alphaAnimator.start()
        assertThat(alphaAnimator.isStarted).isTrue()

        // Animator backToCenter(): inject animator đang chạy, không cần dựng đủ gesture + shader.
        val centerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10_000L
            start()
        }
        val centerField = WaterMarkImageView::class.java.getDeclaredField("animator")
        centerField.isAccessible = true
        centerField.set(view, centerAnimator as Animator)
        assertThat(centerAnimator.isStarted).isTrue()

        root.removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(bgJob.isCancelled).isTrue()
        assertThat(alphaAnimator.isStarted).isFalse()
        assertThat(centerAnimator.isStarted).isFalse()
    }
}
