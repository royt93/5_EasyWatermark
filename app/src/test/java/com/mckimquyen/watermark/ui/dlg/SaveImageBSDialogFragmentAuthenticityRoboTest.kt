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
 * IDEA-03 widget test: switch "Nhúng con dấu chứng thực" trong dialog lưu ảnh.
 *
 * Theo khuôn [SaveImageBSDialogFragmentProofingRoboTest] (IDEA-13).
 */
@RunWith(RobolectricTestRunner::class)
class SaveImageBSDialogFragmentAuthenticityRoboTest {

    private fun setupDialog(): Pair<MainActivity, SaveImageBSDialogFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val dialogFragment = SaveImageBSDialogFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction()
            .add(dialogFragment, "SaveImageBSDialogFragmentAuthenticityTest")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to dialogFragment
    }

    @Test
    fun swAuthenticityStamp_isMaterialSwitch_andReflectsViewModelState() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swAuthenticityStamp
        assertThat(switch).isInstanceOf(MaterialSwitch::class.java)
        assertThat(switch.isChecked).isEqualTo(viewModel.authenticityStamp)
        assertThat(switch.contentDescription.toString())
            .isEqualTo(activity.getString(R.string.authenticity_stamp_title))
    }

    // Trạng thái mặc định (tắt) được khoá ở `UserConfigRepositoryRoboTest.testAuthenticityStamp_*` —
    // assert ở đây sẽ phụ thuộc thứ tự chạy vì các test trong class này dùng chung một DataStore.

    @Test
    fun swAuthenticityStamp_toggle_callsSaveAuthenticityStamp() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val switch = dialog.binding.swAuthenticityStamp
        val initialState = viewModel.authenticityStamp

        switch.isPressed = true
        switch.isChecked = !initialState
        shadowOf(Looper.getMainLooper()).idle()

        val deadline = System.currentTimeMillis() + 3_000
        while (viewModel.authenticityStamp == initialState && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(viewModel.authenticityStamp).isEqualTo(!initialState)
    }

    @Test
    fun moTa_giaiThichRoKhiDinhDangKhongPhaiJpeg() {
        val (activity, dialog) = setupDialog()
        val viewModel = ViewModelProvider(activity)[MainViewModel::class.java]

        val desc = dialog.binding.tvAuthenticityStampDesc.text.toString()
        val expected = if (viewModel.outputFormat == android.graphics.Bitmap.CompressFormat.JPEG) {
            activity.getString(R.string.authenticity_stamp_desc)
        } else {
            activity.getString(R.string.authenticity_stamp_desc_unsupported, viewModel.outputFormat.name)
        }
        assertThat(desc).isEqualTo(expected)
    }

    /** Switch mới KHÔNG được đẩy switch proofing (IDEA-13) ra khỏi layout. */
    @Test
    fun switchProofingCuVanCon() {
        val (_, dialog) = setupDialog()

        assertThat(dialog.binding.swProofingMode).isNotNull()
        assertThat(dialog.binding.swAuthenticityStamp).isNotNull()
    }
}
