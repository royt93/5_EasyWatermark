package com.mckimquyen.watermark.utils

import android.content.Intent
import android.net.Uri

/**
 * BUG-09: resolve URI ảnh từ intent `ACTION_SEND` — share sheet Android đặt URI ở
 * `EXTRA_STREAM`, không phải `intent.data` (data chỉ dùng cho `ACTION_VIEW`/deep-link).
 */
object ShareIntentResolver {

    fun resolveSharedImageUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }
}
