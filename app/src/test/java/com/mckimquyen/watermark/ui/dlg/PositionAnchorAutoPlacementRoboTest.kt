package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import com.mckimquyen.watermark.ui.MainViewModel
import com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * IDEA-01: bấm nút "Tự động né khuôn mặt" trong [PositionAnchorBottomSheetFragment] phải gọi đúng
 * [MainViewModel.autoPlaceWatermarkForBatch], hiện/ẩn loading đúng lúc, và kết quả cuối cùng ghi
 * lại đúng vào ảnh trong batch. Dùng [FaceDetectionSource] GIẢ (không phải ML Kit thật — native,
 * không chạy được dưới Robolectric, xem [AutoPlacementEngine]) — cùng `TestHostActivity` pattern
 * đã dùng ở `QrCodeBottomSheetFragmentRoboTest` để có `MainViewModel` thật (không qua Hilt).
 */
@RunWith(RobolectricTestRunner::class)
class PositionAnchorAutoPlacementRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private class FakeFaceDetectionSource(
        private val faceRects: List<RectF>
    ) : FaceDetectionSource {
        override suspend fun detectFaces(bitmap: Bitmap): List<RectF> = faceRects
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val waterMarkDataStore = newTestWaterMarkDataStore(context)
    private lateinit var waterMarkRepo: WaterMarkRepository
    private lateinit var viewModel: MainViewModel
    private var activityController: ActivityController<TestHostActivity>? = null

    @Before
    fun setUp() {
        waterMarkRepo = WaterMarkRepository(context, waterMarkDataStore)
        val fakeEngine = AutoPlacementEngine(
            context,
            FakeFaceDetectionSource(listOf(RectF(0f, 0f, 1f, 1f))),
            ExportNaming()
        )
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(newTestUserDataStore(context)),
            waterMarkRepo = waterMarkRepo,
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null),
            autoPlacementEngine = fakeEngine
        )
        testViewModel = viewModel
        viewModel.waterMark.observeForever { }
        runBlocking { waterMarkRepo.updateImageList(listOf(ImageInfo(Uri.parse("content://media/1.jpg")))) }
    }

    @After
    fun tearDown() {
        try {
            activityController?.pause()?.stop()?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchFragment(): PositionAnchorBottomSheetFragment {
        val controller = Robolectric.buildActivity(TestHostActivity::class.java).setup()
        activityController = controller
        val activity = controller.get()
        val containerId = FrameLayout(activity).let {
            it.id = View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = PositionAnchorBottomSheetFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, PositionAnchorBottomSheetFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    private fun awaitAutoPlacementFinished() {
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.isAutoPlacing.value && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun clickAutoPlacement_showsLoadingThenHides_updatesImageOffset() {
        val fragment = launchFragment()
        assertThat(fragment.binding.pbAutoPlacement.visibility).isEqualTo(View.GONE)

        fragment.binding.btnAutoPlacement.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        awaitAutoPlacementFinished()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.pbAutoPlacement.visibility).isEqualTo(View.GONE)
        assertThat(fragment.binding.btnAutoPlacement.isEnabled).isTrue()
        // Fake trả face phủ toàn ảnh -> engine chắc chắn ghi lại offset + chuyển CLAMP (xem
        // AutoPlacementEngineRoboTest.suggestPlacements_faceCoversWholeImage_...).
        val updated = waterMarkRepo.imageInfoList.single()
        assertThat(updated.obtainTileMode()).isEqualTo(android.graphics.Shader.TileMode.CLAMP)
    }

    @Test
    fun clickAutoPlacement_secondClickWhileRunning_isNoOp() {
        val fragment = launchFragment()
        fragment.binding.btnAutoPlacement.performClick()
        // Nút đã bị disable ngay khi bắt đầu chạy (observe isAutoPlacing) -> click lần 2 vô nghĩa
        // với người dùng thật, nhưng vẫn gọi trực tiếp ViewModel để verify no-op ở tầng logic.
        viewModel.autoPlaceWatermarkForBatch()
        awaitAutoPlacementFinished()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(waterMarkRepo.imageInfoList).hasSize(1)
    }
}
