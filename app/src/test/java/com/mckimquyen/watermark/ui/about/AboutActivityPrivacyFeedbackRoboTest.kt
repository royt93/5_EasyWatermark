package com.mckimquyen.watermark.ui.about

import android.os.Build
import android.os.Looper
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class AboutActivityPrivacyFeedbackRoboTest {

    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.loadBanner(any(), any(), any(), any(), any()) } returns null
        every { AdManager.loadInterstitial(any()) } returns Unit
        every { AdManager.showConsentFormIfAvailable(any(), any()) } returns false
    }

    @After
    fun tearDown() = unmockkObject(AdManager)

    @Test
    fun adPrivacyRow_whenSdkHasNoForm_showsFeedbackInsteadOfSilentNoOp() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        val activity = controller.get()
        activity.findViewById<View>(R.id.rowAdPrivacy).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val shadowRoot = activity.findViewById<View>(com.google.android.material.R.id.snackbar_text)
        assertThat(shadowRoot).isNotNull()
        assertThat((shadowRoot as android.widget.TextView).text.toString())
            .isEqualTo(activity.getString(R.string.ad_privacy_options_unavailable))
    }
}
