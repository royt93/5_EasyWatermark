package com.mckimquyen.watermark.ui

import android.content.ClipData
import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.widget.LaunchView
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem
import java.io.File

/**
 * FEAT-21: Dán ảnh trực tiếp từ Clipboard để watermark nhanh.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityPasteClipboardRoboTest {

    private lateinit var testTempDir: File

    @After
    fun tearDown() {
        if (this::testTempDir.isInitialized) {
            testTempDir.deleteRecursively()
        }
    }

    @Test
    fun launchView_displaysPasteFromClipboardCard() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView

        assertThat(launchView).isNotNull()
        assertThat(launchView.ivPasteFromClipboard).isNotNull()
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)
    }

    @Test
    fun performPasteFromClipboard_withValidImageInClipboard_transitionsToEditorAndLoadsImage() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        testTempDir = File(activity.cacheDir, "test_paste_${System.currentTimeMillis()}").apply { mkdirs() }
        val sampleImage = File(testTempDir, "copied_screenshot.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val imageUri = Uri.fromFile(sampleImage)
        val clipData = ClipData.newUri(activity.contentResolver, "Screenshot", imageUri)

        // Thực hiện dán từ clipboard
        activity.performPasteFromClipboard(clipData)
        var attempts = 0
        while (viewModel.imageList.value?.first.isNullOrEmpty() && attempts < 40) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            attempts++
        }

        // Kiểm tra đã nạp ảnh vào ViewModel
        val currentImages = viewModel.imageList.value?.first
        assertThat(currentImages).isNotNull()
        assertThat(currentImages!!.map { it.uri }).contains(imageUri)

        // Kiểm tra chuyển sang Editor Mode
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.Editor)
    }

    @Test
    fun performPasteFromClipboard_withTextOnlyInClipboard_showsToastAndRemainsInLaunchMode() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val launchView = contentRoot.getChildAt(0) as LaunchView
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val textClip = ClipData.newPlainText("text_label", "Just some random copied text")

        // Thực hiện dán với clipboard chỉ có text
        activity.performPasteFromClipboard(textClip)
        shadowOf(Looper.getMainLooper()).idle()

        // ViewModel không có ảnh mới, vẫn ở LaunchMode
        assertThat(viewModel.imageList.value?.first).isEmpty()
        assertThat(launchView.mode).isEqualTo(LaunchView.ViewMode.LaunchMode)

        // Co thong bao Snackbar (M3, thay Toast) bao khong tim thay anh
        val snackbarText = activity.window.decorView
            .findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
        assertThat(snackbarText).isNotNull()
        assertThat(snackbarText.text.toString()).isEqualTo(activity.getString(R.string.clipboard_no_image))
    }

    @Test
    fun onOptionsItemSelected_actionPaste_withValidImage_pastesImage() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()

        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]
        testTempDir = File(activity.cacheDir, "test_menu_paste_${System.currentTimeMillis()}").apply { mkdirs() }
        val sampleImage = File(testTempDir, "menu_photo.jpg").apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }
        val imageUri = Uri.fromFile(sampleImage)
        val clipData = ClipData.newUri(activity.contentResolver, "Photo", imageUri)

        // Gán vào clipboard hệ thống của Context
        val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(clipData)

        // Bấm menu Action Paste
        val menuItem = RoboMenuItem(R.id.actionPaste)
        val consumed = activity.onOptionsItemSelected(menuItem)
        var attempts = 0
        while (viewModel.imageList.value?.first.isNullOrEmpty() && attempts < 40) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            attempts++
        }

        assertThat(consumed).isTrue()
        val currentImages = viewModel.imageList.value?.first
        assertThat(currentImages).isNotNull()
        assertThat(currentImages!!.map { it.uri }).contains(imageUri)
    }
}
