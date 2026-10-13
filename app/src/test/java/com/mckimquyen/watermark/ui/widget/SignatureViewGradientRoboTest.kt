package com.mckimquyen.watermark.ui.widget

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-80: `SignatureView.gradientColors` (var public) chỉ được áp shader khi có >= 2 màu; đọc 1 lần vào local
 * thay vì check-then-`!!`. Test đặc tả hành vi (refactor, không đổi hành vi): null / 1 màu → không shader.
 */
@RunWith(RobolectricTestRunner::class)
class SignatureViewGradientRoboTest {

    private fun strokeShaderAfterTouch(colors: IntArray?): Any? {
        val view = SignatureView(ApplicationProvider.getApplicationContext())
        view.gradientColors = colors
        val now = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        val field = SignatureView::class.java.getDeclaredField("strokes").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val strokes = field.get(view) as List<SignatureView.Stroke>
        assertThat(strokes).hasSize(1)
        return strokes.single().paint.shader
    }

    @Test
    fun nullGradient_noShader() {
        assertThat(strokeShaderAfterTouch(null)).isNull()
    }

    @Test
    fun singleColorGradient_noShader() {
        assertThat(strokeShaderAfterTouch(intArrayOf(Color.RED))).isNull()
    }

    @Test
    fun twoColorGradient_appliesShader() {
        assertThat(strokeShaderAfterTouch(intArrayOf(Color.RED, Color.BLUE))).isNotNull()
    }
}
