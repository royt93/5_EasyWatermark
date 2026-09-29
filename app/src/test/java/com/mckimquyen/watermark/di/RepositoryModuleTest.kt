package com.mckimquyen.watermark.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-23: Kiểm thử RepositoryModule đảm bảo provideMemorySettingRepository()
 * cung cấp đúng instance mà không còn qualifier @Named("WaterMarkPreferences") nhầm lẫn.
 *
 * BUG-AUDIT-2026-09-29: đã xoá testProvideUserRepository/testProvideWaterMarkRepository theo sau
 * khi xoá 2 binding chết provideUserRepository/provideWaterMarkRepository ở RepositoryModule
 * (không nơi nào trong repo request 2 binding @Named(...) này).
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryModuleTest {

    @Test
    fun testProvideMemorySettingRepository() {
        val repo = RepositoryModule.provideMemorySettingRepository()
        assertThat(repo).isNotNull()
    }

    @Test
    fun testProvideTemplateRepository() {
        val repo = RepositoryModule.provideTemplateRepository(null)
        assertThat(repo).isNotNull()
    }
}
