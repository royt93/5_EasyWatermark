package com.mckimquyen.watermark.feature.vip

import com.mckimquyen.watermark.BuildConfig

/** 2 mã VIP legacy (30/3 ngày). Giá trị nằm ở myKeyStore/.../app.properties → BuildConfig, không hardcode. */
object VipKeys {
    private const val DAYS_LONG = 30
    private const val DAYS_SHORT = 3

    fun durationDaysFor(input: String): Int? {
        val normalized = input.trim()
        return when (normalized) {
            BuildConfig.VIP_LEGACY_30D_CODE -> DAYS_LONG
            BuildConfig.VIP_LEGACY_3D_CODE -> DAYS_SHORT
            else -> null
        }
    }
}
