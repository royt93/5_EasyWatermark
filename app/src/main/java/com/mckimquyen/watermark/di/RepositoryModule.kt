package com.mckimquyen.watermark.di

import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import com.mckimquyen.watermark.utils.facedetection.MlKitFaceDetectionSource
import com.mckimquyen.watermark.utils.textdetection.MlKitSensitiveTextSource
import com.mckimquyen.watermark.utils.textdetection.SensitiveTextSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * BUG-AUDIT-2026-09-29: đã xoá `provideUserRepository`/`provideWaterMarkRepository`
 * (`@Named("UserPreferences")`/`@Named("WaterMarkPreferences")`) — dead code, không nơi nào trong
 * repo request 2 binding `@Named(...)` này (2 repo tương ứng được cung cấp qua constructor Hilt
 * @Inject bình thường ở nơi khác, không qua module này).
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMemorySettingRepository(): MemorySettingRepo {
        return MemorySettingRepo()
    }

    @Provides
    fun provideTemplateRepository(dao: TemplateDao?): TemplateRepository {
        return TemplateRepository(dao)
    }

    /** IDEA-01. */
    @Provides
    @Singleton
    fun provideFaceDetectionSource(impl: MlKitFaceDetectionSource): FaceDetectionSource = impl

    /** IDEA-14. */
    @Provides
    @Singleton
    fun provideSensitiveTextSource(impl: MlKitSensitiveTextSource): SensitiveTextSource = impl
}
