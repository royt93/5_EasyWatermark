package com.mckimquyen.watermark.feature.vip

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VipTokenPendingState {
    sealed class State {
        data object Idle : State()
        data object Consumed : State()
        data class Pending(val timedOut: Boolean, val startedAtElapsedRealtime: Long) : State()
        data class Resolved(val activated: Boolean) : State()
    }

    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()

    fun begin() {
        mutableState.update { current ->
            if (current == State.Idle || current == State.Consumed) {
                State.Pending(
                    timedOut = false,
                    startedAtElapsedRealtime = SystemClock.elapsedRealtime()
                )
            } else {
                current
            }
        }
    }

    fun timeout() {
        mutableState.update { current ->
            if (current is State.Pending && !current.timedOut) current.copy(timedOut = true) else current
        }
    }

    /**
     * Công bố kết quả; trả true nếu kết quả được chấp nhận (chuyển sang [State.Resolved]).
     * Kết quả trùng (đã Resolved/Consumed) hoặc thất bại khi không có chu kỳ pending thì bị bỏ và trả false.
     */
    fun resolve(activated: Boolean): Boolean {
        var accepted = false
        mutableState.update { current ->
            when {
                current is State.Resolved || current == State.Consumed -> current
                // Thất bại khi không có chu kỳ pending nào đang chờ: bỏ đi để lần mở VIP sau không bật dialog lạ.
                // Thành công vẫn giữ lại để ghi nhận VIP dù không có màn hình nào đang mở.
                !activated && current == State.Idle -> current
                else -> State.Resolved(activated).also { accepted = true }
            }
        }
        return accepted
    }

    fun consumeResolved() {
        mutableState.update { current ->
            if (current is State.Resolved) State.Consumed else current
        }
    }

    @VisibleForTesting
    fun reset() {
        mutableState.value = State.Idle
    }
}
