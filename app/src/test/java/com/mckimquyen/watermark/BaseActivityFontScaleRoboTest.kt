package com.mckimquyen.watermark

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.about.OpenSourceActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * REVIEW-13: `BaseActivity.attachBaseContext()` trước đây ép CỨNG `fontScale = 1.0f` cho MỌI
 * activity, vô hiệu hoá hoàn toàn cài đặt Accessibility > Font size của hệ thống (R5: không được
 * bỏ accessibility). Fix: clamp trần [BaseActivity.MAX_FONT_SCALE] thay vì chặn tuyệt đối — verify
 * cả 2 chiều: scale vượt trần bị giới hạn, scale trong trần được giữ nguyên như user đã chọn.
 *
 * Dùng mutate trực tiếp field `Configuration.fontScale` của application context (không dùng
 * `RuntimeEnvironment.setQualifiers()` — đụng `ShadowDisplayManager` trên bản Robolectric đang
 * dùng, xem ghi chú ở [com.mckimquyen.watermark.ui.dlg.GalleryPluralsAllLocalesRoboTest]).
 */
@RunWith(RobolectricTestRunner::class)
class BaseActivityFontScaleRoboTest {

    private val appConfig = ApplicationProvider.getApplicationContext<android.content.Context>().resources.configuration

    @After
    fun resetFontScale() {
        appConfig.fontScale = 1.0f
    }

    @Test
    fun attachBaseContext_systemFontScaleAboveCap_isClampedToCap() {
        appConfig.fontScale = 2.0f

        val controller = Robolectric.buildActivity(OpenSourceActivity::class.java).create()
        val activity = controller.get()

        // So sánh bằng giá trị trần CHÍNH XÁC (không phải isAtMost) — hardcode cũ (1.0f) cũng
        // vô tình thoả isAtMost(1.3f), không phân biệt được bug cũ với fix mới.
        assertThat(activity.resources.configuration.fontScale).isWithin(0.001f).of(1.3f)

        controller.destroy()
    }

    @Test
    fun attachBaseContext_systemFontScaleWithinCap_isPreserved() {
        appConfig.fontScale = 1.15f

        val controller = Robolectric.buildActivity(OpenSourceActivity::class.java).create()
        val activity = controller.get()

        assertThat(activity.resources.configuration.fontScale).isWithin(0.001f).of(1.15f)

        controller.destroy()
    }
}
