package com.mckimquyen.watermark.feature.vip

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VipTokenResultListenerTest {
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
    fun success_recordsGrantWithoutAnyScreenAndPublishesResolved() {
        VipTokenResultListener(context).invoke(true)

        assertThat(VipPrefs(context).hasUserActivatedVip()).isTrue()
        assertThat(VipPrefs(context).lastGrantedAtMs()).isGreaterThan(0L)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun failure_doesNotRecordGrant() {
        VipTokenPendingState.begin()

        VipTokenResultListener(context).invoke(false)

        assertThat(VipPrefs(context).lastGrantedAtMs()).isEqualTo(0L)
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = false))
    }

    @Test
    fun failureWithoutPendingCycle_isDroppedAndRecordsNothing() {
        VipTokenResultListener(context).invoke(false)

        assertThat(VipPrefs(context).hasUserActivatedVip()).isFalse()
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
    }

    @Test
    fun duplicateSuccessCallback_doesNotRewriteGrantedAt() {
        val listener = VipTokenResultListener(context)
        listener.invoke(true)
        val first = VipPrefs(context).lastGrantedAtMs()
        Thread.sleep(CLOCK_ADVANCE_MS)

        listener.invoke(true)

        assertThat(VipPrefs(context).lastGrantedAtMs()).isEqualTo(first)
    }

    @Test
    fun successCallbackAfterConsumedCycle_doesNotRewriteGrantedAt() {
        val listener = VipTokenResultListener(context)
        listener.invoke(true)
        val first = VipPrefs(context).lastGrantedAtMs()
        VipTokenPendingState.consumeResolved()
        Thread.sleep(CLOCK_ADVANCE_MS)

        listener.invoke(true)

        assertThat(VipPrefs(context).lastGrantedAtMs()).isEqualTo(first)
    }

    @Test
    fun newPendingCycleAfterConsumed_successRecordsNewGrant() {
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
