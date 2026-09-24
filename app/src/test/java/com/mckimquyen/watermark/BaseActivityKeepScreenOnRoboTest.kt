package com.mckimquyen.watermark

import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.feature.vip.VipManagementActivity
import com.mckimquyen.watermark.ui.about.OpenSourceActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * `BaseActivity.onCreate()` phải bật `FLAG_KEEP_SCREEN_ON` cho MỌI activity kế thừa (yêu cầu user:
 * "keep screen on cho mọi screen"), không chỉ `MainActivity` (đã có fix riêng từ trước, xem
 * `MainActivityKeepScreenOnRoboTest`) — verify qua 2 activity KHÁC không liên quan gì tới editor
 * để chứng minh cơ chế chung ở base class hoạt động, không phải trùng hợp 1 màn riêng lẻ đã tự set.
 */
@RunWith(RobolectricTestRunner::class)
class BaseActivityKeepScreenOnRoboTest {

    private fun hasKeepScreenOn(flags: Int): Boolean =
        (flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

    @Test
    fun openSourceActivity_onCreate_setsKeepScreenOnFlag() {
        val controller = Robolectric.buildActivity(OpenSourceActivity::class.java).create().start().resume()
        val activity = controller.get()

        assertThat(hasKeepScreenOn(activity.window.attributes.flags)).isTrue()

        controller.pause().stop().destroy()
    }

    @Test
    fun vipManagementActivity_onCreate_setsKeepScreenOnFlag() {
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java).create().start().resume()
        val activity = controller.get()

        assertThat(hasKeepScreenOn(activity.window.attributes.flags)).isTrue()

        controller.pause().stop().destroy()
    }
}
