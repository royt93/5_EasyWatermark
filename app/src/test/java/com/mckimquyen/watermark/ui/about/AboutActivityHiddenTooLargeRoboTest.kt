package com.mckimquyen.watermark.ui.about

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.export.AuthenticityVerifier
import com.mckimquyen.watermark.export.stego.InvisibleWatermark
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** BUG-85: ảnh quá lớn để đọc lớp ẩn phải báo riêng, KHÔNG được báo như "không có watermark". */
@RunWith(RobolectricTestRunner::class)
class AboutActivityHiddenTooLargeRoboTest {

    private fun report(hidden: InvisibleWatermark.Result?, tooLarge: Boolean) = AboutViewModel.VerifyReport(
        stamp = AuthenticityVerifier.Result.NoStamp,
        hidden = hidden,
        currentOwner = "Roy Studio",
        leakedRecipient = null,
        hiddenTooLarge = tooLarge
    )

    @Test
    fun tooLarge_showsDedicatedMessage_notTheAbsentOne() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()

        val message = activity.buildVerifyMessage(report(hidden = null, tooLarge = true))

        assertThat(message).contains(activity.getString(R.string.invisible_watermark_too_large))
        assertThat(message).doesNotContain(activity.getString(R.string.invisible_watermark_absent))
    }

    @Test
    fun notTooLarge_withoutWatermark_stillSaysAbsent() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()

        val message = activity.buildVerifyMessage(report(hidden = null, tooLarge = false))

        assertThat(message).contains(activity.getString(R.string.invisible_watermark_absent))
        assertThat(message).doesNotContain(activity.getString(R.string.invisible_watermark_too_large))
    }

    @Test
    fun foundWatermark_neverShowsTooLargeMessage() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val found = InvisibleWatermark.Result(ownerId = 123, confidence = 0.96, rounds = 4)

        val message = activity.buildVerifyMessage(report(hidden = found, tooLarge = false))

        assertThat(message).doesNotContain(activity.getString(R.string.invisible_watermark_too_large))
    }
}
