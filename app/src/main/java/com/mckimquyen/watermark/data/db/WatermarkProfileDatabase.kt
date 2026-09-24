package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity

/**
 * FEAT-06: DB riêng, KHÔNG chung với [AppDatabase] — xem doc ở [WatermarkProfileEntity] lý do tách.
 */
@Database(entities = [WatermarkProfileEntity::class], version = 2, exportSchema = false)
abstract class WatermarkProfileDatabase : RoomDatabase() {
    abstract fun watermarkProfileDao(): WatermarkProfileDao

    companion object {
        /**
         * FEAT-03: thêm cột `extraLayersRaw` (layer phụ) — builder (`di/AppModule.kt`) KHÔNG có
         * `fallbackToDestructiveMigration()`, thiếu Migration thật sẽ crash app cho user đã có
         * profile lưu sẵn trên máy (khác `AppDatabase` asset-seeded, không thể fallback destructive).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watermark_profile ADD COLUMN extraLayersRaw TEXT")
            }
        }
    }
}
