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
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-12: [MainViewModel.undo]/[MainViewModel.redo]/`canUndo`/`canRedo` phải delegate đúng xuống
 * [WaterMarkRepository] (logic thật đã test kỹ ở `WaterMarkRepositoryUndoRedoRoboTest`, ở đây chỉ
 * verify đường dây nối ViewModel → Repository → LiveData đúng).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelUndoRedoRoboTest {

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
        viewModel.canUndo.observeForever {}
        viewModel.canRedo.observeForever {}
        viewModel.waterMark.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun canUndo_falseInitially_trueAfterEdit() {
        assertThat(viewModel.canUndo.value).isFalse()

        viewModel.updateText("changed")
        awaitTrue { viewModel.canUndo.value == true }

        assertThat(viewModel.canUndo.value).isTrue()
    }

    @Test
    fun undo_restoresPreviousText() {
        // Bug tiềm ẩn phát hiện khi audit IDEA-07 (2026-09-24): đọc `.value` ngay sau
        // `observeForever` ở setUp() có thể vẫn null (Flow→LiveData chưa kịp emit lần đầu qua
        // DataStore đọc file thật, `idle()` không đảm bảo bắt kịp — cùng lý do `awaitTrue` bên
        // dưới tồn tại) → `original` = null nhầm thay vì text mặc định thật, undo sau đó so sánh
        // sai. Poll bằng `awaitTrue` giống các test khác trong file thay vì đọc `.value` trần.
        awaitTrue { viewModel.waterMark.value != null }
        val original = viewModel.waterMark.value?.text
        viewModel.updateText("changed")
        awaitTrue { viewModel.waterMark.value?.text == "changed" }

        viewModel.undo()
        awaitTrue { viewModel.waterMark.value?.text == original && viewModel.canRedo.value == true }

        assertThat(viewModel.waterMark.value?.text).isEqualTo(original)
        assertThat(viewModel.canRedo.value).isTrue()
    }

    @Test
    fun redo_afterUndo_reappliesChange() {
        viewModel.updateText("changed")
        awaitTrue { viewModel.waterMark.value?.text == "changed" }
        viewModel.undo()
        awaitTrue { viewModel.canRedo.value == true }

        viewModel.redo()
        awaitTrue { viewModel.waterMark.value?.text == "changed" }

        assertThat(viewModel.waterMark.value?.text).isEqualTo("changed")
    }

    /**
     * `waterMarkRepo.canUndo`/`canRedo` (StateFlow) → `asLiveData()` cầu nối qua thêm 1 vòng
     * dispatch trên main looper so với việc `undo()`/`redo()` (fire-and-forget qua `viewModelScope`)
     * hoàn tất — 1 lần `idle()` không đủ chắc chắn bắt kịp; poll ngắn giống
     * `awaitJobFinished`/`awaitSkipFlag` đã dùng ở nơi khác trong repo này.
     */
    private fun awaitTrue(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
    }
}
