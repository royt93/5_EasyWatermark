package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}
