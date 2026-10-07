package com.mckimquyen.watermark.common.const

import com.mckimquyen.watermark.BuildConfig

object AdKeys {
    val PRIVACY_POLICY_URL: String = BuildConfig.PRIVACY_POLICY_URL

    // Nguồn thật: myKeyStore/com.mckimquyen.watermark/app.properties (VIP_KEY_SECRET) → BuildConfig.
    val VIP_SECRET_30_DAYS: String = BuildConfig.VIP_KEY_SECRET
}
