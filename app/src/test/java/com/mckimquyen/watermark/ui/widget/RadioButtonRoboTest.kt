package com.mckimquyen.watermark.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RadioButtonRoboTest {

    private lateinit var context: Context
    private lateinit var radioButton: RadioButton

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyApp)
        radioButton = RadioButton(context)
    }

    @Test
    fun initialState_isNotChecked() {
        assertThat(radioButton.isChecked).isFalse()
    }

    @Test
    fun toggle_flipsCheckedState() {
        radioButton.toggle()
        assertThat(radioButton.isChecked).isTrue()

        radioButton.toggle()
        assertThat(radioButton.isChecked).isFalse()
    }

    @Test
    fun setChecked_triggersListener() {
        var observedState: Boolean? = null
        radioButton.setOnCheckedChangeListener { isChecked ->
            observedState = isChecked
        }

        radioButton.isChecked = true
        assertThat(observedState).isTrue()

        radioButton.isChecked = false
        assertThat(observedState).isFalse()
    }

    @Test
    fun draw_bothStates_doesNotCrash() {
        radioButton.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        )
        radioButton.layout(0, 0, 100, 100)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw unchecked
        radioButton.isChecked = false
        radioButton.draw(canvas)

        // Draw checked
        radioButton.isChecked = true
        radioButton.draw(canvas)
    }
}
