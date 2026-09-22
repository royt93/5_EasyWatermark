package com.mckimquyen.watermark.ui.widget

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-18: [WaterMarkImageView.compareRevealFraction] — property điều khiển slider so sánh
 * trước/sau, coerce trong [0,1].
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewCompareRoboTest {

    private fun newView(): WaterMarkImageView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return WaterMarkImageView(context)
    }

    @Test
    fun compareRevealFraction_defaultsToOne() {
        val view = newView()
        assertThat(view.compareRevealFraction).isEqualTo(1f)
    }

    @Test
    fun compareRevealFraction_coercesAboveOne_toOne() {
        val view = newView()
        view.compareRevealFraction = 1.5f
        assertThat(view.compareRevealFraction).isEqualTo(1f)
    }

    @Test
    fun compareRevealFraction_coercesBelowZero_toZero() {
        val view = newView()
        view.compareRevealFraction = -0.3f
        assertThat(view.compareRevealFraction).isEqualTo(0f)
    }

    @Test
    fun compareRevealFraction_validValue_setsExactly() {
        val view = newView()
        view.compareRevealFraction = 0.42f
        assertThat(view.compareRevealFraction).isEqualTo(0.42f)
    }
}
