package com.mckimquyen.watermark.ui

import android.app.Application
import android.net.ConnectivityManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Doc AD_PROMPT_AOS Bước 4 + example: Splash gọi `requestConsentInfoUpdate` VÔ ĐIỀU KIỆN.
 * Regression: trước đây rẽ nhánh `hasNetwork()` → offline lần đầu thì UMP không bao giờ chạy
 * (SDK chỉ retry khi UMP đã báo lỗi fetch), user EEA không thấy form consent và mất ad cả phiên.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SplashConsentFlowRoboTest {

    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.requestConsentInfoUpdate(any(), any(), any()) } answers {
            thirdArg<(Boolean) -> Unit>().invoke(true)
        }
    }

    @After
    fun tearDown() {
        unmockkObject(AdManager)
    }

    private fun setNetworkAvailable(available: Boolean) {
        val cm = ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(cm)
        if (available) {
            shadow.setActiveNetworkInfo(cm.activeNetworkInfo)
        } else {
            shadow.setActiveNetworkInfo(null)
            shadow.setDefaultNetworkActive(false)
        }
    }

    @Test
    fun offline_stillRequestsConsent() {
        setNetworkAvailable(false)

        Robolectric.buildActivity(SplashActivity::class.java).setup()

        verify(atLeast = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }
    }

    @Test
    fun online_requestsConsentExactlyOnce() {
        setNetworkAvailable(true)

        Robolectric.buildActivity(SplashActivity::class.java).setup()

        verify(exactly = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }
    }

    @Test
    fun consentCallbackIsForwarded_notSwallowed() {
        val captured = slot<(Boolean) -> Unit>()
        every { AdManager.requestConsentInfoUpdate(any(), any(), capture(captured)) } returns Unit

        Robolectric.buildActivity(SplashActivity::class.java).setup()

        // Callback phải được truyền vào SDK (nếu null/bỏ qua thì Splash treo vĩnh viễn).
        verify(exactly = 1) { AdManager.requestConsentInfoUpdate(any(), any(), any()) }
        assert(captured.isCaptured)
    }
}
