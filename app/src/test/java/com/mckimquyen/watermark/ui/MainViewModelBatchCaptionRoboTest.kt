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

/** FEAT-13: [MainViewModel.updateBatchCaptions] uỷ quyền cho [WaterMarkRepository.updateImageCaptions]. */
@RunWith(RobolectricTestRunner::class)
class MainViewModelBatchCaptionRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking { waterMarkDataStore.edit { it.clear() } }
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        viewModel.imageList.observeForever {}
    }

    private fun awaitCaptions(expected: List<String?>, timeoutMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (viewModel.imageList.value?.first?.map { it.caption } == expected) return
            Thread.sleep(20)
        }
    }

    @Test
    fun updateBatchCaptions_appliesByIndex_toCurrentImageList() {
        runBlocking {
            waterMarkRepo.updateImageList(
                listOf(
                    ImageInfo(Uri.parse("content://media/a")),
                    ImageInfo(Uri.parse("content://media/b"))
                )
            )
        }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.updateBatchCaptions(listOf("Cap A", "Cap B"))
        awaitCaptions(listOf("Cap A", "Cap B"))

        assertThat(viewModel.imageList.value?.first?.map { it.caption }).containsExactly("Cap A", "Cap B").inOrder()
    }

    @Test
    fun updateBatchCaptions_allNull_clearsCaptions() {
        runBlocking {
            waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/a"))))
        }
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.updateBatchCaptions(listOf("Cap A"))
        awaitCaptions(listOf("Cap A"))

        viewModel.updateBatchCaptions(listOf(null))
        awaitCaptions(listOf(null))

        assertThat(viewModel.imageList.value?.first?.first()?.caption).isNull()
    }
}
