package com.mckimquyen.watermark.feature.vip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Integration: SharedPreferences thật trên thiết bị, không mock; kiểm ghi mốc cấp đúng một lần cho mỗi chu kỳ. */
@RunWith(AndroidJUnit4::class)
class VipTokenResultListenerIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        VipTokenPendingState.reset()
        VipPrefs(context).clearGrantedAtMs()
    }

    @After
    fun tearDown() {
        VipTokenPendingState.reset()
        VipPrefs(context).clearGrantedAtMs()
    }

    @Test
    fun successWithoutAnyScreen_recordsGrantOnce_andDuplicateDoesNotRewrite() {
        val listener = VipTokenResultListener(context)

        listener.invoke(true)
        val first = VipPrefs(context).lastGrantedAtMs()
        Thread.sleep(CLOCK_ADVANCE_MS)
        listener.invoke(true)

        assertThat(first).isGreaterThan(0L)
        assertThat(VipPrefs(context).lastGrantedAtMs()).isEqualTo(first)
        assertThat(VipTokenPendingState.state.value)
            .isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun failureWithoutPending_isDroppedAndRecordsNothing() {
        VipTokenResultListener(context).invoke(false)

        assertThat(VipPrefs(context).lastGrantedAtMs()).isEqualTo(0L)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
    }

    @Test
    fun newCycleAfterConsumed_recordsFreshGrant() {
        val listener = VipTokenResultListener(context)
        listener.invoke(true)
        val first = VipPrefs(context).lastGrantedAtMs()
        VipTokenPendingState.consumeResolved()
        VipTokenPendingState.begin()
        Thread.sleep(CLOCK_ADVANCE_MS)

        listener.invoke(true)

        assertThat(VipPrefs(context).lastGrantedAtMs()).isGreaterThan(first)
    }

    private companion object {
        const val CLOCK_ADVANCE_MS = 20L
    }
}
