package com.mckimquyen.watermark.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-18: [MainViewModel.compareReveal]/[MainViewModel.updateCompareReveal] — state THUẦN UI
 * cho slider so sánh trước/sau, KHÔNG được đụng tới `waterMarkRepo`/DataStore (đây không phải 1
 * thay đổi cấu hình watermark thật).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelCompareRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        viewModel.compareReveal.observeForever {}
    }

    @Test
    fun compareReveal_defaultsToOne() {
        assertThat(viewModel.compareReveal.value).isEqualTo(1f)
    }

    @Test
    fun updateCompareReveal_setsExactValue() {
        viewModel.updateCompareReveal(0.3f)

        assertThat(viewModel.compareReveal.value).isEqualTo(0.3f)
    }

    @Test
    fun updateCompareReveal_coercesOutOfRangeValues() {
        viewModel.updateCompareReveal(1.5f)
        assertThat(viewModel.compareReveal.value).isEqualTo(1f)

        viewModel.updateCompareReveal(-0.5f)
        assertThat(viewModel.compareReveal.value).isEqualTo(0f)
    }

    @Test
    fun updateCompareReveal_doesNotTouchWaterMarkDataStore() = runBlocking {
        val originalText = waterMarkRepo.waterMark.first().text

        viewModel.updateCompareReveal(0.2f)

        assertThat(waterMarkRepo.waterMark.first().text).isEqualTo(originalText)
        assertThat(waterMarkRepo.canUndo.value).isFalse()
    }
}
