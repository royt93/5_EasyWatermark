package com.mckimquyen.watermark.data.model

import androidx.annotation.Keep

/**
 * FEAT-19: Chính sách xử lý trùng tên file khi export lại cùng batch/naming pattern.
 *
 * - [KEEP_BOTH]: Giữ cả 2 tệp. Mặc định của Android MediaStore (tự động thêm suffix dạng (1), (2)).
 * - [RENAME_VERSION]: Tự động thêm hậu tố phiên bản rõ ràng (_v2, _v3, ...) do app kiểm soát.
 * - [OVERWRITE]: Ghi đè lên tệp cũ đã tồn tại cùng tên trong thư mục đích.
 * - [SKIP]: Bỏ qua không export lại nếu tệp đích đã tồn tại (coi như hoàn thành với URI hiện có).
 */
@Keep
enum class ConflictPolicy(val id: Int) {
    KEEP_BOTH(0),
    RENAME_VERSION(1),
    OVERWRITE(2),
    SKIP(3);

    companion object {
        fun fromId(id: Int): ConflictPolicy = entries.firstOrNull { it.id == id } ?: KEEP_BOTH
    }
}
