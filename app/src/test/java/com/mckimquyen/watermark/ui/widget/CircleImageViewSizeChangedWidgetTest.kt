package com.mckimquyen.watermark.ui.widget

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
}
