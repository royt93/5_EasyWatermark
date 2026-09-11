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
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-12: `removeImage` không được crash IndexOutOfBoundsException khi `imageInfo`
 * là null hoặc không còn tồn tại trong list (đã bị xoá bởi thao tác khác trước đó).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelRemoveImageRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        viewModel.imageList.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun imageInfo(uri: Uri) = ImageInfo(uri)

    @Test
    fun removeImage_nullInfo_doesNotCrash() {
        val a = imageInfo(Uri.parse("content://media/A"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(null, curSelectedPos = 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a)
    }

    @Test
    fun removeImage_infoAlreadyRemoved_doesNotCrash() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b)) }
        shadowOf(Looper.getMainLooper()).idle()

        // b không còn trong list thật (giả lập race: đã bị xoá bởi thao tác khác trước đó).
        runBlocking { waterMarkRepo.updateImageList(listOf(a)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(b, curSelectedPos = 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a)
    }

    @Test
    fun removeImage_existingInfo_removesIt() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(b, curSelectedPos = 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a)
    }
}
