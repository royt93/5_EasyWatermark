package com.mckimquyen.watermark.data.model

import org.junit.Test
import kotlin.test.assertEquals

class ResultErrorCodeTest {
    @Test
    fun `ERROR_CODE_GENERIC equals minus one`() {
        assertEquals("-1", ERROR_CODE_GENERIC)
    }

    @Test
    fun `Result failure with ERROR_CODE_GENERIC stores code`() {
        val result: Result<String> = Result.failure(
            data = null,
            code = ERROR_CODE_GENERIC,
            message = "Test error"
        )
        assertEquals(ERROR_CODE_GENERIC, result.code)
        assertEquals("-1", result.code)
        assertEquals(true, result.isFailure())
    }

    @Test
    fun `Multiple failure results use same ERROR_CODE_GENERIC`() {
        val result1 = Result.failure<String>(code = ERROR_CODE_GENERIC)
        val result2 = Result.failure<String>(code = ERROR_CODE_GENERIC)
        assertEquals(result1.code, result2.code)
        assertEquals("-1", result1.code)
    }
}
