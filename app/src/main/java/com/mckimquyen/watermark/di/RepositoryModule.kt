package com.mckimquyen.watermark.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Named("UserPreferences")
    @Provides
    @Singleton
    fun provideUserRepository(dataStore: DataStore<Preferences>): UserConfigRepository {
        return UserConfigRepository(dataStore)
    }

    @Named("WaterMarkPreferences")
    @Provides
    @Singleton
    fun provideWaterMarkRepository(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>
    ): WaterMarkRepository {
        return WaterMarkRepository(context, dataStore)
    }

    @Named("WaterMarkPreferences")
    @Provides
    @Singleton
    fun provideMemorySettingRepository(): MemorySettingRepo {
        return MemorySettingRepo()
    }

    @Provides
    fun provideTemplateRepository(dao: TemplateDao?): TemplateRepository {
        return TemplateRepository(dao)
    }
}
