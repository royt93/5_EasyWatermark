package com.mckimquyen.watermark.feature.vip

import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.MyApplication
import com.mckimquyen.watermark.R
import com.roy.sdkadbmob.AdManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class VipTokenPendingActivityRoboTest {
    private val listener = slot<(Boolean) -> Unit>()

    @Before
    fun setUp() {
        activeControllers.clear()
        shadowOf(Looper.getMainLooper()).idle()
        VipTokenPendingState.reset()
        mockkObject(AdManager)
        every { AdManager.loadRewarded(any()) } returns Unit
        every { AdManager.isVipByKeyActive() } returns false
        every { AdManager.getVipByKeyExpiry() } returns 0L
        every { AdManager.isVipTokenPending } returns false
        every { AdManager.isVipTokenResolutionInFlight } returns false
    }

    @After
    fun tearDown() {
        activeControllers.forEach { controller ->
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
        unmockkObject(AdManager)
    }

    private val activeControllers = mutableListOf<ActivityController<VipManagementActivity>>()

    @Test
    fun applicationRegistersPendingListenerBeforeInitialize() {
        every { AdManager.setPendingVipTokenResultListener(capture(listener)) } returns Unit
        every { AdManager.initialize(any(), any()) } returns Unit

        ApplicationProvider.getApplicationContext<MyApplication>().setupAdmobForTest()

        verifyOrder {
            AdManager.setPendingVipTokenResultListener(any())
            AdManager.initialize(any(), any())
        }
        verify(exactly = 1) { AdManager.setPendingVipTokenResultListener(any()) }
        assertThat(listener.isCaptured).isTrue()
        listener.captured.invoke(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
        // Listener đăng ký thật phải ghi mốc cấp VIP ngay lúc resolve, không đợi màn VIP mở.
        assertThat(VipPrefs(ApplicationProvider.getApplicationContext()).lastGrantedAtMs()).isGreaterThan(0L)
    }

    @Test
    fun resume_refreshesVipStateWhenVipIsInactive() {
        var expiryReads = 0
        every { AdManager.getVipByKeyExpiry() } answers { expiryReads++; 0L }
        val controller = launchController()
        val initialReads = expiryReads

        controller.pause().resume()

        assertThat(expiryReads).isGreaterThan(initialReads)
        assertThat(controller.get().findViewById<android.widget.TextView>(R.id.tvVipStatus).text)
            .isEqualTo(controller.get().getString(R.string.vip_status_free))
    }

    @Test
    fun resume_whenSdkHasPendingToken_restoresPendingUiAndLock() {
        var sdkPending = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        val controller = launchController()
        sdkPending = true

        controller.pause().resume()

        val activity = controller.get()
        assertThat(VipTokenPendingState.state.value)
            .isInstanceOf(VipTokenPendingState.State.Pending::class.java)
        assertThat(activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).isEnabled)
            .isTrue()
        assertThat(activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus).text.toString())
            .isEqualTo(activity.getString(R.string.vip_token_pending))
    }

    @Test
    fun isVipTokenResolutionInFlight_aloneDoesNotCreatePhantomPendingState() {
        every { AdManager.isVipTokenResolutionInFlight } returns true
        val controller = launchController()

        controller.pause().resume()

        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
    }

    @Test
    fun tokenApiFalseWithSdkPending_showsPendingAndLocksRedeem() {
        var sdkPending = false
        var sdkResolving = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        every { AdManager.isVipTokenResolutionInFlight } answers { sdkResolving }
        every { AdManager.activateVipByToken(any(), any()) } answers {
            sdkPending = true
            sdkResolving = true
            false
        }
        val activity = launchActivity()

        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).setText("token")
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey).performClick()

        shadowOf(Looper.getMainLooper()).idle()
        assertThat(VipTokenPendingState.state.value)
            .isInstanceOf(VipTokenPendingState.State.Pending::class.java)
        assertThat((VipTokenPendingState.state.value as VipTokenPendingState.State.Pending).timedOut).isFalse()
        assertThat(activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus).text.toString())
            .isEqualTo(activity.getString(R.string.vip_token_pending))
        assertThat(activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).isEnabled)
            .isTrue()
        verify(exactly = 1) { AdManager.activateVipByToken(any(), "token") }

        shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
        assertThat((VipTokenPendingState.state.value as VipTokenPendingState.State.Pending).timedOut).isTrue()
        assertThat(activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus).text.toString())
            .isEqualTo(activity.getString(R.string.vip_token_pending_timeout))
    }

    @Test
    fun redeemWhileSdkResolutionInFlight_doesNotSubmitSecondToken() {
        var sdkPending = false
        var sdkResolving = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        every { AdManager.isVipTokenResolutionInFlight } answers { sdkResolving }
        every { AdManager.activateVipByToken(any(), any()) } answers {
            sdkPending = true
            sdkResolving = true
            false
        }
        val activity = launchActivity()
        val input = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey)
        val redeem = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey)
        input.setText("first-token")
        redeem.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        sdkPending = false
        VipTokenPendingState.resolve(activated = true)
        shadowOf(Looper.getMainLooper()).idle()
        input.setText("second-token")
        redeem.performClick()

        verify(exactly = 1) { AdManager.activateVipByToken(any(), "first-token") }
        verify(exactly = 0) { AdManager.activateVipByToken(any(), "second-token") }
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
    }

    @Test
    fun tokenApiFalseWithoutSdkPending_showsInvalidResult() {
        every { AdManager.activateVipByToken(any(), any()) } returns false
        val activity = launchActivity()

        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).setText("token")
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey).performClick()

        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
        verify(exactly = 1) { AdManager.activateVipByToken(any(), "token") }
    }

    @Test
    fun failedCallbackWhileActivityFinishing_doesNotShowDialogOrMarkVip() {
        val controller = launchController()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().finish()

        VipTokenPendingState.resolve(activated = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(controller.get().isFinishing).isTrue()
        assertThat(VipPrefs(controller.get()).hasUserActivatedVip()).isFalse()
        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNull()
    }

    @Test
    fun failedCallbackWhileActivityFinishing_keepsResultSoReopenedScreenCanShowIt() {
        val first = launchController()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        first.get().finish()
        VipTokenPendingState.resolve(activated = false)
        shadowOf(Looper.getMainLooper()).idle()
        // Kết quả chưa bị tiêu thụ vì Activity đang đóng.
        assertThat(VipTokenPendingState.state.value)
            .isEqualTo(VipTokenPendingState.State.Resolved(activated = false))

        launchActivity()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNotNull()
    }

    @Test
    fun successfulCallbackWhileActivityFinishing_listenerPersistsVipAndKeepsResultForReopen() {
        val controller = launchController()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        controller.get().finish()

        VipTokenResultListener(controller.get()).invoke(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(controller.get().isFinishing).isTrue()
        assertThat(VipPrefs(controller.get()).hasUserActivatedVip()).isTrue()
        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNull()
        assertThat(VipTokenPendingState.state.value)
            .isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun callbackAfterTimeout_resolvesPendingResultAndUnlocksRedeem() {
        val activity = launchActivity()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS)
        assertThat((VipTokenPendingState.state.value as VipTokenPendingState.State.Pending).timedOut).isTrue()

        VipTokenPendingState.resolve(activated = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
        assertThat(activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).isEnabled)
            .isTrue()
    }

    @Test
    fun recreateDuringPending_keepsTimeoutDeadline() {
        val controller = launchController()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.SECONDS)
        controller.recreate()

        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.SECONDS)

        assertThat((VipTokenPendingState.state.value as VipTokenPendingState.State.Pending).timedOut).isTrue()
    }

    @Test
    fun pendingSuccessCallback_refreshesVipAndShowsSuccessOnce() {
        var sdkPending = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        every { AdManager.activateVipByToken(any(), any()) } answers {
            sdkPending = true
            false
        }
        val controller = launchController()
        val activity = controller.get()
        val input = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey)
        input.setText("token")
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        VipTokenPendingState.resolve(activated = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus).visibility)
            .isEqualTo(android.view.View.GONE)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNotNull()
    }

    @Test
    fun duringTokenPending_legacyCodeStillRedeems_butSecondTokenIsBlocked() {
        var sdkPending = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        every { AdManager.activateVipByToken(any(), any()) } answers { sdkPending = true; false }
        every { AdManager.activateVipByKey(any(), any(), any()) } returns true
        val activity = launchActivity()
        val input = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey)
        val redeem = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey)
        input.setText("first.token")
        redeem.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(VipTokenPendingState.state.value).isInstanceOf(VipTokenPendingState.State.Pending::class.java)

        input.setText("second.token")
        redeem.performClick()
        verify(exactly = 0) { AdManager.activateVipByToken(any(), "second.token") }
        // Bị chặn thì phải có phản hồi, không để người dùng bấm mà im lặng.
        shadowOf(Looper.getMainLooper()).idle()
        val snackbarText = activity.findViewById<android.view.View>(com.google.android.material.R.id.snackbar_text)
        assertThat((snackbarText as android.widget.TextView).text.toString())
            .isEqualTo(activity.getString(R.string.vip_token_pending))

        val code = com.mckimquyen.watermark.BuildConfig.VIP_LEGACY_3D_CODE
        input.setText(code)
        redeem.performClick()
        verify(exactly = 1) { AdManager.activateVipByKey(any(), code, 3) }
    }

    @Test
    fun resume_afterConsumedCycle_whenSdkSlotHoldsAnotherToken_showsPendingAgain() {
        // SDK: isVipTokenPending == (pendingActivateToken != null). Slot còn token => SDK sẽ retry và phát
        // callback sau, nên UI phải quay lại Pending dù chu kỳ trước đã Consumed.
        var sdkPending = false
        every { AdManager.isVipTokenPending } answers { sdkPending }
        val controller = launchController()
        VipTokenPendingState.begin()
        VipTokenPendingState.resolve(activated = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
        sdkPending = true

        controller.pause().resume()

        assertThat(VipTokenPendingState.state.value)
            .isInstanceOf(VipTokenPendingState.State.Pending::class.java)
    }

    @Test
    fun reopenedScreen_afterBackgroundSuccess_showsDialogOnceWithoutRewritingGrantedAt() {
        val first = launchController()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        first.get().finish()
        VipTokenResultListener(first.get()).invoke(true)
        shadowOf(Looper.getMainLooper()).idle()
        val grantedAtFirst = VipPrefs(first.get()).lastGrantedAtMs()
        assertThat(grantedAtFirst).isGreaterThan(0L)
        java.lang.Thread.sleep(CLOCK_ADVANCE_MS)

        val second = launchActivity()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(VipPrefs(second).lastGrantedAtMs()).isEqualTo(grantedAtFirst)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNotNull()
    }

    @Test
    fun redeemButton_followsInputText_whetherOrNotTokenIsPending() {
        val activity = launchActivity()
        val input = activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey)
        val redeem = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey)
        assertThat(redeem.isEnabled).isFalse()

        input.setText("abc")
        assertThat(redeem.isEnabled).isTrue()

        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(redeem.isEnabled).isTrue()

        input.setText("   ")
        assertThat(redeem.isEnabled).isFalse()
    }

    @Test
    fun pendingStatusText_isHiddenWhenIdleAndShownWithAccessibilityDescriptionWhenPending() {
        val activity = launchActivity()
        val status = activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus)
        assertThat(status.visibility).isEqualTo(android.view.View.GONE)

        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(status.visibility).isEqualTo(android.view.View.VISIBLE)
        assertThat(status.contentDescription.toString()).isEqualTo(status.text.toString())
        assertThat(status.text.toString()).isEqualTo(activity.getString(R.string.vip_token_pending))
    }

    @Test
    fun failedResolveDuringPending_showsFailureDialogAndLeavesNoPendingText() {
        val activity = launchActivity()
        VipTokenPendingState.begin()
        shadowOf(Looper.getMainLooper()).idle()

        VipTokenPendingState.resolve(activated = false)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(org.robolectric.shadows.ShadowDialog.getLatestDialog()).isNotNull()
        assertThat(activity.findViewById<android.widget.TextView>(R.id.tvVipPendingStatus).visibility)
            .isEqualTo(android.view.View.GONE)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
    }

    @Test
    fun legacyCode_stillUsesLegacyKeyApi() {
        val activity = launchActivity()
        val code = com.mckimquyen.watermark.BuildConfig.VIP_LEGACY_30D_CODE

        activity.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtVipKey).setText(code)
        activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRedeemKey).performClick()

        verify(exactly = 1) { AdManager.activateVipByKey(any(), code, 30) }
        verify(exactly = 0) { AdManager.activateVipByToken(any(), any()) }
    }

    private fun launchController(): ActivityController<VipManagementActivity> {
        val controller = Robolectric.buildActivity(VipManagementActivity::class.java).setup()
        activeControllers += controller
        return controller
    }

    private fun launchActivity(): VipManagementActivity = launchController().get()
}

private const val CLOCK_ADVANCE_MS = 20L

private fun MyApplication.setupAdmobForTest() {
    val method = javaClass.getDeclaredMethod("setupAdmob")
    method.isAccessible = true
    method.invoke(this)
}
