package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P1 finding review pass 8: `JobState.Success`/`Failure` là plain class (không `data class`) ->
 * `equals()` so reference identity, khiến `DiffUtil.areContentsTheSame`
 * (`SaveImageListAdapter.differCallback`) luôn coi 2 state có nội dung giống hệt nhau là KHÁC
 * nhau -> rebind/flicker thừa mỗi lần `jobState` emit lại cùng giá trị.
 */
class JobStateTest {

    @Test
    fun success_sameResultContent_areEqual() {
        val a = JobState.Success(Result.success(data = "uri", code = "OK"))
        val b = JobState.Success(Result.success(data = "uri", code = "OK"))

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun failure_sameResultContent_areEqual() {
        val a = JobState.Failure(Result.failure<Any>(code = "ERR", message = "boom"))
        val b = JobState.Failure(Result.failure<Any>(code = "ERR", message = "boom"))

        assertThat(a).isEqualTo(b)
    }

    @Test
    fun success_differentResultContent_areNotEqual() {
        val a = JobState.Success(Result.success(data = "uri1"))
        val b = JobState.Success(Result.success(data = "uri2"))

        assertThat(a).isNotEqualTo(b)
    }
}
