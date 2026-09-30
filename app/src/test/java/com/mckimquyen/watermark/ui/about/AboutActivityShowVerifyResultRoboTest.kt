package com.mckimquyen.watermark.ui.about

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.export.AuthenticityVerifier
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/**
 * P1 review pass 8: `showVerifyResult` chạy từ callback `viewModelScope` (sống ngoài Activity) —
 * nếu Activity đã `finish()` trước khi kết quả verify về (I/O chậm + user back ra ngay), gọi
 * `MaterialAlertDialogBuilder(this).show()` là dùng token cửa sổ đã chết -> `BadTokenException`
 * trên thiết bị thật. Robolectric không mô phỏng đúng exception đó (shadow không check window
 * token), nên assert qua hành vi quan sát được: activity đã finish thì KHÔNG được tạo dialog nào.
 */
@RunWith(RobolectricTestRunner::class)
class AboutActivityShowVerifyResultRoboTest {

    private fun report() = AboutViewModel.VerifyReport(
        stamp = AuthenticityVerifier.Result.NoStamp,
        hidden = null,
        currentOwner = "Roy Studio",
        leakedRecipient = null
    )

    @Test
    fun `activity da finish thi khong show dialog`() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        activity.finish()

        activity.showVerifyResult(report())

        assertThat(ShadowDialog.getLatestDialog()).isNull()
    }

    @Test
    fun `activity con song thi van show dialog binh thuong`() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()

        activity.showVerifyResult(report())

        assertThat(ShadowDialog.getLatestDialog()).isNotNull()
    }
}
