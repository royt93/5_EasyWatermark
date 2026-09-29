package com.mckimquyen.watermark.utils

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * BUG-09: resolve URI ảnh từ intent `ACTION_SEND` — share sheet Android đặt URI ở
 * `EXTRA_STREAM`, không phải `intent.data` (data chỉ dùng cho `ACTION_VIEW`/deep-link).
 * BUG-AUDIT-2026-09-29-R7: dùng IntentCompat.getParcelableExtra() type-safe cho Android 13+ (API 33+),
 * tránh warning deprecation và chống ClassCastException nếu sender gửi sai kiểu.
 */
object ShareIntentResolver {

    fun resolveSharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
    }
}
