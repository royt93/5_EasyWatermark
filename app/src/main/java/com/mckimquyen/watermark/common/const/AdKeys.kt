package com.mckimquyen.watermark.common.const

import android.util.Base64
import com.mckimquyen.watermark.BuildConfig

object AdKeys {
    val PRIVACY_POLICY_URL: String = BuildConfig.PRIVACY_POLICY_URL

    val VIP_SECRET_30_DAYS: String by lazy {
        String(Base64.decode(VIP_SECRET_30_DAYS_B64, Base64.NO_WRAP))
    }

    private const val VIP_SECRET_30_DAYS_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="
}
