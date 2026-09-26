package com.mckimquyen.watermark.data.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import com.mckimquyen.watermark.data.repo.UserConfigRepository

@Keep
data class UserPreferences(
    val outputFormat: Bitmap.CompressFormat,
    val compressLevel: Int,
    val maxOutputLongEdge: Int = UserConfigRepository.DEFAULT_MAX_LONG_EDGE,
    val copyright: String = "",
    /** Pattern tên file xuất, vd "{filename}_wm_{seq}" (xem [TextTokenResolver]). Rỗng = hành vi mặc định "ewm_{timestamp}". */
    val outputNamePattern: String = "",
    /** FEAT-19: Chính sách xử lý trùng tên file khi export lại. */
    val conflictPolicy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
    /** FEAT-15: null = xuất vào Pictures/WaterMarkCreator/ (mặc định); khác null = thư mục SAF user tự chọn. */
    val outputDirectoryUri: Uri? = null,
    /** IDEA-13: bật chế độ proofing — watermark lớn đánh số + sinh proof_index.html. */
    val proofingMode: Boolean = false
) {
    companion object {
        val DEFAULT = UserPreferences(
            UserConfigRepository.DEFAULT_BITMAP_COMPRESS_FORMAT,
            UserConfigRepository.DEFAULT_COMPRESS_LEVEL,
            UserConfigRepository.DEFAULT_MAX_LONG_EDGE,
            "",
            "",
            ConflictPolicy.KEEP_BOTH
        )
    }
}
