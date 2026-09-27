package com.mckimquyen.watermark.ui.about

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.export.AuthenticityVerifier
import com.mckimquyen.watermark.export.stego.InvisibleWatermark
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-10 widget test: kết quả tra cứu phải nói rõ NGƯỜI NHẬN đã làm rò rỉ, không chỉ in ID hex.
 */
@RunWith(RobolectricTestRunner::class)
class AboutActivityRecipientLeakRoboTest {

    @Test
    fun `co recipient khop thi hien ten va ma len dau ket qua`() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val recipient = Recipient(id = 1, name = "Khách hàng VIP A", code = "VIP-A")
        val report = AboutViewModel.VerifyReport(
            stamp = AuthenticityVerifier.Result.NoStamp,
            hidden = InvisibleWatermark.Result(ownerId = 123, confidence = 0.96, rounds = 4),
            currentOwner = "Roy Studio",
            leakedRecipient = recipient
        )

        val message = activity.buildVerifyMessage(report)

        assertThat(message).contains(recipient.name)
        assertThat(message).contains(recipient.code)
        // Cảnh báo truy nguồn phải là thông tin đầu tiên, trước chi tiết EXIF/Stego kỹ thuật.
        assertThat(message.startsWith("⚠️")).isTrue()
    }

    @Test
    fun `khong khop recipient thi khong tu bao la ro ri`() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val report = AboutViewModel.VerifyReport(
            stamp = AuthenticityVerifier.Result.NoStamp,
            hidden = InvisibleWatermark.Result(ownerId = 123, confidence = 0.96, rounds = 4),
            currentOwner = "Roy Studio",
            leakedRecipient = null
        )

        val message = activity.buildVerifyMessage(report)

        assertThat(message).doesNotContain("Leak source")
        assertThat(message).doesNotContain("Nguồn rò rỉ")
    }
}
