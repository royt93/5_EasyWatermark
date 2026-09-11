package com.mckimquyen.watermark.ui

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * BUG-11: `compressImg` không được crash `NoSuchElementException` khi `imageInfoList` rỗng
 * (danh sách ảnh vừa bị xoá hết trước khi thao tác nén hoàn tất).
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelCompressImgRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        // Kích hoạt collect waterMark Flow -> LiveData (giống các test khác), đảm bảo
        // waterMark.value != null trước khi gọi compressImg (imageInfoList rỗng mới là điều kiện test).
        viewModel.waterMark.observeForever {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun compressImg_emptyImageList_doesNotCrash_andPostsFailure() {
        assertThat(viewModel.waterMark.value).isNotNull()

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var lastResultCode: String? = null
        viewModel.compressedResult.observeForever { result ->
            lastResultCode = result.code
        }

        viewModel.compressImg(activity)

        // compressImg chạy trên Dispatchers.IO (thread thật, ngoài tầm kiểm soát Robolectric
        // scheduler) rồi postValue về Main looper -> phải idle lặp lại để "bơm" Main looper
        // cho tới khi observer nhận được kết quả, thay vì chỉ idle 1 lần.
        val deadline = System.currentTimeMillis() + 5_000
        while (lastResultCode != MainViewModel.TYPE_COMPRESS_ERROR && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertThat(lastResultCode).isEqualTo(MainViewModel.TYPE_COMPRESS_ERROR)
    }
}
