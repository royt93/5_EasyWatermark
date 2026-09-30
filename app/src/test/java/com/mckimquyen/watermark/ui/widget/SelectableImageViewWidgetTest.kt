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

    /**
     * Review pass 11: `circleResId` setter/onSizeChanged trước đây ghi đè `srcBitmap` bằng bitmap
     * màu/vector MỚI nhưng không recycle bitmap CŨ do chính view sở hữu. RecyclerView rebind swatch
     * nhiều lần sẽ tích lũy bitmap native cho tới GC.
     */
    @Test
    fun rebindColorSwatch_recyclesPreviousOwnedBitmap() {
        val siv = SelectableImageView(themedContext)
        siv.circleColor = Color.RED
        siv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        )
        siv.layout(0, 0, 100, 100)

        val field = SelectableImageView::class.java.getDeclaredField("srcBitmap").apply { isAccessible = true }
        val oldBitmap = field.get(siv) as Bitmap
        assertThat(oldBitmap.isRecycled).isFalse()

        // Mô phỏng ColorPreviewAdapter rebind cùng ViewHolder: circleColor trước, circleResId sau.
        siv.circleColor = Color.BLUE
        siv.circleResId = -1

        assertThat(oldBitmap.isRecycled).isTrue()
        assertThat(field.get(siv)).isNotSameInstanceAs(oldBitmap)
    }

    @Test
    fun circleColorSetter_refreshesOwnedBitmapImmediately() {
        val siv = SelectableImageView(themedContext)
        siv.circleColor = Color.RED
        siv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        )
        siv.layout(0, 0, 100, 100)

        val field = SelectableImageView::class.java.getDeclaredField("srcBitmap").apply { isAccessible = true }
        val redBitmap = field.get(siv) as Bitmap

        siv.circleColor = Color.BLUE

        val blueBitmap = field.get(siv) as Bitmap
        assertThat(blueBitmap).isNotSameInstanceAs(redBitmap)
        assertThat(redBitmap.isRecycled).isTrue()
        assertThat(blueBitmap.getPixel(50, 50)).isEqualTo(Color.BLUE)
    }

    @Test
    fun dlgExifBorder_inflatesWithFrameIcon() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.dlg_exif_border, null, false)
        assertThat(root).isNotNull()
    }
}
