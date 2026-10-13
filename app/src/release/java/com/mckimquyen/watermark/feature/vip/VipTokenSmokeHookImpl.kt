package com.mckimquyen.watermark.feature.vip

import android.content.Context

internal object VipTokenSmokeHookImpl : VipTokenSmokeHook {
    override fun consumePendingToken(context: Context, submit: (String) -> Boolean): SubmitResult =
        SubmitResult.NotArmed

    override fun consumeInitDelayMs(context: Context): Long = 0L
}
