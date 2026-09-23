package com.mckimquyen.watermark.ui

import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Bug fix: TECNO HiOS (và OEM tương tự) tự tắt màn hình theo Screen timeout hệ thống dù editor
 * đang mở — app không giữ màn hình sáng ở đâu ngoài lúc export (SaveImageBSDialogFragment). Thêm
 * FLAG_KEEP_SCREEN_ON cho toàn bộ MainActivity để màn hình luôn sáng khi đang dùng editor.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityKeepScreenOnRoboTest {

    @Test
    fun onCreate_setsFlagKeepScreenOn_forEditorWindow() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()

        val flags = activity.window.attributes.flags
        assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isEqualTo(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
}
