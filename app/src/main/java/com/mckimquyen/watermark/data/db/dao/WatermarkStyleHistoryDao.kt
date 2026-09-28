package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity

@Dao
interface WatermarkStyleHistoryDao {

    @Insert
    suspend fun insert(entity: WatermarkStyleHistoryEntity): Long

    @Query("SELECT * FROM watermark_style_history ORDER BY timestamp DESC LIMIT :n")
    suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity>

    /** Giữ tối đa [keep] dòng mới nhất — tránh bảng phình vô hạn theo thời gian dùng app. */
    @Query("DELETE FROM watermark_style_history WHERE id NOT IN (SELECT id FROM watermark_style_history ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun pruneKeepLatest(keep: Int)

    /**
     * Code review 2026-09-28: gộp [insert] + [pruneKeepLatest] vào 1 transaction — trước đó 2 lời
     * gọi suspend tách rời, process chết đúng giữa 2 lệnh (rất hiếm, nhưng có thể) để bảng phình quá
     * [keep] tạm thời cho tới lần ghi thành công kế tiếp mới tự prune lại.
     */
    @Transaction
    suspend fun recordAndPrune(entity: WatermarkStyleHistoryEntity, keep: Int) {
        insert(entity)
        pruneKeepLatest(keep)
    }
}
