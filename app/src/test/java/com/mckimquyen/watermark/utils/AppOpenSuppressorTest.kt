package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/** AppOpenSuppressor: cờ chặn App Open phải bật quanh launch, tắt khi xong và KHÔNG kẹt khi launch ném lỗi. */
class AppOpenSuppressorTest {

    private val calls = mutableListOf<Boolean>()
    private val original = AppOpenSuppressor.setSuppressed

    @Before
    fun setUp() {
        calls.clear()
        AppOpenSuppressor.setSuppressed = { calls.add(it) }
    }

    @After
    fun tearDown() {
        AppOpenSuppressor.setSuppressed = original
    }

    @Test
    fun begin_thenEnd_togglesTrueThenFalse() {
        AppOpenSuppressor.begin()
        AppOpenSuppressor.end()

        assertThat(calls).containsExactly(true, false).inOrder()
    }

    @Test
    fun around_enablesSuppressBeforeRunningLaunch() {
        var suppressedWhenLaunching: Boolean? = null

        AppOpenSuppressor.around { suppressedWhenLaunching = calls.lastOrNull() }

        assertThat(suppressedWhenLaunching).isTrue()
        // Thành công: giữ cờ bật tới khi kết quả launcher về (host gọi end() ở onResume), không tự tắt.
        assertThat(calls).containsExactly(true)
    }

    @Test
    fun around_returnsLaunchResult() {
        assertThat(AppOpenSuppressor.around { 42 }).isEqualTo(42)
    }

    @Test
    fun around_whenLaunchThrows_clearsFlagAndRethrows() {
        val boom = IllegalStateException("no activity to handle intent")

        val thrown = runCatching { AppOpenSuppressor.around<Unit> { throw boom } }.exceptionOrNull()

        assertThat(thrown).isSameInstanceAs(boom)
        // Không để kẹt cờ: true rồi false ngay.
        assertThat(calls).containsExactly(true, false).inOrder()
    }

    @Test
    fun end_withoutBegin_isHarmless() {
        AppOpenSuppressor.end()

        assertThat(calls).containsExactly(false)
    }
}
