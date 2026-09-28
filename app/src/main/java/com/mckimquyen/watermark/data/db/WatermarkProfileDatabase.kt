package com.mckimquyen.watermark.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity

/**
 * FEAT-06: DB riêng, KHÔNG chung với [AppDatabase] — xem doc ở [WatermarkProfileEntity] lý do tách.
 * IDEA-12: thêm bảng `watermark_style_history` (version 3) vào chung DB này thay vì mở DB riêng.
 */
@Database(
    entities = [WatermarkProfileEntity::class, WatermarkStyleHistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class WatermarkProfileDatabase : RoomDatabase() {
    abstract fun watermarkProfileDao(): WatermarkProfileDao
    abstract fun watermarkStyleHistoryDao(): WatermarkStyleHistoryDao

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

        /** IDEA-12: tạo bảng mới, KHÔNG đụng bảng `watermark_profile` sẵn có — an toàn cho data cũ. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `watermark_style_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `textColor` INTEGER NOT NULL,
                        `textStyleKey` INTEGER NOT NULL,
                        `textTypefaceKey` INTEGER NOT NULL,
                        `alpha` INTEGER NOT NULL,
                        `anchor` INTEGER NOT NULL,
                        `markModeValue` INTEGER NOT NULL,
                        `exifFrameStyle` INTEGER NOT NULL,
                        `textEffectStroke` INTEGER NOT NULL,
                        `textEffectShadow` INTEGER NOT NULL,
                        `textEffectPillBackground` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
