package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test thuần (JVM) cho [JobStateResolver] — trực tiếp phòng regression BUG-03
 * (per-item jobState báo Success giả dù `Result` là failure).
 */
class JobStateResolverTest {

    @Test
    fun resolve_successResult_returnsJobStateSuccess() {
        val result = Result.success(data = "output-uri")

        val jobState = JobStateResolver.resolve(result)

        assertThat(jobState).isInstanceOf(JobState.Success::class.java)
        assertThat((jobState as JobState.Success).result).isSameInstanceAs(result)
    }

    @Test
    fun resolve_failureResult_returnsJobStateFailure() {
        val result = Result.failure<String>(data = null, code = "type_error_save_oom")

        val jobState = JobStateResolver.resolve(result)

        assertThat(jobState).isInstanceOf(JobState.Failure::class.java)
        assertThat((jobState as JobState.Failure).result).isSameInstanceAs(result)
    }

    @Test
    fun resolve_failureResultWithNonNullData_stillReturnsFailure() {
        // Kiểm tra edge case: type == Failure quyết định, không phải data null hay không.
        val result = Result.failure(data = "partial-data", code = "type_error_x")

        val jobState = JobStateResolver.resolve(result)

        assertThat(jobState).isInstanceOf(JobState.Failure::class.java)
    }

    @Test
    fun resolve_nullResult_throwsIllegalArgument() {
        try {
            JobStateResolver.resolve(null)
            throw AssertionError("Expected IllegalArgumentException but nothing was thrown")
        } catch (e: IllegalArgumentException) {
            // expected: requireNotNull ném IllegalArgumentException
        }
    }
}
