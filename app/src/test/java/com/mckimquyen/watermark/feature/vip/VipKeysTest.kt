package com.mckimquyen.watermark.feature.vip

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.BuildConfig
import org.junit.Test

class VipKeysTest {

    @Test
    fun classifyInput_legacyCodes_keepsLegacyRouteAndDuration() {
        assertThat(VipKeys.classifyInput(BuildConfig.VIP_LEGACY_30D_CODE))
            .isEqualTo(VipKeys.InputType.Legacy(30))
        assertThat(VipKeys.classifyInput(BuildConfig.VIP_LEGACY_3D_CODE))
            .isEqualTo(VipKeys.InputType.Legacy(3))
        assertThat(VipKeys.classifyInput(" ${BuildConfig.VIP_LEGACY_30D_CODE} "))
            .isEqualTo(VipKeys.InputType.Legacy(30))
    }

    @Test
    fun classifyInput_nonLegacyInput_routesToTokenActivation() {
        assertThat(VipKeys.classifyInput("eyJhbGciOiJFUzI1NiJ9.payload.signature"))
            .isEqualTo(VipKeys.InputType.Token)
    }

    @Test
    fun wrongOrEmptyCode_hasNoLegacyDuration() {
        assertThat(VipKeys.durationDaysFor("")).isNull()
        assertThat(VipKeys.durationDaysFor("wrong")).isNull()
    }

    @Test
    fun legacyCodes_differ() {
        assertThat(BuildConfig.VIP_LEGACY_30D_CODE).isNotEqualTo(BuildConfig.VIP_LEGACY_3D_CODE)
    }

    @Test
    fun vipTokenPublicKey_isNotSdkSampleKey() {
        assertThat(BuildConfig.VIP_TOKEN_PUBLIC_KEY).isNotEmpty()
        assertThat(BuildConfig.VIP_TOKEN_PUBLIC_KEY).doesNotContain("M0pDMMSTDmt3FVyE")
    }

    @Test
    fun redeemCodes_mapBothLegacyCodesToTheirDays() {
        val codes = VipKeys.redeemCodes()

        assertThat(codes).hasSize(2)
        assertThat(codes[BuildConfig.VIP_LEGACY_30D_CODE]).isEqualTo(30)
        assertThat(codes[BuildConfig.VIP_LEGACY_3D_CODE]).isEqualTo(3)
    }

    @Test
    fun redeemCodes_agreeWithDurationDays() {
        VipKeys.redeemCodes().forEach { (code, days) ->
            assertThat(VipKeys.durationDaysFor(code)).isEqualTo(days)
        }
    }

    @Test
    fun classifyInput_trimsSurroundingWhitespaceAndNewlines() {
        val code = BuildConfig.VIP_LEGACY_3D_CODE
        assertThat(VipKeys.classifyInput("  $code\n")).isEqualTo(VipKeys.InputType.Legacy(3))
        assertThat(VipKeys.classifyInput("\t$code\r\n")).isEqualTo(VipKeys.InputType.Legacy(3))
    }

    @Test
    fun classifyInput_isCaseSensitiveAndRejectsPartialCodes() {
        val code = BuildConfig.VIP_LEGACY_3D_CODE
        assertThat(VipKeys.classifyInput(code.dropLast(1))).isEqualTo(VipKeys.InputType.Token)
        assertThat(VipKeys.classifyInput(code + "x")).isEqualTo(VipKeys.InputType.Token)
        if (code != code.uppercase()) {
            assertThat(VipKeys.classifyInput(code.uppercase())).isEqualTo(VipKeys.InputType.Token)
        }
    }

    @Test
    fun classifyInput_emptyAndBlankFallThroughToToken() {
        assertThat(VipKeys.classifyInput("")).isEqualTo(VipKeys.InputType.Token)
        assertThat(VipKeys.classifyInput("   ")).isEqualTo(VipKeys.InputType.Token)
    }

    @Test
    fun classifyInput_zeroWidthCharacterInsideCode_isNotSilentlyAccepted() {
        val code = BuildConfig.VIP_LEGACY_3D_CODE
        val polluted = code.take(2) + "\u200B" + code.drop(2)
        assertThat(VipKeys.classifyInput(polluted)).isEqualTo(VipKeys.InputType.Token)
    }
}
