package com.mckimquyen.watermark.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mckimquyen.watermark.data.model.entity.Recipient
import kotlinx.coroutines.flow.Flow

/**
 * IDEA-10: Quản lý truy xuất danh sách người nhận trong Room DB.
 */
@Dao
interface RecipientDao {

    @Query("SELECT * FROM recipient ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Recipient>>

    @Query("SELECT * FROM recipient ORDER BY timestamp DESC")
    suspend fun getAllList(): List<Recipient>

    @Query("SELECT * FROM recipient WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Recipient?

    @Query("SELECT * FROM recipient WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Recipient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recipient: Recipient): Long

    @Update
    suspend fun update(recipient: Recipient)

    @Delete
    suspend fun delete(recipient: Recipient)

    @Query("DELETE FROM recipient WHERE id = :id")
    suspend fun deleteById(id: Long)
}
