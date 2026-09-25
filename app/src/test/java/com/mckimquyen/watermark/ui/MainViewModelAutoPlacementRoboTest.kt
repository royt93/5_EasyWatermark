package com.mckimquyen.watermark.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.export.AutoPlacementEngine
import com.mckimquyen.watermark.export.ExportNaming
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-01: BUG THẬT phát hiện lúc smoke test trên TECNO BG6 — `autoPlaceWatermarkForBatch()` gọi
 * `waterMarkRepo.updateImageList()` (chỉ refresh dải thumbnail qua `imageInfoMapFlow`) nhưng canvas
 * chính (`WaterMarkImageView`, `MainActivity` observe `viewModel.selectedImage`) KHÔNG tự refresh
 * nếu `selectedImage` không được re-emit riêng — chạy xong mà màn hình không đổi gì. Test này verify
 * đúng `selectedImage` (không chỉ `waterMarkRepo.imageInfoList`) phản ánh offset mới.
 */
@RunWith(RobolectricTestRunner::class)
class MainViewModelAutoPlacementRoboTest {

    private class FakeFaceDetectionSource(
        private val faceRects: List<RectF>
    ) : FaceDetectionSource {
        override suspend fun detectFaces(bitmap: Bitmap): List<RectF> = faceRects
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun awaitAutoPlacementFinished(viewModel: MainViewModel) {
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.isAutoPlacing.value && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun autoPlaceWatermarkForBatch_selectedImageLiveData_reflectsNewOffset_notJustRepoList() {
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val fakeEngine = AutoPlacementEngine(
            context,
            FakeFaceDetectionSource(listOf(RectF(0f, 0f, 1f, 1f))),
            ExportNaming()
        )
        val viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(newTestUserDataStore(context)),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            autoPlacementEngine = fakeEngine
        )
        runBlocking { waterMarkRepo.waterMark.first() } // warm-up: xem MainViewModelCompressImgRoboTest comment
        viewModel.waterMark.observeForever { }
        viewModel.selectedImage.observeForever { }
        val uri = Uri.parse("content://media/1.jpg")
        runBlocking {
            waterMarkRepo.updateImageList(listOf(ImageInfo(uri)))
            waterMarkRepo.select(uri)
        }
        shadowOf(Looper.getMainLooper()).idle()
        // Ảnh đang mở trong editor mặc định offset 0.5/0.5 (chưa từng chỉnh).
        assertThat(viewModel.selectedImage.value?.offsetX).isEqualTo(0.5f)

        viewModel.autoPlaceWatermarkForBatch()
        awaitAutoPlacementFinished(viewModel)
        shadowOf(Looper.getMainLooper()).idle()

        // Đây chính là field canvas (WaterMarkImageView) thật sự đọc — phải đổi, không chỉ
        // waterMarkRepo.imageInfoList đổi ngầm bên trong.
        val selected = viewModel.selectedImage.value
        assertThat(selected?.offsetX == 0.5f && selected?.offsetY == 0.5f).isFalse()
        assertThat(selected?.obtainTileMode()).isEqualTo(android.graphics.Shader.TileMode.CLAMP)
    }
}
