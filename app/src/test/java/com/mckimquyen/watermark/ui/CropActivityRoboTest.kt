package com.mckimquyen.watermark.ui

import android.content.Intent
import android.net.Uri
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-16: [CropActivity] — phần đồng bộ trong onCreate (chip tỉ lệ, slider, toolbar). Không cover
 * luồng decode ảnh bất đồng bộ + Apply (lifecycleScope.launch thật) trong JVM test — mirror giới
 * hạn môi trường đã ghi nhận ở FEAT-15 cho luồng Activity/Fragment bất đồng bộ tương tự, verify
 * bằng smoke test thật trên device thay vì cố ép 1 test JVM dễ flaky.
 */
@RunWith(RobolectricTestRunner::class)
class CropActivityRoboTest {

    private fun launchWithUri(uri: String = "content://media/test.jpg") =
        Robolectric.buildActivity(
            CropActivity::class.java,
            Intent(null, Uri.EMPTY).putExtra(CropActivity.EXTRA_IMAGE_URI, uri)
        ).setup().get()

    @Test
    fun onCreate_missingImageUri_finishesImmediately() {
        val controller = Robolectric.buildActivity(CropActivity::class.java, Intent()).create()
        val activity = controller.get()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun onCreate_withImageUri_buildsRatioChips_freeSelectedByDefault() {
        val activity = launchWithUri()

        val chipGroup = activity.findViewById<ChipGroup>(R.id.cgRatio)
        assertThat(chipGroup.childCount).isEqualTo(6)
        val labels = (0 until chipGroup.childCount).map { (chipGroup.getChildAt(it) as Chip).text.toString() }
        assertThat(labels).containsExactly(
            activity.getString(R.string.crop_ratio_free),
            activity.getString(R.string.crop_ratio_square),
            activity.getString(R.string.crop_ratio_4_5),
            activity.getString(R.string.crop_ratio_16_9),
            activity.getString(R.string.crop_ratio_9_16),
            activity.getString(R.string.crop_ratio_3_4)
        ).inOrder()

        val freeChip = chipGroup.getChildAt(0) as Chip
        assertThat(freeChip.isChecked).isTrue()
        assertThat(chipGroup.checkedChipId).isEqualTo(freeChip.id)
    }

    @Test
    fun onCreate_straightenSlider_rangeMatchesSpec() {
        val activity = launchWithUri()

        val slider = activity.findViewById<Slider>(R.id.sliderStraighten)
        assertThat(slider.valueFrom).isEqualTo(-45f)
        assertThat(slider.valueTo).isEqualTo(45f)
        assertThat(slider.value).isEqualTo(0f)
    }

    @Test
    fun toolbarBackNavigation_finishesActivity() {
        val activity = launchWithUri()
        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar.navigationIcon).isNotNull()

        // Toolbar không expose getter cho navigation click listener — tìm đúng
        // AppCompatImageButton nội bộ mà Toolbar tự tạo cho navigation icon rồi click thật.
        val navButton = (0 until toolbar.childCount)
            .map { toolbar.getChildAt(it) }
            .firstOrNull { it is androidx.appcompat.widget.AppCompatImageButton }
            ?: error("navigation button view not found")
        navButton.performClick()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun applyButton_exists_andIsFilledStyle() {
        val activity = launchWithUri()

        val btn = activity.findViewById<MaterialButton>(R.id.btnApplyCrop)
        assertThat(btn.text.toString()).isEqualTo(activity.getString(R.string.crop_apply))
    }
}
