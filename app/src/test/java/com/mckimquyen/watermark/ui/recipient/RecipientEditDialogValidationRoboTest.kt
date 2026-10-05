package com.mckimquyen.watermark.ui.recipient

import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * BUG-71: `setPositiveButton` luôn tự dismiss nên validate lỗi (tên/mã rỗng) chỉ toast rồi dialog biến
 * mất, user phải nhập lại từ đầu. Dialog phải GIỮ NGUYÊN (còn input) khi dữ liệu chưa hợp lệ.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientEditDialogValidationRoboTest {

    private fun openAddDialog(): Pair<RecipientManagementActivity, AlertDialog> {
        val controller = Robolectric.buildActivity(RecipientManagementActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        activity.findViewById<android.view.View>(R.id.btnAddRecipient).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        // Material3 dùng androidx.appcompat.app.AlertDialog (khác android.app.AlertDialog của ShadowAlertDialog).
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        return activity to dialog
    }

    @Test
    fun confirmWithEmptyName_dialogStaysOpen_andKeepsOtherInput() {
        val (_, dialog) = openAddDialog()
        dialog.findViewById<EditText>(R.id.etName)!!.setText("")
        dialog.findViewById<EditText>(R.id.etNotes)!!.setText("ghi chú quan trọng")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.findViewById<EditText>(R.id.etNotes)!!.text.toString()).isEqualTo("ghi chú quan trọng")
    }

    @Test
    fun confirmWithEmptyCode_dialogStaysOpen() {
        val (_, dialog) = openAddDialog()
        dialog.findViewById<EditText>(R.id.etName)!!.setText("Khách A")
        dialog.findViewById<EditText>(R.id.etCode)!!.setText("")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(dialog.isShowing).isTrue()
        assertThat(dialog.findViewById<EditText>(R.id.etName)!!.text.toString()).isEqualTo("Khách A")
    }

    @Test
    fun confirmWithValidInput_dialogDismisses() {
        val (_, dialog) = openAddDialog()
        dialog.findViewById<EditText>(R.id.etName)!!.setText("Khách B")
        dialog.findViewById<EditText>(R.id.etCode)!!.setText("CODE-B-${System.nanoTime()}")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        val deadline = System.currentTimeMillis() + 5_000
        while (dialog.isShowing && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertThat(dialog.isShowing).isFalse()
    }
}
