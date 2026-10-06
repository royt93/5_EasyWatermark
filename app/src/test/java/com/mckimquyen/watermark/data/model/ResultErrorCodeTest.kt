package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * P2: [ERROR_CODE_GENERIC] = "-1" constant refactor — thay thế 14 hardcode "-1" chuỗi
 * qua codebase với named constant duy nhất. Verify giá trị và Result.failure() sử dụng đúng.
 */
class ResultErrorCodeTest {
    @Test
    fun errorCodeGeneric_equalsMinusOne() {
        assertThat(ERROR_CODE_GENERIC).isEqualTo("-1")
    }

    @Test
    fun resultFailure_withErrorCodeGeneric_storesCodeCorrectly() {
        val result: Result<String> = Result.failure(
            data = null,
            code = ERROR_CODE_GENERIC,
            message = "Test error"
        )
        assertThat(result.code).isEqualTo(ERROR_CODE_GENERIC)
        assertThat(result.code).isEqualTo("-1")
        assertThat(result.isFailure()).isTrue()
    }

    @Test
    fun multipleFailureResults_useSameErrorCodeGeneric() {
        val result1 = Result.failure<String>(code = ERROR_CODE_GENERIC)
        val result2 = Result.failure<String>(code = ERROR_CODE_GENERIC)
        assertThat(result1.code).isEqualTo(result2.code)
        assertThat(result1.code).isEqualTo("-1")
    }
}
