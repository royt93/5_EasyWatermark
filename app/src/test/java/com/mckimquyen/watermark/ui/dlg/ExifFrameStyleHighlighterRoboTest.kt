package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget test (Robolectric): [ExifFrameStyleHighlighter] tô viền đúng nút đang chọn trong
 * [ExifPbFragment] — tách khỏi Fragment/Hilt để test trực tiếp (theo cách [FuncPanelAdapterRoboTest]
 * test [com.mckimquyen.watermark.ui.adapter.FuncPanelAdapter] mà không cần Activity).
 */
@RunWith(RobolectricTestRunner::class)
class ExifFrameStyleHighlighterRoboTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_MyApp)

    private fun button() = MaterialButton(themedContext)

    private fun buttons() = mapOf(
        ExifFrameStyle.CLASSIC to button(),
        ExifFrameStyle.POLAROID to button(),
        ExifFrameStyle.FILM_STRIP to button(),
        ExifFrameStyle.MINIMAL to button()
    )

    @Test
    fun apply_selectedButton_getsPrimaryStrokeColor() {
        val buttons = buttons()

        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.POLAROID)

        val expected = ContextCompat.getColor(context, R.color.glass_text_primary)
        assertThat(buttons.getValue(ExifFrameStyle.POLAROID).strokeColor?.defaultColor).isEqualTo(expected)
    }

    @Test
    fun apply_unselectedButtons_getBorderStrokeColor() {
        val buttons = buttons()

        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.POLAROID)

        val expected = ContextCompat.getColor(context, R.color.glass_border)
        assertThat(buttons.getValue(ExifFrameStyle.CLASSIC).strokeColor?.defaultColor).isEqualTo(expected)
        assertThat(buttons.getValue(ExifFrameStyle.FILM_STRIP).strokeColor?.defaultColor).isEqualTo(expected)
        assertThat(buttons.getValue(ExifFrameStyle.MINIMAL).strokeColor?.defaultColor).isEqualTo(expected)
    }

    @Test
    fun apply_selectedButton_hasThickerStrokeThanUnselected() {
        val buttons = buttons()

        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.MINIMAL)

        assertThat(buttons.getValue(ExifFrameStyle.MINIMAL).strokeWidth)
            .isGreaterThan(buttons.getValue(ExifFrameStyle.CLASSIC).strokeWidth)
    }

    @Test
    fun apply_switchingSelection_revertsPreviousButtonToUnselected() {
        val buttons = buttons()
        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.CLASSIC)

        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.FILM_STRIP)

        val expectedUnselected = ContextCompat.getColor(context, R.color.glass_border)
        val expectedSelected = ContextCompat.getColor(context, R.color.glass_text_primary)
        assertThat(buttons.getValue(ExifFrameStyle.CLASSIC).strokeColor?.defaultColor).isEqualTo(expectedUnselected)
        assertThat(buttons.getValue(ExifFrameStyle.FILM_STRIP).strokeColor?.defaultColor).isEqualTo(expectedSelected)
    }

    @Test
    fun apply_everyStyleAsSelected_exactlyOneButtonHighlighted() {
        val buttons = buttons()

        ExifFrameStyle.entries.forEach { selected ->
            ExifFrameStyleHighlighter.apply(buttons, selected)

            val expectedSelected = ContextCompat.getColor(context, R.color.glass_text_primary)
            val highlightedCount = buttons.values.count { it.strokeColor?.defaultColor == expectedSelected }
            assertThat(highlightedCount).isEqualTo(1)
            assertThat(buttons.getValue(selected).strokeColor?.defaultColor).isEqualTo(expectedSelected)
        }
    }
}
