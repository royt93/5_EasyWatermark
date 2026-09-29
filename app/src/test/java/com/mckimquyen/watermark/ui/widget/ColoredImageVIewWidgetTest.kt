package com.mckimquyen.watermark.ui.widget

import android.animation.ValueAnimator
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
import com.mckimquyen.watermark.utils.ktx.colorPrimary
import com.mckimquyen.watermark.utils.ktx.colorSecondary
import com.mckimquyen.watermark.utils.ktx.colorTertiary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ColoredImageVIew (toolbar logo shimmer) truoc day co nhanh else hardcode 4 mau neon
 * (#FFA51F/#FFD703/#C0FF39/#00FFE0) khi thiet bi khong ho tro Dynamic Color (API < 31) -
 * pha vo Material You tren phan lon thiet bi vi minSdk=24. Test nay khoa lai: colorList
 * phai luon lay tu theme (colorPrimary/Secondary/Tertiary), khong con nhanh fallback rieng.
 */
@RunWith(RobolectricTestRunner::class)
class ColoredImageVIewWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    }

    @Test
    fun colorList_usesThemeColors_noHardcodedNeonFallback() {
        val view = ColoredImageVIew(themedContext)

        val field = ColoredImageVIew::class.java.getDeclaredField("colorList")
        field.isAccessible = true
        val colorList = (field.get(view) as IntArray).toList()

        val neonColors = listOf(
            Color.parseColor("#FFA51F"),
            Color.parseColor("#FFD703"),
            Color.parseColor("#C0FF39"),
            Color.parseColor("#00FFE0")
        )
        assertThat(colorList).containsNoneIn(neonColors)
        assertThat(colorList).containsExactly(
            themedContext.colorPrimary,
            themedContext.colorSecondary,
            themedContext.colorTertiary,
            themedContext.colorTertiary
        ).inOrder()
    }

    /**
     * BUG-42: cùng lỗi so `w != oldh` (thay vì `w != oldw`) với CircleImageView. Reset cờ về
     * false thủ công (mô phỏng đúng `onDraw()` "tiêu thụ" cờ trong app thật) trước khi trigger
     * layout thứ 2 — nếu không, cờ mặc định `true` lúc khởi tạo sẽ làm test pass giả ngay cả khi
     * còn bug (không phân biệt được công thức đúng/sai).
     */
    @Test
    fun layout_newWidthEqualsOldHeight_stillDetectsSizeChange() {
        val view = ColoredImageVIew(themedContext)
        view.layout(0, 0, 50, 100)
        view.sizeHasChanged = false

        view.layout(0, 0, 100, 100)

        // Công thức cũ (w != oldh || h != oldh) = (100!=100 || 100!=100) = false → bug.
        // Công thức đúng (w != oldw || h != oldh) = (100!=50 || 100!=100) = true.
        assertThat(view.sizeHasChanged).isTrue()
    }

    private fun readInnerBitmap(view: ColoredImageVIew): Bitmap? {
        val field = ColoredImageVIew::class.java.getDeclaredField("innerBitmap")
        field.isAccessible = true
        return field.get(view) as Bitmap?
    }

    /** `layout()` một mình không set `measuredWidth/Height` (chỉ set qua `measure()`) — `onDraw()`
     *  return sớm nếu `measuredWidth + measuredHeight <= 0`, nên test vẽ thật cần đo trước. */
    private fun measureAndLayout(view: View, size: Int, size2: Int = size) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size2, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, size, size2)
    }

    /**
     * BUG-AUDIT-2026-09-29: `onDraw()` trước đây cấp phát `innerBitmap` MỚI mỗi lần gọi, kể cả
     * khi size không đổi (colorAnimator INFINITE gọi postInvalidateDelayed liên tục → onDraw chạy
     * liên tục) — GC churn nặng. Vẽ 2 lần cùng size, `innerBitmap` phải là CÙNG 1 instance.
     */
    @Test
    fun draw_sameSizeTwice_reusesInnerBitmap_doesNotReallocateEveryFrame() {
        val view = ColoredImageVIew(themedContext).apply {
            setImageDrawable(ColorDrawable(Color.BLUE))
        }
        measureAndLayout(view, 64)
        val canvas = Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888))

        view.draw(canvas)
        val first = readInnerBitmap(view)
        view.draw(canvas)
        val second = readInnerBitmap(view)

        assertThat(first).isNotNull()
        assertThat(second).isSameInstanceAs(first)
        assertThat(first!!.isRecycled).isFalse()
    }

    /** Đổi size thật -> phải tạo bitmap mới VÀ recycle bản cũ (không bỏ rơi). */
    @Test
    fun draw_afterResize_recyclesOldInnerBitmap() {
        val view = ColoredImageVIew(themedContext).apply {
            setImageDrawable(ColorDrawable(Color.BLUE))
        }
        measureAndLayout(view, 50, 100)
        view.draw(Canvas(Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888)))
        val old = readInnerBitmap(view)

        measureAndLayout(view, 100, 100)
        view.draw(Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)))
        val new = readInnerBitmap(view)

        assertThat(new).isNotSameInstanceAs(old)
        assertThat(old!!.isRecycled).isTrue()
    }

    /**
     * BUG-AUDIT-2026-09-29: `onDetachedFromWindow()` trước đây gọi `pause()` — animator
     * `repeatCount=INFINITE` vẫn coi là "started" (chỉ tạm dừng tick), sống mãi trong
     * `AnimationHandler` giữ tham chiếu View/Context nếu view không bao giờ re-attach. Gắn view
     * vào 1 Activity thật (Robolectric, `.visible()` để thật sự attach vào WindowManager — thiếu
     * cờ này `isAttachedToWindow` vẫn false) rồi `removeView()` để trigger đúng lifecycle thật,
     * không gọi thẳng `onDetachedFromWindow()` (method protected, không truy cập được từ test).
     *
     * `animator.start()` gọi TRỰC TIẾP trong test (thay vì trông cậy `onAttachedToWindow()` của
     * view tự gọi) — Robolectric shadow không đảm bảo lan truyền callback `dispatchAttachedToWindow`
     * đủ để state "started" phản ánh đúng dù `isAttachedToWindow` đã true; mô phỏng đúng trạng thái
     * "đang chạy animation" mà không phụ thuộc timing riêng của shadow, phần đang test thật sự
     * (khác biệt cancel/pause) nằm ở `onDetachedFromWindow()`, không phải ở `onAttachedToWindow()`.
     */
    @Test
    fun onDetachedFromWindow_cancelsAnimator_animatorNoLongerStarted() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val view = ColoredImageVIew(themedContext).apply {
            setImageDrawable(ColorDrawable(Color.BLUE))
        }

        root.addView(view, ViewGroup.LayoutParams(64, 64))
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(view.isAttachedToWindow).isTrue()

        // Lưu ý: `ObjectAnimator.ofFloat(1f, 0.1f)` (2 tham số, không target/property) thực chất
        // resolve tới static method kế thừa từ ValueAnimator.ofFloat(vararg) — kiểu trả về thật là
        // ValueAnimator, không phải ObjectAnimator (chi tiết ngoài phạm vi finding đang fix).
        val delegateField = ColoredImageVIew::class.java.getDeclaredField("colorAnimator\$delegate")
        delegateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val animator = (delegateField.get(view) as Lazy<ValueAnimator>).value
        animator.start()
        assertThat(animator.isStarted).isTrue()

        root.removeView(view)
        shadowOf(Looper.getMainLooper()).idle()

        // cancel() (fix) -> isStarted về false ngay. pause() (bug cũ) sẽ giữ isStarted=true mãi.
        assertThat(animator.isStarted).isFalse()
    }
}
