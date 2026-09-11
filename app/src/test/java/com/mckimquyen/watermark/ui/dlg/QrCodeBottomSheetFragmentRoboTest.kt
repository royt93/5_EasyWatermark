package com.mckimquyen.watermark.ui.dlg

import android.os.Looper
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * BUG-13: gõ vào ô nội dung QR không còn sinh bitmap đồng bộ trên Main thread ngay mỗi ký tự —
 * `refreshPreview()` phải debounce 250ms (`QR_REFRESH_DEBOUNCE_MS`) và huỷ job cũ khi gõ tiếp
 * (không tích luỹ nhiều lần generate cho cùng 1 chuỗi gõ liên tục).
 *
 * Dùng `shadowOf(Looper).idleFor(duration)` (tôn trọng mốc thời gian đã lên lịch của
 * `Handler.postDelayed`/coroutine `delay`) chứ KHÔNG dùng `idle()` trần — `idle()` không tham số
 * chạy hết mọi task đang chờ kể cả task lên lịch ở tương lai (đã verify thực nghiệm: gọi `idle()`
 * ngay sau `setText` khiến `refreshPreview` chạy ngay lập tức, che mất hành vi debounce cần test).
 *
 * `QrCodeBottomSheetFragment` không chạm `shareViewModel` trong `onViewCreated`/`refreshPreview`
 * (chỉ dùng ở `btnUse` click, ngoài phạm vi test này) nên add thẳng vào `FragmentActivity` thường
 * với `setShowsDialog(false)` (bỏ qua `onCreateDialog`/BottomSheetDialog thật) — cùng kỹ thuật
 * `GalleryFragmentLifecycleRoboTest` dùng cho `BaseBindBSDFragment`.
 */
@RunWith(RobolectricTestRunner::class)
class QrCodeBottomSheetFragmentRoboTest {

    private fun launchFragment(): QrCodeBottomSheetFragment {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = QrCodeBottomSheetFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, QrCodeBottomSheetFragment.TAG)
            .commit()
        // An toàn: tại đây chưa có coroutine delay nào được lên lịch (etContent còn rỗng, chưa
        // gõ gì) nên idle() trần không "ăn nhầm" task debounce nào.
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun previewDrawableIsSet(fragment: QrCodeBottomSheetFragment): Boolean =
        fragment.binding.ivPreview.drawable != null

    /**
     * Sau khi debounce hết hạn, `generate` thật chạy trên `Dispatchers.Default` thật (một thread
     * pool thật, không phải virtual time của Robolectric) rồi mới post kết quả trở lại Main —
     * poll ngắn thay vì assert ngay để không bị race với thread nền đó (cùng pattern
     * `MainViewModelCompressImgRoboTest`).
     */
    private fun awaitPreviewGenerated(fragment: QrCodeBottomSheetFragment) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!previewDrawableIsSet(fragment) && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun typing_doesNotGenerateImmediately_generatesAfterDebounceDelay() {
        val fragment = launchFragment()
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        fragment.binding.etContent.setText("hello")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS) // < 250ms debounce
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS) // tổng > 250ms
        awaitPreviewGenerated(fragment)
        assertThat(previewDrawableIsSet(fragment)).isTrue()
    }

    @Test
    fun rapidRetyping_cancelsStaleJob_onlyFinalContentGeneratesAfterItsOwnDelay() {
        val fragment = launchFragment()

        fragment.binding.etContent.setText("a")
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS) // < 250ms

        fragment.binding.etContent.setText("ab") // huỷ job của "a", đặt lại debounce từ đầu
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        // Tổng thời gian từ lần gõ đầu tiên là 300ms (> 250ms) nhưng job của "a" đã bị huỷ khi gõ
        // "ab" ở mốc 100ms, nên job mới ("ab") mới trôi qua 200ms (< 250ms) -> vẫn chưa generate.
        assertThat(previewDrawableIsSet(fragment)).isFalse()

        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS) // đủ 300ms cho "ab"
        awaitPreviewGenerated(fragment)
        assertThat(previewDrawableIsSet(fragment)).isTrue()
    }
}
