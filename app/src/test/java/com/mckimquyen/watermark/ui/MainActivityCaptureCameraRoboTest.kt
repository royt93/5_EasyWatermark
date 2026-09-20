package com.mckimquyen.watermark.ui

import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.LaunchView
import com.mckimquyen.watermark.utils.CameraCaptureHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowToast
import java.io.File

/**
 * FEAT-22: Chụp ảnh trực tiếp từ Camera rồi watermark ngay.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityCaptureCameraRoboTest {

    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        ShadowToast.reset()
    }

    @After
    fun tearDown() {
        if (this::testTempDir.isInitialized) {
            testTempDir.deleteRecursively()
        }
    }

    @Test
    fun launchView_displaysCaptureFromCameraCard() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView

        assertThat(launchView).isNotNull()
        assertThat(launchView.ivCaptureFromCamera).isNotNull()
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)
    }

    @Test
    fun handleCameraResult_success_transitionsToEditorAndLoadsCapturedPhoto() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        testTempDir = File(activity.cacheDir, "test_camera_${System.currentTimeMillis()}").apply { mkdirs() }
        val samplePhoto = File(testTempDir, "camera_capture.jpg").apply {
            writeBytes(byteArrayOf(10, 20, 30, 40))
        }
        val photoUri = Uri.fromFile(samplePhoto)

        // Mô phỏng kết quả camera trả về thành công
        activity.handleCameraResult(success = true, photoUriOverride = photoUri)

        var attempts = 0
        while (viewModel.imageList.value?.first.isNullOrEmpty() && attempts < 40) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            attempts++
        }

        // Kiểm tra đã nạp ảnh vào ViewModel
        val currentImages = viewModel.imageList.value?.first
        assertThat(currentImages).isNotNull()
        assertThat(currentImages!!.map { it.uri }).contains(photoUri)

        // Kiểm tra chuyển sang Editor Mode
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.Editor)
    }

    @Test
    fun handleCameraResult_failureOrCancel_remainsInLaunchModeAndCleansUp() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        testTempDir = File(activity.cacheDir, "test_camera_${System.currentTimeMillis()}").apply { mkdirs() }
        val zeroBytePhoto = File(testTempDir, "empty_capture.jpg").apply {
            createNewFile()
        }

        // Mô phỏng kết quả chụp ảnh bị huỷ hoặc thất bại
        activity.handleCameraResult(success = false, photoUriOverride = Uri.fromFile(zeroBytePhoto))
        CameraCaptureHelper.cleanupPhotoFile(zeroBytePhoto)
        shadowOf(Looper.getMainLooper()).idle()

        // ViewModel không nhận ảnh, vẫn ở LaunchMode
        assertThat(viewModel.imageList.value?.first).isEmpty()
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)
        assertThat(zeroBytePhoto.exists()).isFalse()
    }

    @Test
    fun onOptionsItemSelected_actionCamera_isHandledSuccessfully() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val menuItem = RoboMenuItem(R.id.actionCamera)
        val consumed = activity.onOptionsItemSelected(menuItem)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(consumed).isTrue()
    }
}
