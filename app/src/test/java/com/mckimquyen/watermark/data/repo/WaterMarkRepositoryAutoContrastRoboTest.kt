package com.mckimquyen.watermark.data.repo

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-06: [WaterMarkRepository.updateAutoContrastEnabled] — toggle bật/tắt tự động tương phản,
 * mặc định phải tắt (không đổi hành vi render cũ cho user chưa từng bật). DataStore cô lập — xem
 * `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkRepositoryAutoContrastRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var repo: WaterMarkRepository

    @Before
    fun setUp() {
        repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
    }

    @Test
    fun defaultValue_isDisabled() = runBlocking {
        assertThat(repo.waterMark.first().autoContrastEnabled).isFalse()
    }

    @Test
    fun updateAutoContrastEnabled_true_thenReadBack_isTrue() = runBlocking {
        repo.updateAutoContrastEnabled(true)

        assertThat(repo.waterMark.first().autoContrastEnabled).isTrue()
    }

    @Test
    fun updateAutoContrastEnabled_toggleBackToFalse_readBack_isFalse() = runBlocking {
        repo.updateAutoContrastEnabled(true)
        repo.updateAutoContrastEnabled(false)

        assertThat(repo.waterMark.first().autoContrastEnabled).isFalse()
    }
}
