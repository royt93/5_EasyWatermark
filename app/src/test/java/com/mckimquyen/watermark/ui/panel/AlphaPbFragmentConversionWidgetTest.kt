package com.mckimquyen.watermark.ui.panel

import android.graphics.Color
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** ENH-48: verify actual panel slider callback rounds and formats persisted alpha consistently. */
@RunWith(RobolectricTestRunner::class)
class AlphaPbFragmentConversionWidgetTest {

    @Test
    fun userChangesAlphaSliderTo99_percentRestoresAs99() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        val fragment = AlphaPbFragment()
        activity.supportFragmentManager.beginTransaction().add(fragment, TEST_TAG).commit()
        shadowOf(Looper.getMainLooper()).idle()

        val slider = requireNotNull(fragment.view).findViewById<Slider>(R.id.slideContentSize)
        assertThat(slider.valueFrom).isEqualTo(SLIDER_MIN_PERCENT)
        assertThat(slider.valueTo).isEqualTo(SLIDER_MAX_PERCENT)

        fragment.doOnChange(slider, TARGET_PERCENT, fromUser = true)
        val deadline = System.currentTimeMillis() + VIEW_MODEL_TIMEOUT_MS
        while ((viewModel.waterMark.value?.alpha != EXPECTED_NATIVE_ALPHA) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(POLL_INTERVAL_MS)
        }

        val persistedModel = requireNotNull(viewModel.waterMark.value)
        assertThat(persistedModel.alpha).isEqualTo(EXPECTED_NATIVE_ALPHA)
        assertThat(fragment.formatValue(persistedModel)).isEqualTo(TARGET_PERCENT)
        assertThat(fragment.formatValueTips(persistedModel)).contains("99")
        assertThat(fragment.formatValue(sampleWaterMark(alpha = MAX_NATIVE_ALPHA))).isEqualTo(SLIDER_MAX_PERCENT)
    }

    private fun sampleWaterMark(alpha: Int) = WaterMark(
        text = "sample",
        textSize = 24f,
        textColor = Color.BLACK,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = alpha,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = Uri.EMPTY,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false
    )

    private companion object {
        const val TEST_TAG = "alpha-conversion-test"
        const val SLIDER_MIN_PERCENT = 0f
        const val SLIDER_MAX_PERCENT = 100f
        const val TARGET_PERCENT = 99f
        const val EXPECTED_NATIVE_ALPHA = 252
        const val MAX_NATIVE_ALPHA = 255
        const val VIEW_MODEL_TIMEOUT_MS = 3_000L
        const val POLL_INTERVAL_MS = 20L
    }
}
