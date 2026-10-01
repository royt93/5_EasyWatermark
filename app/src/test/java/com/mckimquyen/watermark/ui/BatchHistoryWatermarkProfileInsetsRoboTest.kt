package com.mckimquyen.watermark.ui

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
 * REVIEW-13: `setOnApplyWindowInsetsListener` của 2 màn này trước đây GHI ĐÈ thẳng
 * `setPadding(..., navBarBottom)`, làm mất `paddingBottom` khai trong XML (16dp/8dp — khoảng thở
 * dưới item cuối list). Trên thiết bị/orientation có `navBarBottom == 0` (gesture nav không chiếm
 * inset, màn hình external...), item cuối dính sát mép màn hình. Fix: cộng dồn
 * `baseBottomPadding + navBarBottom`, đúng pattern `CropActivity`/`SmartRedactionActivity`.
 */
@RunWith(RobolectricTestRunner::class)
class BatchHistoryWatermarkProfileInsetsRoboTest {

    private fun mockInsets(navBarBottom: Int) = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 40, 0, 0))
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarBottom))
        .build()

    @Test
    fun batchHistoryActivity_navBarBottomZero_keepsXmlBasePadding() {
        val controller = Robolectric.buildActivity(BatchHistoryActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvHistory = activity.findViewById<RecyclerView>(R.id.rvHistory)
        val baseBottomPadding = rvHistory.paddingBottom // 16dp từ activity_batch_history.xml

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 0))

        assertThat(baseBottomPadding).isGreaterThan(0)
        assertThat(rvHistory.paddingBottom).isEqualTo(baseBottomPadding)

        controller.pause().stop().destroy()
    }

    @Test
    fun batchHistoryActivity_navBarBottomPositive_addsToXmlBasePadding() {
        val controller = Robolectric.buildActivity(BatchHistoryActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvHistory = activity.findViewById<RecyclerView>(R.id.rvHistory)
        val baseBottomPadding = rvHistory.paddingBottom

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 120))

        assertThat(rvHistory.paddingBottom).isEqualTo(baseBottomPadding + 120)

        controller.pause().stop().destroy()
    }

    @Test
    fun watermarkProfileActivity_navBarBottomZero_keepsXmlBasePadding() {
        val controller = Robolectric.buildActivity(WatermarkProfileActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvProfiles = activity.findViewById<RecyclerView>(R.id.rvProfiles)
        val baseBottomPadding = rvProfiles.paddingBottom // 8dp từ activity_watermark_profile.xml

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 0))

        assertThat(baseBottomPadding).isGreaterThan(0)
        assertThat(rvProfiles.paddingBottom).isEqualTo(baseBottomPadding)

        controller.pause().stop().destroy()
    }

    @Test
    fun watermarkProfileActivity_navBarBottomPositive_addsToXmlBasePadding() {
        val controller = Robolectric.buildActivity(WatermarkProfileActivity::class.java).create().start().resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val rvProfiles = activity.findViewById<RecyclerView>(R.id.rvProfiles)
        val baseBottomPadding = rvProfiles.paddingBottom

        ViewCompat.dispatchApplyWindowInsets(contentRoot, mockInsets(navBarBottom = 120))

        assertThat(rvProfiles.paddingBottom).isEqualTo(baseBottomPadding + 120)

        controller.pause().stop().destroy()
    }
}
