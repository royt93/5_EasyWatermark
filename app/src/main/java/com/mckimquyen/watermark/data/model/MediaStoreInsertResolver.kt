package com.mckimquyen.watermark.data.model

import android.net.Uri

/**
 * Bọc kết quả `ContentResolver.insert()`. `insert()` có thể trả `null` (provider từ chối,
 * hết dung lượng, lỗi nội bộ MediaStore) — trước đây bị force-unwrap `!!` gây crash (BUG-04).
 * Hàm thuần (không phụ thuộc Android runtime thật) để dễ unit test.
 */
object MediaStoreInsertResolver {

    fun resolve(insertedUri: Uri?, errorCode: String): Result<Uri> =
        if (insertedUri != null) {
            Result.success(insertedUri)
        } else {
            Result.failure(
                data = null,
                code = errorCode,
                message = "MediaStore insert() returned null."
            )
        }
}
