package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * FEAT-07: [MainViewModel.generateExportPreview]/[MainViewModel.estimateExportOutput] — wrapper
 * mỏng cho grid xem trước batch export. Phần render watermark thật uỷ quyền cho
 * [com.mckimquyen.watermark.export.BatchExportEngine] (đã test riêng ở
 * `BatchExportEnginePreviewRoboTest`) — test này chỉ verify hành vi của lớp ViewModel: đọc đúng
 * cấu hình hiện tại (`waterMark`/`outputFormat`/`compressLevel`/`maxOutputLongEdge`) và guard khi
 * cấu hình chưa sẵn sàng.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelExportPreviewRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newViewModel(): MainViewModel {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        return MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    @Test
    fun generateExportPreview_waterMarkNotObservedYet_returnsNullWithoutCrashing() = runBlocking {
        // `waterMark` là LiveData.asLiveData() từ Flow — chưa có observer thì `.value` giữ null,
        // generateExportPreview() phải guard sớm thay vì NPE khi đọc config.
        val viewModel = newViewModel()

        val result = viewModel.generateExportPreview(
            context.contentResolver,
            ImageInfo(Uri.parse("content://does.not.exist/1.jpg")),
            0
        )

        assertThat(result).isNull()
    }

    @Test
    fun estimateExportOutput_defaultPrefs_originalResizeKeepsRawDimensions() {
        val viewModel = newViewModel()

        // Mặc định UserPreferences: maxOutputLongEdge = 0 (Original) → giữ nguyên kích thước gốc.
        val (dimensions, bytes) = viewModel.estimateExportOutput(4000, 3000)

        assertThat(dimensions).isEqualTo(4000 to 3000)
        assertThat(bytes).isGreaterThan(0L)
    }

    @Test
    fun estimateExportOutput_afterChangingResizeAndFormat_reflectsNewSettings() {
        val viewModel = newViewModel()
        viewModel.saveOutput(Bitmap.CompressFormat.PNG, 100)
        viewModel.saveMaxLongEdge(1080)

        // saveOutput/saveMaxLongEdge ghi DataStore bất đồng bộ qua viewModelScope.launch — poll
        // thay vì 1 lần idle() (giống pattern awaitX() đã dùng ở các test bất đồng bộ khác).
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && viewModel.maxOutputLongEdge != 1080) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        val (dimensions, bytes) = viewModel.estimateExportOutput(4000, 3000)

        // cạnh dài 4000 -> 1080 (scale 0.27), tỉ lệ giữ nguyên.
        assertThat(dimensions.first).isEqualTo(1080)
        assertThat(dimensions.second).isEqualTo(810)
        assertThat(bytes).isGreaterThan(0L)
    }
}
