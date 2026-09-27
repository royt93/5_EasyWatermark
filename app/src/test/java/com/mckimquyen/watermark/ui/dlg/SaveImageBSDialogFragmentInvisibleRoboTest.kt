package com.mckimquyen.watermark.ui.dlg

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.ui.MainActivity
import com.mckimquyen.watermark.ui.MainViewModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-02 widget test: switch "Watermark vô hình" trong dialog lưu ảnh.
 * Theo khuôn `SaveImageBSDialogFragmentAuthenticityRoboTest` (IDEA-03).
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentInvisibleRoboTest {

    private fun setupDialog(): Pair<MainActivity, SaveImageBSDialogFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val dialogFragment = SaveImageBSDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(dialogFragment, "SaveImageBSDialogFragmentInvisibleTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun swInvisibleWatermark_isMaterialSwitch_andReflectsViewModelState() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swInvisibleWatermark
        assertThat(switch).isInstanceOf(MaterialSwitch::class.java)
        assertThat(switch.isChecked).isEqualTo(viewModel.invisibleWatermark)
        assertThat(switch.contentDescription.toString())
            .isEqualTo(activity.getString(R.string.invisible_watermark_title))
    }

    @Test
    fun swInvisibleWatermark_toggle_luuVaoViewModel() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swInvisibleWatermark
        val initialState = viewModel.invisibleWatermark

        switch.isPressed = true
        switch.isChecked = !initialState
        shadowOf(Looper.getMainLooper()).idle()

        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.invisibleWatermark == initialState && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.invisibleWatermark).isEqualTo(!initialState)
    }

    /** Switch mới không được đẩy 2 switch cũ (IDEA-13, IDEA-03) ra khỏi layout. */
    @Test
    fun cacSwitchCuVanCon() {
        val (_, dialog) = setupDialog()

        assertThat(dialog.binding.swProofingMode).isNotNull()
        assertThat(dialog.binding.swAuthenticityStamp).isNotNull()
        assertThat(dialog.binding.swInvisibleWatermark).isNotNull()
    }

    /**
     * Mô tả KHÔNG được nói "chỉ JPEG" như con dấu EXIF: lớp ẩn nằm trong pixel nên áp dụng được cho
     * mọi định dạng. Nhầm chỗ này là nói dối người dùng.
     */
    @Test
    fun moTa_khongGioiHanDinhDang() {
        val (activity, dialog) = setupDialog()

        val desc = dialog.binding.tvInvisibleWatermarkDesc.text.toString()
        assertThat(desc).isEqualTo(activity.getString(R.string.invisible_watermark_desc))
        assertThat(desc).doesNotContain("JPEG")
    }
}
