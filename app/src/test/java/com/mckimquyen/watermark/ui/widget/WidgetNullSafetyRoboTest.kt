package com.mckimquyen.watermark.ui.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-80 (R5 null-safety): characterization test cho `SelectableImageView` / `CircleImageView` sau khi bỏ `!!`.
 * Không tái hiện được NPE gốc (single-thread, giá trị vừa gán), nên khoá hành vi: vẽ sau detach/khi chưa có
 * bitmap không được crash, và vẽ bình thường vẫn ra pixel đúng.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetNullSafetyRoboTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    private fun layout(v: View, size: Int) {
        v.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        )
        v.layout(0, 0, size, size)
    }

    @Test
    fun selectableImageView_drawAfterDetach_doesNotCrash() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val siv = SelectableImageView(themedContext).apply { circleColor = Color.RED }
        root.addView(siv, ViewGroup.LayoutParams(SIZE, SIZE))
        shadowOf(Looper.getMainLooper()).idle()

        root.removeView(siv) // onDetachedFromWindow recycle + null srcBitmap
        shadowOf(Looper.getMainLooper()).idle()
        layout(siv, SIZE)

        siv.draw(Canvas(Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)))
    }

    @Test
    fun selectableImageView_attrsConstructor_acceptsNonNullContext() {
        val siv = SelectableImageView(themedContext, null)
        assertThat(siv).isNotNull()
    }

    @Test
    fun circleImageView_drawWithDrawable_masksToCircle() {
        val civ = CircleImageView(themedContext).apply { setImageDrawable(ColorDrawable(Color.RED)) }
        layout(civ, SIZE)
        // Lần draw đầu chỉ khởi tạo sourceImageBitmap (super.onDraw vẽ drawable chưa mask) rồi invalidate;
        // trạng thái ổn định đo ở lần draw sau trên canvas mới.
        civ.draw(Canvas(Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)))
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

        civ.draw(Canvas(out))

        // Tâm có màu nguồn (mask không xoá mất ảnh, không NPE ở `mask`).
        assertThat(out.getPixel(SIZE / 2, SIZE / 2)).isEqualTo(Color.RED)
        assertThat(civ.destCircleBitmap).isNotNull()
    }

    @Test
    fun circleImageView_drawWithoutDrawable_doesNotCrash() {
        val civ = CircleImageView(themedContext)
        layout(civ, SIZE)

        civ.draw(Canvas(Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)))
    }

    private companion object {
        const val SIZE = 100
    }
}
