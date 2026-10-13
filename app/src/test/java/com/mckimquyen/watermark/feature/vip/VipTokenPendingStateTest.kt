package com.mckimquyen.watermark.feature.vip

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class VipTokenPendingStateTest {

    @Before
    fun resetState() {
        VipTokenPendingState.reset()
    }

    @After
    fun tearDown() {
        VipTokenPendingState.reset()
    }

    @Test
    fun begin_setsPendingState() {
        val pending = VipTokenPendingState

        pending.begin()

        assertThat(pending.state.value).isInstanceOf(VipTokenPendingState.State.Pending::class.java)
        assertThat((pending.state.value as VipTokenPendingState.State.Pending).timedOut).isFalse()
    }

    @Test
    fun timeout_keepsPendingStateLocked() {
        val pending = VipTokenPendingState
        pending.begin()

        pending.timeout()
        pending.timeout()

        assertThat((pending.state.value as VipTokenPendingState.State.Pending).timedOut).isTrue()
    }

    @Test
    fun resolve_reportsSuccessAndFailure() {
        val pending = VipTokenPendingState
        pending.begin()
        pending.resolve(activated = true)
        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))

        pending.consumeResolved()
        pending.begin()
        pending.resolve(activated = false)
        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = false))
        pending.consumeResolved()
    }

    @Test
    fun resolveBeforeUiBegins_retainsResultAndBeginDoesNotDiscardIt() {
        val pending = VipTokenPendingState

        pending.resolve(activated = true)
        pending.begin()

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun duplicateResolve_doesNotReplaceUnconsumedResult() {
        val pending = VipTokenPendingState
        pending.begin()
        pending.resolve(activated = false)

        pending.resolve(activated = true)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = false))
    }

    @Test
    fun duplicateResolveAfterConsume_doesNotRecreateResolvedResult() {
        val pending = VipTokenPendingState
        pending.begin()
        pending.resolve(activated = true)
        pending.consumeResolved()

        pending.resolve(activated = true)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
    }

    @Test
    fun successfulResolutionDuringNextCycle_resolvesCurrentPending() {
        val pending = VipTokenPendingState
        pending.begin()
        pending.resolve(activated = true)
        pending.consumeResolved()
        pending.begin()

        pending.resolve(activated = true)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun resolve_returnsTrueOnlyWhenResultIsAccepted() {
        VipTokenPendingState.begin()
        assertThat(VipTokenPendingState.resolve(activated = true)).isTrue()
        assertThat(VipTokenPendingState.resolve(activated = true)).isFalse()
        VipTokenPendingState.consumeResolved()
        assertThat(VipTokenPendingState.resolve(activated = true)).isFalse()
    }

    @Test
    fun resolve_failureWhileIdle_returnsFalseAndKeepsIdle() {
        assertThat(VipTokenPendingState.resolve(activated = false)).isFalse()
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
    }

    @Test
    fun consumeResolved_isIdempotentAndNeverRevivesResult() {
        VipTokenPendingState.begin()
        VipTokenPendingState.resolve(activated = true)

        VipTokenPendingState.consumeResolved()
        VipTokenPendingState.consumeResolved()

        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
    }

    @Test
    fun consumeResolved_withoutResolvedResult_leavesStateUntouched() {
        VipTokenPendingState.consumeResolved()
        assertThat(VipTokenPendingState.state.value).isEqualTo(VipTokenPendingState.State.Idle)
        VipTokenPendingState.begin()
        VipTokenPendingState.consumeResolved()
        assertThat(VipTokenPendingState.state.value).isInstanceOf(VipTokenPendingState.State.Pending::class.java)
    }

    @Test
    fun consumeResolved_marksCycleConsumed() {
        val pending = VipTokenPendingState
        pending.begin()
        pending.resolve(activated = true)

        pending.consumeResolved()

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Consumed)
    }

    @Test
    fun failureWithNoPendingCycle_isDroppedSoNoStrayDialogLater() {
        val pending = VipTokenPendingState

        pending.resolve(activated = false)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Idle)
    }

    @Test
    fun successWithNoPendingCycle_isStillRetainedToRecordVip() {
        val pending = VipTokenPendingState

        pending.resolve(activated = true)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = true))
    }

    @Test
    fun failureDuringPending_isStillReported() {
        val pending = VipTokenPendingState
        pending.begin()

        pending.resolve(activated = false)

        assertThat(pending.state.value).isEqualTo(VipTokenPendingState.State.Resolved(activated = false))
    }
}
