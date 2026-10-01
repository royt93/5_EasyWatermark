package com.mckimquyen.watermark.ui.recipient

import android.os.Looper
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * REVIEW-14: `RecipientManagementActivity` ghi đè thẳng `rvRecipients.setPadding(..., navBarBottom)`
 * — mất `paddingBottom="8dp"` khai trong `activity_recipient_management.xml`. Comment tại chỗ còn
 * ghi đúng "bài học FEAT-06/BatchHistoryActivity" nhưng lại chép nhầm pattern CŨ (đã fix ở
 * [com.mckimquyen.watermark.ui.BatchHistoryWatermarkProfileInsetsRoboTest]). Cùng bài kiểm tra.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientManagementActivityInsetsRoboTest {

    private fun mockInsets(navBarBottom: Int) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 40, 0, 0))
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarBottom))
        .build()

    @Test
    fun recipientManagementActivity_navBarBottomZero_keepsXmlBasePadding() {
        val controller = Robolectric.buildActivity(RecipientManagementActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvRecipients = activity.findViewById<RecyclerView>(R.id.rvRecipients)
        val baseBottomPadding = rvRecipients.paddingBottom // 8dp từ activity_recipient_management.xml

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 0))

        assertThat(baseBottomPadding).isGreaterThan(0)
        assertThat(rvRecipients.paddingBottom).isEqualTo(baseBottomPadding)

        controller.pause().stop().destroy()
    }

    @Test
    fun recipientManagementActivity_navBarBottomPositive_addsToXmlBasePadding() {
        val controller = Robolectric.buildActivity(RecipientManagementActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvRecipients = activity.findViewById<RecyclerView>(R.id.rvRecipients)
        val baseBottomPadding = rvRecipients.paddingBottom

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 120))

        assertThat(rvRecipients.paddingBottom).isEqualTo(baseBottomPadding + 120)

        controller.pause().stop().destroy()
    }
}
