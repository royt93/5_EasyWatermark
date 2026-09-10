package com.mckimquyen.watermark.data.model

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.mckimquyen.watermark.data.repo.UserConfigRepository

@Keep
data class UserPreferences(
    val outputFormat: Bitmap.CompressFormat,
    val compressLevel: Int,
    val maxOutputLongEdge: Int = UserConfigRepository.DEFAULT_MAX_LONG_EDGE,
    val copyright: String = "",
    /** Pattern tên file xuất, vd "{filename}_wm_{seq}" (xem [TextTokenResolver]). Rỗng = hành vi mặc định "ewm_{timestamp}". */
    val outputNamePattern: String = ""
) {
    companion object {
        val DEFAULT = UserPreferences(
            UserConfigRepository.DEFAULT_BITMAP_COMPRESS_FORMAT,
            UserConfigRepository.DEFAULT_COMPRESS_LEVEL,
            UserConfigRepository.DEFAULT_MAX_LONG_EDGE,
            "",
            ""
        )
    }
}
