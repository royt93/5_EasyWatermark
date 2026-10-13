package com.mckimquyen.watermark.feature.vip

import android.app.Activity
import android.content.Intent

internal object VipTokenSmokeEntryPointImpl : VipTokenSmokeEntryPoint {
    override fun open(activity: Activity) {
        activity.startActivity(Intent(activity, VipTokenSmokeActivity::class.java))
    }
}
