package com.mckimquyen.watermark.ui

import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.SignatureView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * BUG-56: nút Apply không được lưu trùng khi tap đúp, toast "đã áp dụng" phải qua i18n
 * (`R.string.signature_applied`), nút phải bật lại nếu lưu thất bại để thử lại được.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SignatureActivityApplyRoboTest {

    private fun drawStroke(view: SignatureView) {
        val t = SystemClock.uptimeMillis()
        listOf(
            MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 50f, 50f, 0),
            MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, 120f, 90f, 0),
            MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, 160f, 130f, 0)
        ).forEach { view.dispatchTouchEvent(it) }
    }

    @Test
    fun apply_doubleTap_savesOnlyOnce_andShowsLocalizedToast() {
        val activity = Robolectric.buildActivity(SignatureActivity::class.java).setup().get()
        val before = signatureFiles(activity).size
        drawStroke(activity.findViewById(R.id.signatureView))
        val btnApply = activity.findViewById<MaterialButton>(R.id.btnApply)

        btnApply.performClick()
        btnApply.performClick() // tap đúp ngay lập tức, trước khi coroutine lưu kịp chạy xong

        awaitIdle(activity)
        assertThat(signatureFiles(activity).size - before).isEqualTo(1)
        assertThat(btnApply.isEnabled).isFalse()
        assertThat(shadowOf(activity).resultCode).isEqualTo(android.app.Activity.RESULT_OK)
        assertThat(snackbarText(activity)).isEqualTo(activity.getString(R.string.signature_applied))
    }

    @Test
    fun apply_emptyCanvas_keepsButtonEnabled() {
        val activity = Robolectric.buildActivity(SignatureActivity::class.java).setup().get()
        val btnApply = activity.findViewById<MaterialButton>(R.id.btnApply)

        btnApply.performClick()

        assertThat(btnApply.isEnabled).isTrue()
        assertThat(snackbarText(activity)).isEqualTo(activity.getString(R.string.draw_here))
    }

    @Test
    fun colorPalette_noParseColorLiterals_inSource() {
        // R5: màu preset phải là hằng có tên, không phải chuỗi hex rải rác parseColor("#...").
        val src = File("src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/mckimquyen/watermark/ui/SignatureActivity.kt")
        assertThat(Regex("parseColor\\(\"#").containsMatchIn(src.readText())).isFalse()
    }

    /** `Activity.toast()` đã chuyển sang Snackbar M3 — đọc text từ view hierarchy thay vì ShadowToast. */
    private fun snackbarText(activity: SignatureActivity): String? {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        return findSnackbarText(root)
    }

    private fun findSnackbarText(view: android.view.View): String? {
        if (view is com.google.android.material.snackbar.Snackbar.SnackbarLayout) {
            return view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString()
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) findSnackbarText(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun signatureFiles(activity: SignatureActivity): List<File> =
        activity.repo.let { _ -> File(activity.filesDir, "signatures").listFiles()?.toList().orEmpty() }

    private fun awaitIdle(activity: SignatureActivity) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (shadowOf(activity).resultCode == android.app.Activity.RESULT_OK) return
            Thread.sleep(20)
        }
    }
}
