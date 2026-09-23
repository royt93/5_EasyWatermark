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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ContextExtension.toast() da chuyen tu Toast sang Snackbar M3 (khong theme, khong dung chuan
 * Material You). Test nay khoa lai hanh vi sau khi doi: hien dung text/duration, bo qua khi msg
 * rong, khong crash khi Fragment view da bi huy (an toan hon Toast cu ve mat lifecycle).
 */
@RunWith(RobolectricTestRunner::class)
class ToastExtensionWidgetTest {

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

    @Test
    fun activityToast_showsSnackbarWithMessage() {
        val activity = newThemedActivity()

        activity.toast("hello")
        shadowOf(Looper.getMainLooper()).idle()

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
        shadowOf(Looper.getMainLooper()).idle()

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
