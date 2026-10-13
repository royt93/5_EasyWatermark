package com.mckimquyen.watermark.feature.vip

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VipTokenSmokeInstrumentationTest {
    @Test
    fun debugHarnessActivity_isNotExportedAndStatusHoldsNoTokenSizedBlob() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            android.content.ComponentName(context, VipTokenSmokeActivity::class.java),
            0
        )
        assertThat(activityInfo.exported).isFalse()

        ActivityScenario.launch(VipTokenSmokeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val status = activity.findViewById<android.widget.TextView>(com.mckimquyen.watermark.R.id.tvSmokeStatus)
                val mint = activity.findViewById<android.widget.Button>(com.mckimquyen.watermark.R.id.btnMintToken)
                assertThat(status.text.toString()).isNotEmpty()
                // Token/key base64 dài >= MIN_SECRET_LENGTH ký tự liền nhau; status chỉ chứa câu thường.
                assertThat(status.text.toString()).doesNotContainMatch(SECRET_LIKE_PATTERN)
                assertThat(mint.isEnabled).isTrue()
                // Không có Intent extra nào mang key: Activity chỉ đọc file app-private.
                assertThat(activity.intent.extras?.keySet().orEmpty()).isEmpty()
                // Không bấm mint nếu operator đã nạp key: mint sẽ xoá key và có thể arm token trên máy thật.
                assumeFalse(
                    "Có key handoff đang chờ; bỏ qua bước bấm mint để không phá dữ liệu QA.",
                    java.io.File(activity.noBackupFilesDir, KEY_FILE_NAME).exists()
                )
                // Bấm mint khi chưa nạp key: đường này chạy thật qua harness và status mới phải sạch secret.
                mint.performClick()
                assertThat(status.text.toString()).doesNotContainMatch(SECRET_LIKE_PATTERN)
                assertThat(status.text.toString()).contains("no new token was made")
            }
        }
    }

    private companion object {
        const val MIN_SECRET_LENGTH = 40
        const val KEY_FILE_NAME = "vip-smoke-private-key"
        val SECRET_LIKE_PATTERN = "[A-Za-z0-9_-]{$MIN_SECRET_LENGTH,}".toRegex().toPattern()
    }
}
