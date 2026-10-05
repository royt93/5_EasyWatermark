package com.mckimquyen.watermark.ui

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-68 (rủi ro đi kèm): `uiStateFlow` chuyển StateFlow → SharedFlow one-shot. `addTemplate()` phát
 * `UiState.DatabaseError` khi DAO null; collector đang STARTED (màn danh sách template) phải NHẬN được,
 * và collector tới SAU (không replay) KHÔNG được nhận lại sự kiện cũ.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelDatabaseErrorEventRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: MainViewModel
    private val jobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        val waterMarkStore = newTestWaterMarkDataStore(context)
        val userStore = newTestUserDataStore(context)
        runBlocking {
            waterMarkStore.edit { it.clear() }
            userStore.edit { it.clear() }
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null), // DAO null → checkIfIsDaoNull() == true
            styleHistoryRepo = noopWatermarkStyleHistoryRepository()
        )
    }

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
    }

    private fun collectInto(sink: MutableList<UiState>) {
        jobs += CoroutineScope(Dispatchers.Unconfined).launch { viewModel.uiStateFlow.collect { sink.add(it) } }
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun addTemplate_daoNull_activeCollectorReceivesDatabaseError() {
        val received = java.util.Collections.synchronizedList(mutableListOf<UiState>())
        collectInto(received)

        viewModel.addTemplate("nội dung")
        idleUntil { received.any { it is UiState.DatabaseError } }

        assertThat(received.any { it is UiState.DatabaseError }).isTrue()
    }

    @Test
    fun databaseError_lateCollector_doesNotReplayOldEvent() {
        val early = java.util.Collections.synchronizedList(mutableListOf<UiState>())
        collectInto(early)
        viewModel.addTemplate("nội dung")
        idleUntil { early.any { it is UiState.DatabaseError } }
        assertThat(early.any { it is UiState.DatabaseError }).isTrue()

        val late = java.util.Collections.synchronizedList(mutableListOf<UiState>())
        collectInto(late)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(late).isEmpty() // đúng ý đồ one-shot: không replay lỗi cũ cho collector mới
    }
}
