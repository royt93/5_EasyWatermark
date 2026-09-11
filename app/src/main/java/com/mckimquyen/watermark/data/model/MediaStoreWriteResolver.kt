package com.mckimquyen.watermark.data.model

/**
 * BUG-19: `contentResolver.openFileDescriptor()` có thể trả `null`, và `Bitmap.compress()` trả
 * `Boolean` báo thành công/thất bại — cả 2 trước đây bị bỏ qua ở nhánh ghi MediaStore, dẫn tới
 * báo "thành công" giả và để lại row `IS_PENDING` rác khi ghi thất bại. Hàm thuần (không phụ
 * thuộc Android runtime thật) để dễ unit test, theo đúng pattern [MediaStoreInsertResolver].
 */
object MediaStoreWriteResolver {

    fun resolve(fdAvailable: Boolean, compressSucceeded: Boolean, errorCode: String): Result<Unit> =
        when {
            !fdAvailable -> Result.failure(
                data = null,
                code = errorCode,
                message = "openFileDescriptor() returned null."
            )

            !compressSucceeded -> Result.failure(
                data = null,
                code = errorCode,
                message = "Bitmap.compress() returned false."
            )

            else -> Result.success(Unit)
        }
}
