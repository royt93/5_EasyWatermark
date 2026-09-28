package com.mckimquyen.watermark.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BUG-37: khi ghi MediaStore thất bại với `conflictPolicy = OVERWRITE`, row đã có sẵn (ảnh CŨ
 * của user) bị đánh `IS_PENDING=1` trước khi ghi, nhưng nhánh cleanup cũ chỉ `delete()` khi
 * `isNewRow == true` — row cũ bị bỏ quên ở trạng thái pending, mất khỏi gallery vĩnh viễn.
 * Hàm thuần quyết định hành động cleanup đúng cho cả 2 trường hợp (row mới vs row ghi đè).
 */
class MediaStoreWriteFailureCleanupTest {

    @Test
    fun decide_isNewRowTrue_returnsDeleteRow() {
        val action = MediaStoreWriteFailureCleanup.decide(isNewRow = true)

        assertThat(action).isEqualTo(MediaStoreCleanupAction.DELETE_ROW)
    }

    @Test
    fun decide_isNewRowFalse_returnsClearPending() {
        val action = MediaStoreWriteFailureCleanup.decide(isNewRow = false)

        assertThat(action).isEqualTo(MediaStoreCleanupAction.CLEAR_PENDING)
    }
}
