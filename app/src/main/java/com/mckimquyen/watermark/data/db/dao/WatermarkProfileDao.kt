package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatermarkProfileDao {

    @Query("SELECT * FROM watermark_profile ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WatermarkProfileEntity>>

    @Insert
    suspend fun insert(entity: WatermarkProfileEntity): Long

    @Query("DELETE FROM watermark_profile WHERE id = :id")
    suspend fun deleteById(id: Long)
}
