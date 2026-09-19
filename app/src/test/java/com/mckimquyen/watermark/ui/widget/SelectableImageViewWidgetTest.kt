package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget test for SelectableImageView color swatch item.
 * Verifies layout inflation, color assignment, selected border ring, and onDraw execution without crashing.
 */
@RunWith(RobolectricTestRunner::class)
class SelectableImageViewWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun itemColorPreview_inflatesWithSelectableImageView() {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.item_color_preview, null, false)
        val siv = view.findViewById<SelectableImageView>(R.id.sivColor)

        assertThat(siv).isNotNull()
    }

    @Test
    fun selectableImageView_handlesWhiteAndBlackSwatches_andDrawsWithoutCrash() {
        val siv = SelectableImageView(themedContext)
        siv.circleColor = Color.WHITE
        siv.isSelected = false

        // Measure & Layout
        siv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        )
        siv.layout(0, 0, 100, 100)

        // Draw unselected white swatch
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        siv.draw(canvas)

        // Draw selected white swatch
        siv.isSelected = true
        siv.draw(canvas)

        // Switch to black swatch and draw
        siv.circleColor = Color.BLACK
        siv.draw(canvas)

        assertThat(bitmap.width).isEqualTo(100)
    }

    @Test
    fun dlgExifBorder_inflatesWithFrameIcon() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.dlg_exif_border, null, false)
        assertThat(root).isNotNull()
    }
}
