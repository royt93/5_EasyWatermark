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
 * BUG-12: `removeImage` không được crash IndexOutOfBoundsException khi `imageInfo`
 * là null hoặc không còn tồn tại trong list (đã bị xoá bởi thao tác khác trước đó).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelRemoveImageRoboTest {

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

    /**
     * ENH-27: Batch 3 ảnh [A, B, C], đang chọn B (pos = 1).
     * Xoá ảnh CUỐI [C] (removePos = 2).
     * selectedPos PHẢI giữ nguyên là 1 (vẫn chọn B), không được nhảy lùi về 0 (A).
     */
    @Test
    fun removeImage_batch3_selectMiddle_removeLast_preservesSelectedPos() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        val c = imageInfo(Uri.parse("content://media/C"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b, c)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(c, curSelectedPos = 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a, b).inOrder()
        assertThat(viewModel.nextSelectedPos).isEqualTo(1)
    }

    /**
     * ENH-27: Batch 3 ảnh [A, B, C], đang chọn B (pos = 1).
     * Xoá ảnh ĐẦU [A] (removePos = 0).
     * selectedPos phải giảm xuống 0 (vì B giờ đã là index 0).
     */
    @Test
    fun removeImage_batch3_selectMiddle_removeFirst_decrementsSelectedPos() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        val c = imageInfo(Uri.parse("content://media/C"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b, c)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(a, curSelectedPos = 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(b, c).inOrder()
        assertThat(viewModel.nextSelectedPos).isEqualTo(0)
    }

    /**
     * ENH-27: Batch 3 ảnh [A, B, C], đang chọn C (pos = 2, phần tử cuối).
     * Xoá chính ảnh C (removePos = 2).
     * selectedPos phải clamp về phần tử cuối mới là B (index 1).
     */
    @Test
    fun removeImage_batch3_selectLast_removeLast_clampsToNewLastPos() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        val c = imageInfo(Uri.parse("content://media/C"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b, c)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(c, curSelectedPos = 2)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a, b).inOrder()
        assertThat(viewModel.nextSelectedPos).isEqualTo(1)
    }

    /**
     * ENH-27: Batch 3 ảnh [A, B, C], đang chọn A (pos = 0).
     * Xoá ảnh CUỐI [C] (removePos = 2).
     * selectedPos giữ nguyên 0 (A).
     */
    @Test
    fun removeImage_batch3_selectFirst_removeLast_preservesSelectedPos() {
        val a = imageInfo(Uri.parse("content://media/A"))
        val b = imageInfo(Uri.parse("content://media/B"))
        val c = imageInfo(Uri.parse("content://media/C"))
        runBlocking { waterMarkRepo.updateImageList(listOf(a, b, c)) }
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.removeImage(c, curSelectedPos = 0)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(viewModel.imageList.value?.first).containsExactly(a, b).inOrder()
        assertThat(viewModel.nextSelectedPos).isEqualTo(0)
    }
}
