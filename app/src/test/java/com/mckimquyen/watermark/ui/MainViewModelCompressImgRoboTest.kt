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
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.testutil.noopWatermarkStyleHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private val userDataStore = newTestUserDataStore(context)

    // BUG-FLAKY-2026-09-30: dispatcher ảo thời gian, chạy compressImg() đồng bộ trên thread test
    // khi advanceUntilIdle() -> hết polling idle()+sleep() đua với thread IO thật.
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        runBlocking {
            waterMarkDataStore.edit { it.clear() }
            // Chạy 1 lần collect() thật (đồng bộ trên thread test) trước khi bọc qua asLiveData() —
            // đảm bảo DataStore đã đọc xong TRƯỚC khi observeForever/idle() chạy, tránh race giữa
            // IO dispatcher (đọc DataStore) và main looper (chỉ idle() thứ ĐÃ có sẵn trong queue,
            // không đợi IO dispatcher — flaky ngẫu nhiên khi chạy chung full suite, xem BUG report).
            waterMarkRepo.waterMark.first()
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            styleHistoryRepo = noopWatermarkStyleHistoryRepository(),
            ioDispatcher = testDispatcher
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

        // testDispatcher ảo thời gian -> compressImg() chạy hết đồng bộ trên thread test ngay khi
        // advance, không còn thread IO thật nào để "đua" nữa. Chỉ cần idle() Main looper 1 lần
        // duy nhất để bơm postValue() đã enqueue vào Handler -> tất định 100%, hết polling/sleep.
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(lastResultCode).isEqualTo(MainViewModel.TYPE_COMPRESS_ERROR)
    }
}
