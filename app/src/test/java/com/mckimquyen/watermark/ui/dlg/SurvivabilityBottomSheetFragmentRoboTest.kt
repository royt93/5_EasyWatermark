package com.mckimquyen.watermark.ui.dlg

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.core.view.isVisible
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.export.SurvivabilityProfile
import com.mckimquyen.watermark.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * IDEA-09 widget test cho bottom sheet mô phỏng nền tảng.
 *
 * Robolectric KHÔNG rasterize pixel thật (decode mọi uri thành bitmap giả) nên test này chỉ assert
 * HÀNH VI UI — danh sách nền tảng, nhãn mặc định, dọn bitmap khi huỷ view. Phần biến đổi ảnh thật
 * (crop/resize/nén) được phủ ở `androidTest/.../SurvivabilityTransformIntegrationTest`.
 */
@RunWith(RobolectricTestRunner::class)
class SurvivabilityBottomSheetFragmentRoboTest {

    private fun setup(): Pair<MainActivity, SurvivabilityBottomSheetFragment> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        val fragment = SurvivabilityBottomSheetFragment().apply {
            setShowsDialog(false)
            arguments = Bundle().apply {
                putString("arg_uri", Uri.parse("content://test/1").toString())
                putInt("arg_index", 0)
            }
        }
        activity.supportFragmentManager.beginTransaction()
            .add(fragment, SurvivabilityBottomSheetFragment.TAG)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()
        return activity to fragment
    }

    @Test
    fun dropdown_hienThiDuNenTang_vaChonSanNenTangDauTien() {
        val (activity, fragment) = setup()

        val adapter = fragment.binding.atvPlatform.adapter
        assertThat(adapter.count).isEqualTo(SurvivabilityProfile.profiles.size)

        val expectedLabels = SurvivabilityProfile.profiles.map {
            SurvivabilityBottomSheetFragment.platformLabel(activity, it.platform)
        }
        val actualLabels = (0 until adapter.count).map { adapter.getItem(it).toString() }
        assertThat(actualLabels).containsExactlyElementsIn(expectedLabels).inOrder()
        assertThat(fragment.binding.atvPlatform.text.toString()).isEqualTo(expectedLabels.first())
    }

    @Test
    fun moiIssueVaSuggestion_deuCoChuoiHienThi() {
        val (activity, _) = setup()

        for (issue in SurvivabilityProfile.Issue.entries) {
            assertThat(SurvivabilityBottomSheetFragment.formatIssue(activity, issue)).isNotEmpty()
        }
        for (suggestion in SurvivabilityProfile.Suggestion.entries) {
            assertThat(SurvivabilityBottomSheetFragment.formatSuggestion(activity, suggestion)).isNotEmpty()
        }
    }

    @Test
    fun hintGiaiThichDayLaMoPhongUocLuong() {
        val (activity, fragment) = setup()
        assertThat(fragment.binding.tvHint.text.toString())
            .isEqualTo(activity.getString(R.string.survivability_hint))
        assertThat(fragment.binding.tvHint.isVisible).isTrue()
    }

    @Test
    fun huyView_khongGiuBitmapVaKhongCrash() {
        val (activity, fragment) = setup()

        activity.supportFragmentManager.beginTransaction().remove(fragment).commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.view).isNull()
    }
}
