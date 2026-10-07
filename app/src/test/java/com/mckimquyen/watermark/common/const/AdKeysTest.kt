package com.mckimquyen.watermark.common.const

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Secret không còn nằm trong source: BuildConfig phải được bơm từ myKeyStore/.../app.properties. */
class AdKeysTest {

    @Test
    fun vipSecret_comesFromBuildConfig() {
        assertThat(AdKeys.VIP_SECRET_30_DAYS).isEqualTo(BuildConfig.VIP_KEY_SECRET)
        assertThat(AdKeys.VIP_SECRET_30_DAYS).isNotEmpty()
    }

    @Test
    fun testDeviceIds_areValidHashes_andSurviveParse() {
        // Máy không có file private (CI) → bỏ qua thay vì fail.
        assumeTrue(BuildConfig.ADMOB_TEST_DEVICE_IDS.isNotBlank())
        val ids = BuildConfig.ADMOB_TEST_DEVICE_IDS.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        assertThat(ids).isNotEmpty()
        assertThat(ids).containsNoDuplicates()
        ids.forEach { assertThat(it).matches("[0-9A-F]{32}") }
    }
}
