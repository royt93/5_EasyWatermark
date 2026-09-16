package com.mckimquyen.watermark.ui

import android.net.Uri
import androidx.annotation.Keep

@Keep
data class Image(
    val id: Int,
    val uri: Uri,
    val name: String,
    val size: Long,
    val date: Long,
    var check: Boolean = false
)
