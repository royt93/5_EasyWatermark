package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import kotlinx.coroutines.flow.Flow

/** BUG-64: projection nhẹ (id + iconUri) cho migration, tránh nạp cả entity. */
data class ProfileIconUri(val id: Long, val iconUri: String)

@Dao
interface WatermarkProfileDao {

    @Query("SELECT * FROM watermark_profile ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WatermarkProfileEntity>>

    @Insert
    suspend fun insert(entity: WatermarkProfileEntity): Long

    /** BUG-64: đọc một lần (không Flow) để migrate URI icon cũ. */
    @Query("SELECT id, iconUri FROM watermark_profile")
    suspend fun getAllIconUris(): List<ProfileIconUri>

    @Query("UPDATE watermark_profile SET iconUri = :iconUri WHERE id = :id")
    suspend fun updateIconUri(id: Long, iconUri: String)

    @Query("DELETE FROM watermark_profile WHERE id = :id")
    suspend fun deleteById(id: Long)
}
