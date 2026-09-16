package com.mckimquyen.watermark.ui

import android.content.Context
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
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Unit test (Robolectric): [MainViewModel.generateOutputName] — FEAT-02, naming template cho
 * file xuất tái dùng đúng [MainViewModel.resolveTextTokens] (đã dùng cho text watermark).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelGenerateOutputNameRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)

    @Before
    fun setUp() {
        runBlocking {
            userDataStore.edit { it.clear() }
            waterMarkDataStore.edit { it.clear() }
        }
    }

    /** [MainViewModel.userPreferences] là StateFlow(Eagerly) — idle main looper để nó kịp collect giá trị đã lưu trước khi test đọc. */
    private fun freshViewModel(): MainViewModel {
        val viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    private fun imageInfo(uri: Uri) = ImageInfo(uri)

    @Test
    fun emptyPattern_usesLegacyDefaultName() {
        val viewModel = freshViewModel()
        val info = imageInfo(Uri.parse("content://media/1"))

        val name = viewModel.generateOutputName(context.contentResolver, info, 0)

        assertThat(name).matches("""ewm_\d+\.jpg""")
    }

    @Test
    fun blankPatternWithOnlyWhitespace_treatedAsEmpty() {
        runBlocking { UserConfigRepository(userDataStore).updateOutputNamePattern("   ") }
        val viewModel = freshViewModel()
        val info = imageInfo(Uri.parse("content://media/1"))

        val name = viewModel.generateOutputName(context.contentResolver, info, 0)

        assertThat(name).matches("""ewm_\d+\.jpg""")
    }

    @Test
    fun patternWithTokens_resolvesPerImageAndAppendsExtension() {
        runBlocking { UserConfigRepository(userDataStore).updateOutputNamePattern("{filename}_wm_{seq}") }
        val viewModel = freshViewModel()
        val info = imageInfo(Uri.parse("content://media/external/images/media/holiday.jpg"))

        // index=2 -> {seq} = "3" (1-based, giống resolveTextTokens dùng cho text watermark)
        val name = viewModel.generateOutputName(context.contentResolver, info, 2)

        assertThat(name).isEqualTo("holiday_wm_3.jpg")
    }

    @Test
    fun patternWithoutTokens_staticNameStillGetsExtension() {
        runBlocking { UserConfigRepository(userDataStore).updateOutputNamePattern("brand_export") }
        val viewModel = freshViewModel()
        val info = imageInfo(Uri.parse("content://media/1"))

        val name = viewModel.generateOutputName(context.contentResolver, info, 0)

        assertThat(name).isEqualTo("brand_export.jpg")
    }

    @Test
    fun differentImages_sameBatchPattern_produceDifferentNames_whenSeqUsed() {
        runBlocking { UserConfigRepository(userDataStore).updateOutputNamePattern("IMG_{seq}") }
        val viewModel = freshViewModel()
        val infoA = imageInfo(Uri.parse("content://media/A"))
        val infoB = imageInfo(Uri.parse("content://media/B"))

        val nameA = viewModel.generateOutputName(context.contentResolver, infoA, 0)
        val nameB = viewModel.generateOutputName(context.contentResolver, infoB, 1)

        assertThat(nameA).isEqualTo("IMG_1.jpg")
        assertThat(nameB).isEqualTo("IMG_2.jpg")
        assertThat(nameA).isNotEqualTo(nameB)
    }
}
