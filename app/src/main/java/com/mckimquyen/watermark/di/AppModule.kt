package com.mckimquyen.watermark.di

import android.content.Context
import androidx.room.Room
import com.mckimquyen.watermark.data.db.AppDatabase
import com.mckimquyen.watermark.data.db.BatchHistoryDatabase
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
            e.printStackTrace()
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
    ).build()

    @Singleton
    @Provides
    fun provideBatchHistoryDao(db: BatchHistoryDatabase) = db.batchHistoryDao()
}
