package com.mckimquyen.watermark

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * ENH-42: `AppLog.d`/`AppLog.i` nhận `msg: String` sẵn — Kotlin dựng xong chuỗi interpolation
 * TRƯỚC khi gọi vào hàm, bất kể `if (BuildConfig.DEBUG)` bên trong có gate hay không (chỉ chặn
 * được `Log.d`/`Log.i` syscall thật, không chặn được việc build string ở call site). Đổi sang
 * `inline fun` nhận lambda `() -> String` — thân lambda (bao gồm string interpolation) chỉ được
 * evaluate khi `BuildConfig.DEBUG == true`.
 *
 * Test này chạy được dưới CẢ 2 variant (không cần dựng riêng, tận dụng `src/test` dùng chung):
 * `testDebugUnitTest` biên dịch với `BuildConfig.DEBUG=true` → lambda ĐƯỢC gọi; `testReleaseUnitTest`
 * biên dịch với `BuildConfig.DEBUG=false` → lambda KHÔNG được gọi. Assert so với chính
 * `BuildConfig.DEBUG` nên đúng ở cả 2 variant, không cần file test riêng cho release.
 */
class AppLogGatingTest {

    @Test
    fun d_lambdaEvaluated_onlyWhenDebugBuild() {
        var evaluated = false
        AppLog.d("TAG") { evaluated = true; "msg tốn CPU dựng" }

        assertThat(evaluated).isEqualTo(BuildConfig.DEBUG)
    }

    @Test
    fun i_lambdaEvaluated_onlyWhenDebugBuild() {
        var evaluated = false
        AppLog.i("TAG") { evaluated = true; "msg tốn CPU dựng" }

        assertThat(evaluated).isEqualTo(BuildConfig.DEBUG)
    }
}
