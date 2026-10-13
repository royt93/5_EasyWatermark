package com.mckimquyen.watermark.ui.widget

import android.animation.ValueAnimator
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.about.OpenSourceActivity
import org.junit.Test
import org.junit.runner.RunWith

/** BUG-82: verify Android window detach cancels a running progress animator. */
@RunWith(AndroidJUnit4::class)
class ProgressImageViewLifecycleIntegrationTest {

    @Test
    fun detachedView_cancelsRunningProgressAnimator() {
        ActivityScenario.launch(OpenSourceActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val view = ProgressImageView(activity)
                root.addView(view, ViewGroup.LayoutParams(TEST_VIEW_SIZE, TEST_VIEW_SIZE))
                val animator = animatorFor(view).apply { duration = TEST_ANIMATION_DURATION_MS }

                view.start()
                assertThat(animator.isStarted).isTrue()

                root.removeView(view)

                assertThat(view.isAttachedToWindow).isFalse()
                assertThat(animator.isStarted).isFalse()
                assertThat(animator.isRunning).isFalse()
            }
        }
    }

    private fun animatorFor(view: ProgressImageView): ValueAnimator {
        val delegate = ProgressImageView::class.java.getDeclaredField(ANIMATOR_DELEGATE_FIELD).apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        return (delegate.get(view) as Lazy<ValueAnimator>).value
    }

    private companion object {
        const val ANIMATOR_DELEGATE_FIELD = "startAnimator\$delegate"
        const val TEST_ANIMATION_DURATION_MS = 10_000L
        const val TEST_VIEW_SIZE = 200
    }
}
