package com.mckimquyen.watermark.feature.vip

import com.mckimquyen.watermark.BuildConfig

/** Mã VIP legacy 30/3 ngày; token ECDSA mới do SDK tự xác minh. */
object VipKeys {
    private const val DAYS_LONG = 30
    private const val DAYS_SHORT = 3

    sealed class InputType {
        data class Legacy(val days: Int) : InputType()
        object Token : InputType()
    }

    /** Map mã → số ngày cho `AdSdkConfig.vipRedeemCodes`; mỗi mã chỉ dùng một lần/thiết bị. */
    fun redeemCodes(): Map<String, Int> = mapOf(
        BuildConfig.VIP_LEGACY_30D_CODE to DAYS_LONG,
        BuildConfig.VIP_LEGACY_3D_CODE to DAYS_SHORT
    )

    fun durationDaysFor(input: String): Int? = when (input.trim()) {
        BuildConfig.VIP_LEGACY_30D_CODE -> DAYS_LONG
        BuildConfig.VIP_LEGACY_3D_CODE -> DAYS_SHORT
        else -> null
    }

    fun classifyInput(input: String): InputType =
        durationDaysFor(input)?.let(InputType::Legacy) ?: InputType.Token
}
