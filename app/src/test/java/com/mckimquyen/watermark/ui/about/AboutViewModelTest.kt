package com.mckimquyen.watermark.ui.about

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.export.AuthenticityVerifier
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * IDEA-03/IDEA-02/IDEA-10: [AboutViewModel.VerifyReport] data class — xác thực con dấu ảnh
 * song song với đọc watermark ẩn pixel, rồi tìm người nhận rò rỉ nếu có.
 * Test VerifyReport structure và data class contract (equals/hashCode).
 */
@HiltAndroidTest
@Config(application = dagger.hilt.android.testing.HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class AboutViewModelTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun verifyReport_defaultValues_matchDocumentation() {
        val stamp = AuthenticityVerifier.Result.NoStamp
        val report = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = null,
            currentOwner = "",
            leakedRecipient = null
        )

        assertThat(report.stamp).isEqualTo(stamp)
        assertThat(report.hidden).isNull()
        assertThat(report.currentOwner).isEmpty()
        assertThat(report.leakedRecipient).isNull()
    }

    @Test
    fun verifyReport_dataClass_equals_comparesBothContent() {
        val stamp = AuthenticityVerifier.Result.NoStamp
        val hidden = null
        val owner = "TestOwner"

        val report1 = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = hidden,
            currentOwner = owner,
            leakedRecipient = null
        )
        val report2 = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = hidden,
            currentOwner = owner,
            leakedRecipient = null
        )

        assertThat(report1).isEqualTo(report2)
        assertThat(report1.hashCode()).isEqualTo(report2.hashCode())
    }

    @Test
    fun verifyReport_differentContent_notEquals() {
        val stamp = AuthenticityVerifier.Result.NoStamp
        val report1 = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = null,
            currentOwner = "Owner1",
            leakedRecipient = null
        )
        val report2 = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = null,
            currentOwner = "Owner2",
            leakedRecipient = null
        )

        assertThat(report1).isNotEqualTo(report2)
    }

    @Test
    fun verifyReport_withRecipient_storesCorrectly() {
        val stamp = AuthenticityVerifier.Result.NoStamp
        val recipient = Recipient(
            id = 1,
            name = "John",
            code = "abc123"
        )

        val report = AboutViewModel.VerifyReport(
            stamp = stamp,
            hidden = null,
            currentOwner = "Current",
            leakedRecipient = recipient
        )

        assertThat(report.leakedRecipient).isEqualTo(recipient)
        assertThat(report.leakedRecipient?.name).isEqualTo("John")
    }
}
