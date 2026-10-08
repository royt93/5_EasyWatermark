package com.mckimquyen.watermark.ui.about

import android.os.Build
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** User EEA/UK/CH phải mở lại được lựa chọn consent quảng cáo (UMP privacy options) từ About. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AboutActivityAdPrivacyRoboTest {

    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.loadBanner(any(), any(), any(), any(), any()) } returns null
        every { AdManager.loadInterstitial(any()) } returns Unit
        every { AdManager.showConsentFormIfAvailable(any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkObject(AdManager)
    }

    @Test
    fun adPrivacyRow_exists_andOpensSdkConsentForm() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val row = activity.findViewById<View>(R.id.rowAdPrivacy)

        assertThat(row).isNotNull()
        assertThat(row.isClickable).isTrue()

        row.performClick()

        verify(exactly = 1) { AdManager.showConsentFormIfAvailable(activity, any()) }
    }

    @Test
    fun aboutActivity_doesNotForwardBannerLifecycleManually_autoManageContract() {
        every { AdManager.bannerResume(any()) } returns Unit
        every { AdManager.bannerPause(any()) } returns Unit
        every { AdManager.bannerDestroy(any()) } returns Unit
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()

        controller.pause().resume().pause().stop().destroy()

        // Doc A.3: autoManageLifecycle=true (mặc định) → KHÔNG tự gọi 3 hàm này.
        verify(exactly = 0) { AdManager.bannerResume(any()) }
        verify(exactly = 0) { AdManager.bannerPause(any()) }
        verify(exactly = 0) { AdManager.bannerDestroy(any()) }
    }
}
