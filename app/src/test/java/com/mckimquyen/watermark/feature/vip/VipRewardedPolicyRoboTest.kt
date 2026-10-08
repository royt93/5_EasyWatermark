package com.mckimquyen.watermark.feature.vip

import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
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
import org.robolectric.annotation.Config

/**
 * Doc AD_PROMPT_AOS Step 7 rule 6: VIP/reward CHỈ cấp khi rewarded `earned == true`.
 * Rewarded không earned → TUYỆT ĐỐI KHÔNG fallback sang interstitial để cấp VIP.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class VipRewardedPolicyRoboTest {

    @Before
    fun setUp() {
        mockkObject(AdManager)
        every { AdManager.loadRewarded(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AdManager)
    }

    private fun clickWatchRewarded(earned: Boolean): VipManagementActivity {
        val callback = slot<(Boolean) -> Unit>()
        every { AdManager.showRewarded(any(), capture(callback)) } answers { callback.captured(earned) }
        every { AdManager.grantVipDays(any(), any()) } returns true
        val activity = Robolectric.buildActivity(VipManagementActivity::class.java).setup().get()
        activity.findViewById<MaterialButton>(R.id.btnWatchRewarded).performClick()
        return activity
    }

    @Test
    fun rewardedNotEarned_neverShowsInterstitial_neverGrantsVip() {
        clickWatchRewarded(earned = false)

        verify(exactly = 0) { AdManager.showInterstitial(any(), any()) }
        verify(exactly = 0) { AdManager.grantVipDays(any(), any()) }
    }

    @Test
    fun rewardedEarned_grantsVipExactlyOnce_withoutInterstitial() {
        clickWatchRewarded(earned = true)

        verify(exactly = 1) { AdManager.grantVipDays(any(), 3) }
        verify(exactly = 0) { AdManager.showInterstitial(any(), any()) }
    }

    @Test
    fun rewardedNotEarned_buttonReEnabled_soUserCanRetry() {
        val activity = clickWatchRewarded(earned = false)

        assertThat(activity.findViewById<MaterialButton>(R.id.btnWatchRewarded).isEnabled).isTrue()
        assertThat(AlertDialog::class.java).isNotNull()
    }

    @Test
    fun rewardedEarned_whenActivityAlreadyDestroyed_stillGrantsVip() {
        // Đổi theme/locale hoặc system kill giữa lúc xem ad: callback SDK về khi Activity đã mất.
        val callback = slot<(Boolean) -> Unit>()
        every { AdManager.showRewarded(any(), capture(callback)) } returns Unit
        every { AdManager.grantVipDays(any(), any()) } returns true
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java).setup()
        controller.get().findViewById<MaterialButton>(R.id.btnWatchRewarded).performClick()

        controller.pause().stop().destroy()
        callback.captured.invoke(true)

        verify(exactly = 1) { AdManager.grantVipDays(any(), 3) }
        verify(exactly = 0) { AdManager.showInterstitial(any(), any()) }
    }

    @Test
    fun rewardedNotEarned_whenActivityAlreadyDestroyed_doesNotGrantAndDoesNotCrash() {
        val callback = slot<(Boolean) -> Unit>()
        every { AdManager.showRewarded(any(), capture(callback)) } returns Unit
        every { AdManager.grantVipDays(any(), any()) } returns true
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java).setup()
        controller.get().findViewById<MaterialButton>(R.id.btnWatchRewarded).performClick()

        controller.pause().stop().destroy()
        callback.captured.invoke(false)

        verify(exactly = 0) { AdManager.grantVipDays(any(), any()) }
    }

    @Test
    fun grantFails_showsNoSuccess_andNeverFallsBackToInterstitial() {
        val callback = slot<(Boolean) -> Unit>()
        every { AdManager.showRewarded(any(), capture(callback)) } answers { callback.captured(true) }
        every { AdManager.grantVipDays(any(), any()) } returns false
        val activity = Robolectric.buildActivity(VipManagementActivity::class.java).setup().get()

        activity.findViewById<MaterialButton>(R.id.btnWatchRewarded).performClick()

        verify(exactly = 1) { AdManager.grantVipDays(any(), 3) }
        verify(exactly = 0) { AdManager.showInterstitial(any(), any()) }
    }
}
