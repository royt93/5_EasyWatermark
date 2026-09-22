package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity

/**
 * FEAT-06: DB riêng, KHÔNG chung với [AppDatabase] — xem doc ở [WatermarkProfileEntity] lý do tách.
 */
@Database(entities = [WatermarkProfileEntity::class], version = 1, exportSchema = false)
abstract class WatermarkProfileDatabase : RoomDatabase() {
    abstract fun watermarkProfileDao(): WatermarkProfileDao
}
