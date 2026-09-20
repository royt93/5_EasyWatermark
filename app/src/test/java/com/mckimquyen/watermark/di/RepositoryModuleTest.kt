package com.mckimquyen.watermark.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ENH-23: Kiểm thử RepositoryModule đảm bảo provideMemorySettingRepository()
 * cung cấp đúng instance mà không còn qualifier @Named("WaterMarkPreferences") nhầm lẫn.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryModuleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testProvideMemorySettingRepository() {
        val repo = RepositoryModule.provideMemorySettingRepository()
        assertThat(repo).isNotNull()
    }

    @Test
    fun testProvideUserRepository() {
        val dataStore = newTestUserDataStore(context)
        val repo = RepositoryModule.provideUserRepository(dataStore)
        assertThat(repo).isNotNull()
    }

    @Test
    fun testProvideWaterMarkRepository() {
        val dataStore = newTestWaterMarkDataStore(context)
        val repo = RepositoryModule.provideWaterMarkRepository(context, dataStore)
        assertThat(repo).isNotNull()
    }

    @Test
    fun testProvideTemplateRepository() {
        val repo = RepositoryModule.provideTemplateRepository(null)
        assertThat(repo).isNotNull()
    }
}
