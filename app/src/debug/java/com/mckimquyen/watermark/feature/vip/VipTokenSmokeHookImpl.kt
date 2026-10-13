package com.mckimquyen.watermark.feature.vip

import android.content.Context

internal object VipTokenSmokeHookImpl : VipTokenSmokeHook {
    override fun consumePendingToken(context: Context, submit: (String) -> Boolean): SubmitResult {
        val filesDir = context.noBackupFilesDir
        val harness = VipTokenSmokeHarness(filesDir) { error("Mint signer is unavailable during app startup") }
        return harness.consumePendingToken(submit)
    }

    override fun consumeInitDelayMs(context: Context): Long =
        VipTokenSmokeHarness(context.noBackupFilesDir) { error("Mint signer is unavailable during app startup") }
            .consumeInitDelayMs()
}
