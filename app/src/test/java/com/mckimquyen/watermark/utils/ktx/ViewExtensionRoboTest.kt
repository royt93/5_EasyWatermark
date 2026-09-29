package com.mckimquyen.watermark.utils.ktx

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * BUG-AUDIT-2026-09-29-R6: `View.disappear(toX, toY)` trước đây đảo tham số:
 * `translationY(toX)` + `translationX(toY)`. Với caller thật `ivDone.disappear()` dùng default
 * `(toX=0, toY=10dp)`, icon check trượt NGANG thay vì trượt XUỐNG khi fade-out.
 */
@RunWith(RobolectricTestRunner::class)
class ViewExtensionRoboTest {

    @Test
    fun disappear_appliesToXToTranslationX_andToYToTranslationY() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = View(activity)
        root.addView(view, ViewGroup.LayoutParams(50, 50))
        shadowOf(Looper.getMainLooper()).idle()

        view.disappear(toX = 12f, toY = 34f, toAlpha = 0.25f, duration = 10L)
        // Dẫn looper đi qua duration animation
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        assertThat(view.translationX).isEqualTo(12f)
        assertThat(view.translationY).isEqualTo(34f)
        assertThat(view.alpha).isEqualTo(0.25f)
        assertThat(view.visibility).isEqualTo(View.GONE)
    }
}
