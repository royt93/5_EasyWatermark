package com.mckimquyen.watermark.feature.vip

import com.mckimquyen.watermark.BuildConfig

/**
 * 2 mã VIP "thẻ cào" (30/3 ngày). Giá trị nằm ở myKeyStore/.../app.properties → BuildConfig, không hardcode.
 * Khai cho SDK qua `AdSdkConfig.vipRedeemCodes` ([redeemCodes]); SDK 1.8.5 đã TẮT nhánh legacy plaintext
 * (`allowLegacyPlaintextVipKey=false`) nên đây là cách duy nhất giữ 2 mã cũ mà không mở lại nhánh kém an toàn.
 */
object VipKeys {
    private const val DAYS_LONG = 30
    private const val DAYS_SHORT = 3

    /** Map mã → số ngày cho `AdSdkConfig.vipRedeemCodes`. N ngày tính từ lúc nhập, mỗi mã 1 lần/máy. */
    fun redeemCodes(): Map<String, Int> = mapOf(
        BuildConfig.VIP_LEGACY_30D_CODE to DAYS_LONG,
        BuildConfig.VIP_LEGACY_3D_CODE to DAYS_SHORT
    )

    fun durationDaysFor(input: String): Int? {
        val normalized = input.trim()
        return when (normalized) {
            BuildConfig.VIP_LEGACY_30D_CODE -> DAYS_LONG
            BuildConfig.VIP_LEGACY_3D_CODE -> DAYS_SHORT
            else -> null
        }
    }
}
