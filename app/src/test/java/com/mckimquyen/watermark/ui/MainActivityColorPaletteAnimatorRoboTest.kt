package com.mckimquyen.watermark.ui

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Review pass 9 (2026-09-30): observer `viewModel.colorPalette` tạo `bgTransformAnimator`/
 * `funcTextColorAnimator` MỚI mỗi lần Palette đổi (mỗi lần chọn ảnh khác) nhưng trước đây KHÔNG
 * cancel animator cũ trước khi ghi đè field — đổi ảnh nhanh (vuốt dải thumbnail, Palette sinh ra
 * trước khi animator cũ chạy xong ANIMATION_DURATION) khiến 2 `ObjectAnimator` cùng ghi
 * backgroundColor/textColor lên cùng view (race/nhấp nháy màu), và animator cũ tiếp tục chạy
 * ngầm sau khi Activity đã destroy (chỉ animator MỚI NHẤT bị cancel ở onDestroy() vì field đã bị
 * ghi đè, animator cũ mất tham chiếu). Fix: cancel cả 2 field trước khi gán animator mới.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityColorPaletteAnimatorRoboTest {

    private fun MainActivity.viewModelField(): MainViewModel =
        MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
            .invoke(this) as MainViewModel

    private fun MainActivity.bgTransformAnimatorField(): ObjectAnimator? =
        MainActivity::class.java.getDeclaredField("bgTransformAnimator").apply { isAccessible = true }
            .get(this) as ObjectAnimator?

    private fun MainActivity.funcTextColorAnimatorField(): ObjectAnimator? =
        MainActivity::class.java.getDeclaredField("funcTextColorAnimator").apply { isAccessible = true }
            .get(this) as ObjectAnimator?

    /** Palette.generate() chạy đồng bộ (không callback) khi gọi trực tiếp trên Bitmap có sẵn. */
    private fun fakePalette(color: Int): Palette {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return Palette.from(bitmap).generate()
    }

    @Test
    fun `doi Palette lan 2 - cancel ca 2 animator cu truoc khi tao animator moi`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        // create()+start() đủ để initObserver() gắn observer + launchView có window Handler cho
        // ObjectAnimator (không resume()/visible() — cùng lý do tránh crash không liên quan đã
        // ghi trong MainActivityInterstitialRoboTest).
        val activity = controller.create().start().get()
        val viewModel = activity.viewModelField()

        viewModel.colorPalette.value = fakePalette(Color.RED)
        val bgAnim1 = activity.bgTransformAnimatorField()
        val funcAnim1 = activity.funcTextColorAnimatorField()
        assertThat(bgAnim1).isNotNull()
        assertThat(funcAnim1).isNotNull()
        assertThat(bgAnim1!!.isStarted).isTrue()
        assertThat(funcAnim1!!.isStarted).isTrue()

        viewModel.colorPalette.value = fakePalette(Color.BLUE)
        val bgAnim2 = activity.bgTransformAnimatorField()
        val funcAnim2 = activity.funcTextColorAnimatorField()

        // Animator MỚI là instance khác, animator CŨ phải đã bị cancel (isStarted=false) thay vì
        // tiếp tục chạy ngầm không kiểm soát được.
        assertThat(bgAnim2).isNotSameInstanceAs(bgAnim1)
        assertThat(funcAnim2).isNotSameInstanceAs(funcAnim1)
        assertThat(bgAnim1.isStarted).isFalse()
        assertThat(funcAnim1.isStarted).isFalse()
    }

    @Test
    fun `onDestroy cancel ca bgTransformAnimator va funcTextColorAnimator`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().get()
        val viewModel = activity.viewModelField()

        viewModel.colorPalette.value = fakePalette(Color.RED)
        val bgAnim = activity.bgTransformAnimatorField()
        val funcAnim = activity.funcTextColorAnimatorField()
        assertThat(bgAnim!!.isStarted).isTrue()
        assertThat(funcAnim!!.isStarted).isTrue()

        controller.destroy()

        assertThat(bgAnim.isStarted).isFalse()
        assertThat(funcAnim.isStarted).isFalse()
        assertThat(activity.bgTransformAnimatorField()).isNull()
        assertThat(activity.funcTextColorAnimatorField()).isNull()
    }
}
