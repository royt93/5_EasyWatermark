package com.mckimquyen.watermark.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.ui.about.OpenSourceActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration test (instrumented, IO thật trên thiết bị): BUG-11 — `compressImg` phải xoá file
 * tạm (`easy_water_mark_*`) trong `context.cacheDir` ở MỌI đường thoát (thành công lẫn khi
 * `cancelCompressJob()` được gọi), không chỉ khi mọi thứ chạy suôn sẻ.
 *
 * Dùng ảnh JPEG thật + `Compressor` thật (không mock) — đúng tinh thần "IO thật" của
 * `BitmapUtilsContextThreadingIntegrationTest`/`WaterMarkRepositoryIntegrationTest` đã có sẵn.
 * Host bằng `OpenSourceActivity` (không load Ad/network khi mở, xem `doc/feat.md`) chỉ để lấy
 * `Activity` thật cho tham số `compressImg(activity: Activity)` — bản thân màn hình không liên
 * quan tính năng đang test.
 *
 * Lưu ý: thân coroutine của `compressImg` hoàn toàn là I/O đồng bộ (không có suspension point nội
 * bộ), nên `cancelCompressJob()` không thực sự ngắt ngang thao tác đang chạy (cooperative
 * cancellation của Kotlin coroutine chỉ có tác dụng tại điểm suspend) — coroutine vẫn chạy hết,
 * và `finally` vẫn dọn file tạm như đường thành công. Test dưới xác nhận đúng hành vi thực tế đó:
 * gọi cancel không tạo ra file rác, dù cơ chế là "chạy xong rồi dọn" chứ không phải "ngắt giữa
 * chừng" — đúng tinh thần Acceptance Criteria ("không để lại file tạm"), không phải lời hứa ngắt
 * ngang tức thời.
 */
@RunWith(AndroidJUnit4::class)
class MainViewModelCompressImgIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel
    private var sourceFile: File? = null

    @Before
    fun setUp() {
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
        waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore)
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @After
    fun tearDown() {
        sourceFile?.delete()
        cacheTmpFiles().forEach { it.delete() }
        runBlocking { context.waterMarkDataStore.edit { it.clear() } }
    }

    private fun createRealJpeg(): Uri {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)
        val file = File.createTempFile("wm_compress_src", ".jpg", context.cacheDir)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        sourceFile = file
        return Uri.fromFile(file)
    }

    private fun cacheTmpFiles(): List<File> =
        context.cacheDir.listFiles { f -> f.name.startsWith("easy_water_mark_") }?.toList().orEmpty()

    /** Đợi `waterMark` LiveData emit giá trị đầu (bắt buộc để qua guard mới của BUG-11). */
    private fun awaitWaterMarkReady() {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.waterMark.observeForever { latch.countDown() }
        }
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    }

    @Test
    fun compressImg_realImage_deletesTempInputFile_afterSuccess() {
        val uri = createRealJpeg()
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(uri))) }
        awaitWaterMarkReady()

        ActivityScenario.launch(OpenSourceActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> viewModel.compressImg(activity) }

            val deadline = System.currentTimeMillis() + 10_000
            while (viewModel.compressedResult.value?.code != MainViewModel.TYPE_COMPRESS_OK &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50)
            }
        }

        assertThat(viewModel.compressedResult.value?.code).isEqualTo(MainViewModel.TYPE_COMPRESS_OK)
        assertThat(cacheTmpFiles()).isEmpty()
    }

    @Test
    fun compressImg_cancelCalledRightAfterLaunch_neverLeavesTempFile() {
        val uri = createRealJpeg()
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(uri))) }
        awaitWaterMarkReady()

        ActivityScenario.launch(OpenSourceActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                viewModel.compressImg(activity)
                viewModel.cancelCompressJob()
            }

            val deadline = System.currentTimeMillis() + 10_000
            while (viewModel.compressedResult.value?.code != MainViewModel.TYPE_COMPRESS_OK &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50)
            }
        }

        assertThat(cacheTmpFiles()).isEmpty()
    }

    @Test
    fun compressImg_emptyImageList_doesNotCrash_onRealDevice() {
        awaitWaterMarkReady()

        ActivityScenario.launch(OpenSourceActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> viewModel.compressImg(activity) }

            val deadline = System.currentTimeMillis() + 5_000
            while (viewModel.compressedResult.value?.code != MainViewModel.TYPE_COMPRESS_ERROR &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(50)
            }
        }

        assertThat(viewModel.compressedResult.value?.code).isEqualTo(MainViewModel.TYPE_COMPRESS_ERROR)
        assertThat(cacheTmpFiles()).isEmpty()
    }
}
