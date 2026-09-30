package com.mckimquyen.watermark.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-FLAKY-2026-09-30: unit test thuần cho [TestDataStoreModule] — không cần Activity/Fragment/
 * Hilt component, chỉ gọi thẳng 2 hàm `@Provides` với 1 [Context] Robolectric để kiểm chứng mỗi
 * lần gọi sinh ra 1 file/instance HOÀN TOÀN RIÊNG (không cache tĩnh, không đụng singleton sản
 * xuất `context.userDataStore`/`waterMarkDataStore`) — đúng thuộc tính module PHẢI có để
 * [SaveImageBSDialogFragmentInvisibleRoboTest] và anh em cô lập được DataStore.
 */
@RunWith(RobolectricTestRunner::class)
class TestDataStoreModuleRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = booleanPreferencesKey("k")

    @Test
    fun userDataStore_moiLanGoi_traVeInstanceRieng_khongChiaSeState() = runBlocking {
        val first = TestDataStoreModule.userDataStore(context)
        val second = TestDataStoreModule.userDataStore(context)

        assertThat(first).isNotSameInstanceAs(second)

        first.edit { it[key] = true }
        assertThat(second.data.first()[key]).isNull()
    }

    @Test
    fun waterMarkDataStore_moiLanGoi_traVeInstanceRieng_khongChiaSeState() = runBlocking {
        val first = TestDataStoreModule.waterMarkDataStore(context)
        val second = TestDataStoreModule.waterMarkDataStore(context)

        assertThat(first).isNotSameInstanceAs(second)

        first.edit { it[key] = true }
        assertThat(second.data.first()[key]).isNull()
    }

    @Test
    fun userDataStore_khongTrungInstanceVoiSingletonSanXuat() = runBlocking {
        val isolated = TestDataStoreModule.userDataStore(context)

        assertThat(isolated).isNotSameInstanceAs(context.userDataStore)
    }
}
