package com.mckimquyen.watermark.ui.widget

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** BUG-82: ProgressImageView must stop progress animation when detached from its window. */
@RunWith(RobolectricTestRunner::class)
class ProgressImageViewLifecycleRoboTest {

    private var activityController: ActivityController<Activity>? = null

    private data class Fixture(val view: ProgressImageView, val root: ViewGroup, val animator: ValueAnimator)

    private fun fixture(): Fixture {
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible()
        activityController = controller
        val activity = controller.get()
        val context = ContextThemeWrapper(activity, R.style.Theme_MyApp)
        val view = ProgressImageView(context)
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.addView(view, ViewGroup.LayoutParams(TEST_VIEW_SIZE, TEST_VIEW_SIZE))
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(view.isAttachedToWindow).isTrue()

        val delegate = ProgressImageView::class.java.getDeclaredField(ANIMATOR_DELEGATE_FIELD).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        val animator = (delegate.get(view) as Lazy<ValueAnimator>).value
        animator.duration = TEST_ANIMATION_DURATION_MS
        return Fixture(view, root, animator)
    }

    @After
    fun tearDown() {
        activityController?.pause()?.stop()?.destroy()
        activityController = null
    }

    @Test
    fun detach_cancelsRunningAnimator() {
        val fixture = fixture()

        fixture.view.start()
        assertThat(fixture.animator.isStarted).isTrue()

        fixture.root.removeView(fixture.view)
        assertThat(fixture.view.isAttachedToWindow).isFalse()
        assertThat(fixture.animator.isStarted).isFalse()
        assertThat(fixture.animator.isRunning).isFalse()
    }

    @Test
    fun start_startsAnimator() {
        val fixture = fixture()

        fixture.view.start()

        assertThat(fixture.animator.isStarted).isTrue()
    }

    @Test
    fun failed_cancelsAnimator() {
        val fixture = fixture()
        fixture.view.start()
        assertThat(fixture.animator.isStarted).isTrue()

        fixture.view.failed()

        assertThat(fixture.animator.isStarted).isFalse()
    }

    @Test
    fun ready_cancelsAnimator() {
        val fixture = fixture()
        fixture.view.start()
        assertThat(fixture.animator.isStarted).isTrue()

        fixture.view.ready()

        assertThat(fixture.animator.isStarted).isFalse()
    }

    private companion object {
        const val ANIMATOR_DELEGATE_FIELD = "startAnimator\$delegate"
        const val TEST_ANIMATION_DURATION_MS = 10_000L
        const val TEST_VIEW_SIZE = 200
    }
}
