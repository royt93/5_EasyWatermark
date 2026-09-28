package com.mckimquyen.watermark.data.model

/** Hành động cleanup MediaStore khi ghi thất bại, xem [MediaStoreWriteFailureCleanup]. */
enum class MediaStoreCleanupAction {
    /** Row vừa `insert()` (KEEP_BOTH/RENAME_VERSION) — xoá row rác như BUG-19. */
    DELETE_ROW,

    /** Row đã có sẵn từ trước, chỉ bị đánh `IS_PENDING=1` để ghi đè (OVERWRITE) — trả về 0
     * để ảnh CŨ của user hiện lại trong gallery, không xoá. */
    CLEAR_PENDING
}

/**
 * BUG-37: `contentResolver.update(existingUri, IS_PENDING=1, ...)` đánh dấu row đã có sẵn
 * (OVERWRITE) trước khi ghi. Nếu ghi thất bại, nhánh cleanup cũ chỉ `delete()` khi
 * `isNewRow == true` (BUG-19) — row cũ mắc `IS_PENDING=1` vĩnh viễn, mất khỏi gallery. Hàm thuần
 * (không phụ thuộc Android runtime thật) quyết định đúng hành động cho cả 2 trường hợp, theo
 * đúng pattern [MediaStoreWriteResolver].
 */
object MediaStoreWriteFailureCleanup {

    fun decide(isNewRow: Boolean): MediaStoreCleanupAction =
        if (isNewRow) MediaStoreCleanupAction.DELETE_ROW else MediaStoreCleanupAction.CLEAR_PENDING
}
