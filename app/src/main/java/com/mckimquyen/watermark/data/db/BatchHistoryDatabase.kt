package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity

/**
 * FEAT-04: DB riêng, KHÔNG chung với [AppDatabase] — xem doc ở [BatchHistoryEntity] lý do tách.
 */
@Database(entities = [BatchHistoryEntity::class], version = 2, exportSchema = false)
abstract class BatchHistoryDatabase : RoomDatabase() {
    abstract fun batchHistoryDao(): BatchHistoryDao

    companion object {
        /**
         * IDEA-10: thêm cột `recipientCode` và `recipientName` để lưu thông tin người nhận theo batch.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE batch_history ADD COLUMN recipientCode TEXT")
                db.execSQL("ALTER TABLE batch_history ADD COLUMN recipientName TEXT")
            }
        }
    }
}
