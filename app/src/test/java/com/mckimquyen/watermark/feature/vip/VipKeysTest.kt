package com.mckimquyen.watermark.feature.vip

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import org.junit.Test

class VipKeysTest {

    @Test
    fun legacyCodes_mapToExpectedDays() {
        assertThat(VipKeys.durationDaysFor(BuildConfig.VIP_LEGACY_30D_CODE)).isEqualTo(30)
        assertThat(VipKeys.durationDaysFor(BuildConfig.VIP_LEGACY_3D_CODE)).isEqualTo(3)
        assertThat(VipKeys.durationDaysFor("  ${BuildConfig.VIP_LEGACY_3D_CODE}  ")).isEqualTo(3)
    }

    @Test
    fun wrongOrEmptyCode_isRejected() {
        assertThat(VipKeys.durationDaysFor("")).isNull()
        assertThat(VipKeys.durationDaysFor("wrong")).isNull()
    }

    @Test
    fun legacyCodes_differ() {
        assertThat(BuildConfig.VIP_LEGACY_30D_CODE).isNotEqualTo(BuildConfig.VIP_LEGACY_3D_CODE)
    }

    @Test
    fun vipTokenPublicKey_isNotSdkSampleKey() {
        // SDK sample key bắt đầu bằng MFkwEw...EAM0pDMMS; release phải dùng key riêng.
        assertThat(BuildConfig.VIP_TOKEN_PUBLIC_KEY).isNotEmpty()
        assertThat(BuildConfig.VIP_TOKEN_PUBLIC_KEY).doesNotContain("M0pDMMSTDmt3FVyE")
    }
}
