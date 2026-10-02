package com.mckimquyen.watermark.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.ItemDualPresetBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-26: smoke test thật trên TECNO KJ7 phát hiện tiêu đề + mô tả preset đè lên nhau khi mô tả
 * xuống 2 dòng (desc bị neo cứng vào đáy icon 44dp). Test này khoá hành vi: title và desc nằm
 * chung 1 cột dọc, desc luôn nằm DƯỚI title, và chiều cao row co giãn theo nội dung.
 */
@RunWith(RobolectricTestRunner::class)
class DualPresetItemLayoutWidgetTest {

    private val themedContext =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)

    private fun inflateAndMeasure(descText: String, widthPx: Int = 720): ItemDualPresetBinding {
        val binding = ItemDualPresetBinding.inflate(LayoutInflater.from(themedContext), null, false)
        binding.tvPresetTitle.text = "Thương hiệu & Bản quyền"
        binding.tvPresetDesc.text = descText
        val parent = LinearLayout(themedContext)
        val root = binding.root
        parent.addView(root, ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return binding
    }

    private fun View.topInRoot(root: View): Int {
        var y = 0
        var v: View = this
        while (v !== root) {
            y += v.top
            v = v.parent as View
        }
        return y
    }

    private fun View.bottomInRoot(root: View): Int = topInRoot(root) + height

    @Test
    fun title_and_desc_share_one_vertical_column() {
        val b = inflateAndMeasure("Logo góc trên phải, chữ bản quyền góc dưới phải")
        assertThat(b.tvPresetDesc.parent).isSameInstanceAs(b.tvPresetTitle.parent)
        assertThat((b.tvPresetTitle.parent as LinearLayout).orientation).isEqualTo(LinearLayout.VERTICAL)
    }

    @Test
    fun longTwoLineDesc_isBelowTitle_noOverlap() {
        val b = inflateAndMeasure("Logo góc trên phải, chữ bản quyền góc dưới phải. Thêm câu dài để chắc chắn xuống nhiều dòng.")
        val root = b.root
        val titleBottom = b.tvPresetTitle.topInRoot(root) + b.tvPresetTitle.height
        val descTop = b.tvPresetDesc.topInRoot(root)
        assertThat(b.tvPresetDesc.lineCount).isAtLeast(1)
        assertThat(descTop).isAtLeast(titleBottom)
    }

    @Test
    fun rowHeight_growsWithDescLines() {
        // Dùng '\n' ép nhiều dòng: Robolectric không đo độ rộng chữ thật nên không tự wrap.
        val short = inflateAndMeasure("Một dòng").root.measuredHeight
        val long = inflateAndMeasure("Dòng 1\nDòng 2\nDòng 3\nDòng 4\nDòng 5").root.measuredHeight
        assertThat(long).isGreaterThan(short)
    }

    @Test
    fun multiLineDesc_isBelowTitle_noOverlap() {
        val b = inflateAndMeasure("Dòng 1\nDòng 2\nDòng 3")
        val titleBottom = b.tvPresetTitle.topInRoot(b.root) + b.tvPresetTitle.height
        assertThat(b.tvPresetDesc.lineCount).isEqualTo(3)
        assertThat(b.tvPresetDesc.topInRoot(b.root)).isAtLeast(titleBottom)
        assertThat(b.tvPresetDesc.bottomInRoot(b.root)).isAtMost(b.root.measuredHeight)
    }
}
