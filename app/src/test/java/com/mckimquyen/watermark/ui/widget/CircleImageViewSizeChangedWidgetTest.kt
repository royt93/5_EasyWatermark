package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-42: `onSizeChanged()` so `w != oldh` (thay vì `w != oldw`) — view GẦN VUÔNG (avatar/logo
 * tròn cỡ cố định, dùng `CircleImageView` trong About) rơi vào trường hợp width MỚI trùng height
 * CŨ, bỏ lỡ resize thật, `destCircleBitmap` giữ mask sai kích thước. `View.layout()` trong
 * Robolectric trigger đúng `onSizeChanged()` thật qua `setFrame()`, không cần gọi trực tiếp.
 */
@RunWith(RobolectricTestRunner::class)
class CircleImageViewSizeChangedWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    /** Đúng case nêu trong ticket: layout đầu 50x100, đổi sang 100x100 — w mới (100) == h cũ (100). */
    @Test
    fun layout_newWidthEqualsOldHeight_stillRecreatesCircleBitmapAtNewSize() {
        val view = CircleImageView(themedContext)

        view.layout(0, 0, 50, 100)
        val firstBitmap = view.destCircleBitmap
        assertThat(firstBitmap?.width).isEqualTo(50)

        view.layout(0, 0, 100, 100)
        val secondBitmap = view.destCircleBitmap

        // Trước fix: sizeHasChanged sai → destCircleBitmap giữ nguyên bản 50x100 cũ.
        assertThat(secondBitmap).isNotSameInstanceAs(firstBitmap)
        assertThat(secondBitmap?.width).isEqualTo(100)
    }

    @Test
    fun layout_sameSize_doesNotRecreateCircleBitmap() {
        val view = CircleImageView(themedContext)
        view.layout(0, 0, 64, 64)
        val firstBitmap = view.destCircleBitmap

        view.layout(0, 0, 64, 64)
        val secondBitmap = view.destCircleBitmap

        assertThat(secondBitmap).isSameInstanceAs(firstBitmap)
    }

    private fun readSourceImageBitmap(view: CircleImageView): Bitmap? {
        val field = CircleImageView::class.java.getDeclaredField("sourceImageBitmap")
        field.isAccessible = true
        return field.get(view) as Bitmap?
    }

    private fun measureAndLayout(view: View, w: Int, h: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)
    }

    /**
     * BUG-AUDIT-2026-09-29: `sourceImageBitmap` bị ghi đè thẳng trong `onDraw()` khi resize, không
     * recycle bản cũ — bất đối xứng với `destCircleBitmap` ngay bên cạnh (`onSizeChanged`) vốn đã
     * recycle đúng. Resize thật (50x100 -> 100x100) qua `draw()` (kích hoạt `onDraw()` thật).
     */
    @Test
    fun draw_afterResize_recyclesOldSourceImageBitmap() {
        val view = CircleImageView(themedContext).apply { setImageDrawable(ColorDrawable(Color.BLUE)) }
        measureAndLayout(view, 50, 100)
        view.draw(Canvas(Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888)))
        val old = readSourceImageBitmap(view)
        assertThat(old).isNotNull()

        measureAndLayout(view, 100, 100)
        view.draw(Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)))
        val new = readSourceImageBitmap(view)

        assertThat(new).isNotSameInstanceAs(old)
        assertThat(old!!.isRecycled).isTrue()
    }

    @Test
    fun draw_sameSizeTwice_reusesSourceImageBitmap_doesNotRecycleLiveBitmap() {
        val view = CircleImageView(themedContext).apply { setImageDrawable(ColorDrawable(Color.BLUE)) }
        measureAndLayout(view, 64, 64)
        val canvas = Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))

        view.draw(canvas)
        val first = readSourceImageBitmap(view)
        view.draw(canvas)
        val second = readSourceImageBitmap(view)

        assertThat(second).isSameInstanceAs(first)
        assertThat(first!!.isRecycled).isFalse()
    }
}
