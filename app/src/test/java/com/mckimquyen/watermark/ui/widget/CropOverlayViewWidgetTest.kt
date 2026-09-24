package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.max
import kotlin.math.min

/**
 * FEAT-16: [CropOverlayView] — thiết kế "pan/zoom ảnh sau khung crop cố định theo tỉ lệ".
 * Trọng tâm test: khung Free trả cropRect null (không crop), khung tỉ lệ center-crop mặc định
 * đúng, và pan bị clamp không bao giờ vượt biên [0,1] (không để lộ vùng ngoài ảnh).
 */
@RunWith(RobolectricTestRunner::class)
class CropOverlayViewWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun layoutView(view: CropOverlayView, size: Int = 300) {
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        view.measure(spec, spec)
        view.layout(0, 0, size, size)
    }

    @Test
    fun freeRatio_computeCropRect_returnsNull() {
        val view = CropOverlayView(context)
        layoutView(view)
        view.setImageBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

        view.setAspectRatio(null)

        assertThat(view.computeCropRect()).isNull()
    }

    @Test
    fun squareRatio_squareBitmap_defaultCrop_coversFullImage() {
        val view = CropOverlayView(context)
        layoutView(view)
        view.setImageBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

        view.setAspectRatio(1f)

        val rect = view.computeCropRect()!!
        assertThat(rect.left).isWithin(0.01f).of(0f)
        assertThat(rect.top).isWithin(0.01f).of(0f)
        assertThat(rect.right).isWithin(0.01f).of(1f)
        assertThat(rect.bottom).isWithin(0.01f).of(1f)
    }

    @Test
    fun squareRatio_wideBitmap_defaultCrop_centersHorizontally() {
        val view = CropOverlayView(context)
        layoutView(view)
        // Bitmap rộng gấp đôi cao — khung vuông center-crop chỉ giữ 50% chiều ngang, giữa ảnh.
        view.setImageBitmap(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))

        view.setAspectRatio(1f)

        val rect = view.computeCropRect()!!
        assertThat(rect.width()).isWithin(0.02f).of(0.5f)
        assertThat(rect.top).isWithin(0.01f).of(0f)
        assertThat(rect.bottom).isWithin(0.01f).of(1f)
        assertThat(rect.left).isWithin(0.02f).of(0.25f)
        assertThat(rect.right).isWithin(0.02f).of(0.75f)
    }

    @Test
    fun computeCoverScale_rotated45_squareContentSquareFrame_doublesTheNaiveBoundingBoxScale() {
        // Bug thật phát hiện qua smoke test trên device thật: coverScale TỪNG tính theo
        // bounding-box ĐÃ PAD của bitmap xoay (Bitmap.createBitmap trả về, luôn to hơn nội dung
        // thật) → khung crop hở góc trong suốt rõ nhất ở 45°. KHÔNG test qua bitmap xoay thật 45°
        // (đã xác nhận qua debug: shadow Bitmap của Robolectric trả `width=0` sai cho matrix xoay
        // không phải bội số 90° — giới hạn môi trường, không phải bug code) — test trực tiếp hàm
        // thuần [CropOverlayView.computeCoverScale] thay vì đường vòng qua Bitmap thật.
        //
        // Ảnh vuông 100x100 xoay 45°, khung vuông 252x252 (300x300 view, padding 24dp mỗi bên,
        // density=1 mặc định Robolectric): bounding-box đã pad có cạnh 100*sqrt(2)≈141.42, nên
        // công thức SAI cũ (Fw/bmp.width) cho scale=252/141.42≈1.782. Công thức ĐÚNG (dựa trên W,H
        // GỐC trước khi pad): scale=Fw*(cos45+sin45)/100=252*sqrt(2)/100≈3.564 — gấp ĐÚNG GẤP ĐÔI
        // giá trị sai cũ (141.42 = 100*sqrt(2) ở mẫu số cũ, còn tử số mới nhân thêm sqrt(2) so với
        // Fw đơn thuần → 2 lần), để khung chạm sát viền nội dung ảnh thật (hình thoi nội tiếp),
        // không còn góc trống.
        val naiveBuggyScale = 252f / (100f * kotlin.math.sqrt(2f))
        val correctScale = CropOverlayView.computeCoverScale(
            frameWidth = 252f,
            frameHeight = 252f,
            contentWidth = 100,
            contentHeight = 100,
            rotationDegrees = 45f
        )
        assertThat(correctScale).isWithin(0.01f).of(naiveBuggyScale * 2f)
    }

    @Test
    fun computeCoverScale_zeroRotation_matchesPlainCenterCropFormula() {
        val scale = CropOverlayView.computeCoverScale(
            frameWidth = 300f,
            frameHeight = 200f,
            contentWidth = 400,
            contentHeight = 100,
            rotationDegrees = 0f
        )
        assertThat(scale).isWithin(0.001f).of(max(300f / 400f, 200f / 100f))
    }

    @Test
    fun pan_largeDrag_clampsWithinImageBounds_widthUnchanged() {
        val view = CropOverlayView(context)
        layoutView(view)
        view.setImageBitmap(Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888))
        view.setAspectRatio(1f)
        val widthBefore = view.computeCropRect()!!.width()

        val downTime = 0L
        dispatch(view, MotionEvent.ACTION_DOWN, 150f, 150f, downTime)
        // Kéo cực mạnh — vượt xa slack thật, chỉ để chạm biên clamp.
        dispatch(view, MotionEvent.ACTION_MOVE, -9850f, 150f, downTime)
        dispatch(view, MotionEvent.ACTION_UP, -9850f, 150f, downTime)

        val rect = view.computeCropRect()!!
        assertThat(rect.left).isAtLeast(0f)
        assertThat(rect.right).isAtMost(1f)
        assertThat(rect.width()).isWithin(0.02f).of(widthBefore)
        // Đã bị đẩy về sát 1 biên (không còn ở giữa center-crop mặc định 0.25..0.75).
        assertThat(min(rect.left, 1f - rect.right)).isWithin(0.02f).of(0f)
    }

    private fun dispatch(view: CropOverlayView, action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, downTime, action, x, y, 0)
        view.onTouchEvent(event)
        event.recycle()
    }
}
