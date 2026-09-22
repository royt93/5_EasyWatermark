package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchHistoryDao {

    @Query("SELECT * FROM batch_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BatchHistoryEntity>>

    @Insert
    suspend fun insert(entity: BatchHistoryEntity): Long

    @Query("DELETE FROM batch_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** FEAT-04: giữ tối đa [keepCount] entry gần nhất — tránh phình DB vô hạn qua thời gian dùng app. */
    @Query("DELETE FROM batch_history WHERE id NOT IN (SELECT id FROM batch_history ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimOldest(keepCount: Int)
}
