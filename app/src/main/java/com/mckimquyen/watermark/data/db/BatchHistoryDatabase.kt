package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity

/**
 * FEAT-04: DB riêng, KHÔNG chung với [AppDatabase] — xem doc ở [BatchHistoryEntity] lý do tách.
 */
@Database(entities = [BatchHistoryEntity::class], version = 1, exportSchema = false)
abstract class BatchHistoryDatabase : RoomDatabase() {
    abstract fun batchHistoryDao(): BatchHistoryDao
}
