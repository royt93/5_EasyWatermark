package com.mckimquyen.watermark.utils.ktx

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * ContextExtension.toast() da chuyen tu Toast sang Snackbar M3 (khong theme, khong dung chuan
 * Material You). Test nay khoa lai hanh vi sau khi doi: hien dung text/duration, bo qua khi msg
 * rong, khong crash khi Fragment view da bi huy (an toan hon Toast cu ve mat lifecycle).
 */
@RunWith(RobolectricTestRunner::class)
class ToastExtensionWidgetTest {

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        const val WAIT_STEP_MS = 20L
        const val SNACKBAR_DRAIN_MS = 10_000L
    }

    class TestActivity : FragmentActivity()

    class TestViewFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View = FrameLayout(requireContext())
    }

    private fun snackbarTextIn(activity: FragmentActivity): String? {
        val tv = activity.window.decorView
            .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        return tv?.text?.toString()
    }

    private fun newThemedActivity(): FragmentActivity =
        Robolectric.buildActivity(TestActivity::class.java).setup().get().apply {
            setTheme(R.style.Theme_MyApp)
        }

    /** BUG-75: Snackbar attach/animate qua Main Looper — full suite tải cao có thể chưa attach khi assert tức thời. */
    private fun idleUntilSnackbar(activity: FragmentActivity, expected: String, timeoutMs: Long = WAIT_TIMEOUT_MS) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (snackbarTextIn(activity) != expected && System.currentTimeMillis() < deadline) {
            // BUG-75: `idle()` trần chạy cả task TƯƠNG LAI (bao gồm auto-dismiss Snackbar) → có thể show
            // rồi dismiss trước khi assertion nhìn thấy. Chỉ tiến clock 20ms mỗi lượt.
            shadowOf(Looper.getMainLooper()).idleFor(WAIT_STEP_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * SnackbarManager là singleton toàn process: Snackbar LENGTH_SHORT của test trước còn hiện làm Snackbar
     * test sau bị xếp hàng. Dọn hàng đợi giữa mỗi test; nếu không, chạy cả suite chập chờn còn chạy riêng pass.
     */
    @After
    fun drainSnackbarQueue() {
        shadowOf(Looper.getMainLooper()).idleFor(SNACKBAR_DRAIN_MS, TimeUnit.MILLISECONDS)
    }

    @Test
    fun activityToast_showsSnackbarWithMessage() {
        val activity = newThemedActivity()

        activity.toast("hello")
        idleUntilSnackbar(activity, "hello")

        assertThat(snackbarTextIn(activity)).isEqualTo("hello")
    }

    @Test
    fun activityToast_blankMessage_showsNothing() {
        val activity = newThemedActivity()

        activity.toast("   ")
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(snackbarTextIn(activity)).isNull()
    }

    @Test
    fun fragmentToast_showsSnackbarAnchoredOnFragmentView() {
        val activity = newThemedActivity()
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = TestViewFragment()
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment).commitNow()
        shadowOf(Looper.getMainLooper()).idle()

        fragment.toast("fragment msg")
        idleUntilSnackbar(activity, "fragment msg")

        assertThat(snackbarTextIn(activity)).isEqualTo("fragment msg")
    }

    @Test
    fun fragmentToast_detachedFragment_doesNotCrashAndShowsNothing() {
        // Fragment chua duoc add vao FragmentManager -> view == null. Truoc day dung Context.toast
        // se can requireContext() (co the crash); Fragment.toast moi chi no-op an toan.
        val fragment = TestViewFragment()
        fragment.toast("should be silently ignored")
    }
}
