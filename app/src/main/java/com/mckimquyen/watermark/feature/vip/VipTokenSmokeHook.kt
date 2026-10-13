package com.mckimquyen.watermark.feature.vip

import android.content.Context

internal interface VipTokenSmokeHook {
    fun consumePendingToken(context: Context, submit: (String) -> Boolean): SubmitResult

    /** Mili-giây cần hoãn `AdManager.initialize` (debug smoke); release luôn 0. Đọc là xoá. */
    fun consumeInitDelayMs(context: Context): Long
}

internal enum class SubmitResult {
    NotArmed,
    Submitted,
    NotSubmitted,
    Failed
}
