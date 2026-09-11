package com.mckimquyen.watermark.ui

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * BUG-08: `postDelayed` hiện interstitial (`MainActivity.saveResult` observer, job finish) phải
 * lưu tham chiếu `Runnable` và `removeCallbacks` trong `onDestroy()` — nếu không, callback vẫn
 * chạy sau khi Activity đã destroy (rủi ro `WindowManager$BadTokenException`/leak Activity).
 *
 * Trước đây báo cáo ghi "không có test tự động" vì `MainActivity` là `@AndroidEntryPoint` và
 * project không có Hilt test harness (`hilt-android-testing`). Re-thử nghiêm túc: hoá ra
 * `Robolectric.buildActivity(MainActivity::class.java)` chạy được thật — `MyApplication` (Hilt)
 * khởi tạo bình thường dưới Robolectric (không cần `HiltTestApplication`/harness riêng), vì mọi
 * module DI ở đây dùng implementation thật (DataStore/Room), không có dependency nào chỉ tồn tại
 * trên thiết bị thật. `AdManager.earlyInit()`/`setupAdmob()` trong `MyApplication.onCreate()`
 * cũng không crash dưới Robolectric SDK 34.
 *
 * Chủ động không gọi `.resume()`/`.visible()` (crash/treo vì lý do KHÔNG liên quan BUG-08— xem
 * comment trong test) nên `launchView` chưa attach window thật (`getHandler()` == null) —
 * `View.postDelayed`/`removeCallbacks` khi chưa attach thao tác trên hàng đợi nội bộ
 * `View.mRunQueue` (`HandlerActionQueue`, được thực thi khi attach). Đọc trực tiếp hàng đợi này
 * qua reflection (`getRunQueue()` + `size()`/`getRunnable()`, đều là API thật của Android, không
 * phải mock) để xác nhận chính xác runnable đã lưu còn được lên lịch hay đã bị gỡ — vẫn là đúng
 * code path `postDelayed`/`removeCallbacks` thật của `MainActivity`, không phải bản sao logic.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityInterstitialRoboTest {

    // `viewModel` là `by viewModels()` (Kotlin property delegate) — field thật là
    // `viewModel$delegate` (kotlin.Lazy), không phải `viewModel`. Gọi thẳng getter private do
    // Kotlin sinh ra (`getViewModel()`) thay vì đoán tên field.
    private fun MainActivity.viewModelField(): MainViewModel =
        MainActivity::class.java.getDeclaredMethod("getViewModel").apply { isAccessible = true }
            .invoke(this) as MainViewModel

    private fun MainActivity.launchViewField(): android.view.View =
        MainActivity::class.java.getDeclaredField("launchView").apply { isAccessible = true }
            .get(this) as android.view.View

    private fun MainActivity.showInterstitialRunnableField(): Runnable? =
        MainActivity::class.java.getDeclaredField("showInterstitialRunnable").apply { isAccessible = true }
            .get(this) as Runnable?

    /** View chưa attach window → postDelayed/removeCallbacks thao tác trên `View.mRunQueue`. */
    private fun android.view.View.pendingRunnables(): List<Runnable> {
        val getRunQueue = android.view.View::class.java.getDeclaredMethod("getRunQueue")
            .apply { isAccessible = true }
        val runQueue = getRunQueue.invoke(this)
        val size = runQueue.javaClass.getMethod("size").invoke(runQueue) as Int
        val getRunnable = runQueue.javaClass.getMethod("getRunnable", Int::class.javaPrimitiveType)
        return (0 until size).map { getRunnable.invoke(runQueue, it) as Runnable }
    }

    @Test
    fun `job finish schedules runnable, onDestroy removes it before it fires`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        // Không gọi resume()/visible(): BaseActivity.onResume() → enableAdaptiveRefreshRate() gọi
        // Context.getDisplay() (crash dưới Robolectric ở đây, không liên quan BUG-08), và
        // visible() từng treo test (nghi animation/ad-init loop chờ vô hạn) — create()+start() đã
        // đủ để attach window/Handler cho launchView, đúng cái ta cần kiểm tra
        // (postDelayed/removeCallbacks) mà không kéo theo rủi ro không liên quan.
        val activity = controller.create().start().get()
        val viewModel = activity.viewModelField()
        val launchView = activity.launchViewField()

        // Mô phỏng job save ảnh xong (đúng code path thật, qua LiveData thật — không phải copy logic).
        viewModel.saveResult.value = Result.success(code = MainViewModel.TYPE_JOB_FINISH, data = null)

        val runnable = activity.showInterstitialRunnableField()
        assertThat(runnable).isNotNull()
        assertThat(launchView.pendingRunnables()).contains(runnable)

        // Thoát Activity ngay lập tức (trong cửa sổ 800ms, trước khi runnable tự chạy).
        controller.destroy()

        assertThat(activity.showInterstitialRunnableField()).isNull()
        assertThat(launchView.pendingRunnables()).doesNotContain(runnable)
    }
}
