package com.mckimquyen.watermark.feature.vip

import android.os.Build
import android.os.Looper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mckimquyen.watermark.BuildConfig
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class VipRedeemRoutingRoboTest {

    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<VipManagementActivity>>()

    @Before
    fun setUp() {
        controllers.clear()
        VipTokenPendingState.reset()
        shadowOf(Looper.getMainLooper()).idle()
        mockkObject(AdManager)
        every { AdManager.loadRewarded(any()) } returns Unit
        every { AdManager.isVipByKeyActive() } returns false
        every { AdManager.getVipByKeyExpiry() } returns 0L
        every { AdManager.isVipTokenPending } returns false
        every { AdManager.isVipTokenResolutionInFlight } returns false
        every { AdManager.activateVipByToken(any(), any()) } returns false
        every { AdManager.activateVipByKey(any(), any(), any()) } returns false
    }

    @After
    fun tearDown() {
        controllers.forEach { controller ->
            if (controller.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                controller.pause().stop()
            }
            if (controller.get().lifecycle.currentState != androidx.lifecycle.Lifecycle.State.DESTROYED) {
                controller.destroy()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        AdManager.setPendingVipTokenResultListener(null)
        VipTokenPendingState.reset()
        shadowOf(Looper.getMainLooper()).idle()
        unmockkObject(AdManager)
    }

    private fun redeem(input: String): VipManagementActivity {
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java).setup()
        controllers += controller
        val activity = controller.get()
        activity.findViewById<TextInputEditText>(R.id.edtVipKey).setText(input)
        activity.findViewById<MaterialButton>(R.id.btnRedeemKey).performClick()
        return activity
    }

    @Test
    fun nonLegacyInput_callsEcdsaTokenApi_notLegacyKeyApi() {
        val token = "eyJhbGciOiJFUzI1NiJ9.payload.signature"

        redeem(token)

        verify(exactly = 1) { AdManager.activateVipByToken(any(), token) }
        verify(exactly = 0) { AdManager.activateVipByKey(any(), any(), any()) }
    }

    @Test
    fun configuredLegacy30DayCode_callsLegacyKeyApi_notTokenApi() {
        val code = BuildConfig.VIP_LEGACY_30D_CODE

        redeem(code)

        verify(exactly = 1) { AdManager.activateVipByKey(any(), code, 30) }
        verify(exactly = 0) { AdManager.activateVipByToken(any(), any()) }
    }
}
