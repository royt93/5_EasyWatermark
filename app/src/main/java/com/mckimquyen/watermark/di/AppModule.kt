package com.mckimquyen.watermark.di

import android.content.Context
import androidx.room.Room
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.data.db.AppDatabase
import com.mckimquyen.watermark.data.db.BatchHistoryDatabase
import com.mckimquyen.watermark.data.db.RecipientDatabase
import com.mckimquyen.watermark.data.db.WatermarkProfileDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideYourDatabase(
        @ApplicationContext app: Context
    ): AppDatabase? {
        val builder = Room.databaseBuilder(
            context = app,
            klass = AppDatabase::class.java,
            name = "ewm-db"
        )
        val isCh = Locale.getDefault().language.contains("zh")
        builder.createFromAsset(if (isCh) "ewm-db-ch.db" else "ewm-db-eng.db")
        try {
            return builder.build()
        } catch (e: Exception) {
            // BUG-AUDIT-2026-09-29: trước đây chỉ printStackTrace() (không log qua AppLog như mọi
            // repo khác trong app) — nếu asset DB hỏng/schema bump quên cập nhật asset, Template
            // tắt câm lặng vĩnh viễn (TemplateRepository tự no-op khi dao null) không dấu vết debug.
            AppLog.e("AppModule", "provideYourDatabase: Room.build() failed, Template feature disabled", e)
        }
        return null
    }

    @Singleton
    @Provides
    fun provideTemplateDao(db: AppDatabase?) = db?.templateDao()

    /** FEAT-04: DB riêng (không seed từ asset) — xem doc ở `BatchHistoryEntity`. */
    @Singleton
    @Provides
    fun provideBatchHistoryDatabase(
        @ApplicationContext app: Context
    ): BatchHistoryDatabase = Room.databaseBuilder(
        context = app,
        klass = BatchHistoryDatabase::class.java,
        name = "batch-history-db"
    ).addMigrations(BatchHistoryDatabase.MIGRATION_1_2).build()

    @Singleton
    @Provides
    fun provideBatchHistoryDao(db: BatchHistoryDatabase) = db.batchHistoryDao()

    /** FEAT-06: DB riêng (không seed từ asset) — xem doc ở `WatermarkProfileEntity`. */
    @Singleton
    @Provides
    fun provideWatermarkProfileDatabase(
        @ApplicationContext app: Context
    ): WatermarkProfileDatabase = Room.databaseBuilder(
        context = app,
        klass = WatermarkProfileDatabase::class.java,
        name = "watermark-profile-db"
    ).addMigrations(WatermarkProfileDatabase.MIGRATION_1_2, WatermarkProfileDatabase.MIGRATION_2_3).build()

    @Singleton
    @Provides
    fun provideWatermarkProfileDao(db: WatermarkProfileDatabase) = db.watermarkProfileDao()

    /** IDEA-12: bảng chung [WatermarkProfileDatabase], không mở DB riêng. */
    @Singleton
    @Provides
    fun provideWatermarkStyleHistoryDao(db: WatermarkProfileDatabase) = db.watermarkStyleHistoryDao()

    /** IDEA-10: DB riêng (không seed từ asset) cho quản lý Người nhận (Recipient). */
    @Singleton
    @Provides
    fun provideRecipientDatabase(
        @ApplicationContext app: Context
    ): RecipientDatabase = Room.databaseBuilder(
        context = app,
        klass = RecipientDatabase::class.java,
        name = "recipient-db"
    ).build()

    @Singleton
    @Provides
    fun provideRecipientDao(db: RecipientDatabase) = db.recipientDao()
}
