package com.mckimquyen.watermark.ui.dlg

import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.databinding.DlgExifBorderBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-18 widget test: switch "màu khung theo ảnh" nằm trong `groupCustomize` (ẩn/hiện cùng các tuỳ chỉnh
 * FEAT-14), là MaterialSwitch M3 và có contentDescription cho TalkBack. Launch thật [ExifPbFragment] không
 * khả thi (Hilt ViewModel, xem `ExifPbFragmentRoboTest`) — test ở mức layout đã inflate với theme app.
 */
@RunWith(RobolectricTestRunner::class)
class ExifAutoPaletteSwitchWidgetTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApp)
    private val binding = DlgExifBorderBinding.inflate(LayoutInflater.from(context))

    @Test
    fun autoPaletteSwitch_isMaterial3Switch_offByDefault_withAccessibleLabel() {
        val switch = binding.swExifAutoPalette

        assertThat(switch).isInstanceOf(MaterialSwitch::class.java)
        assertThat(switch.isChecked).isFalse()
        assertThat(switch.contentDescription.toString()).isEqualTo(context.getString(R.string.exif_customize_auto_palette))
    }

    @Test
    fun autoPaletteSwitch_andBandColorRow_liveInsideCustomizeGroup() {
        var parent = binding.swExifAutoPalette.parent
        while (parent != null && parent !== binding.groupCustomize) parent = parent.parent
        assertThat(parent).isSameInstanceAs(binding.groupCustomize)

        assertThat(binding.rowExifBandColor.parent).isSameInstanceAs(binding.groupCustomize)
    }

    @Test
    fun autoPaletteSwitch_isPlacedBeforeBandColorRow() {
        val group = binding.groupCustomize
        val switchRowIndex = group.indexOfChild(binding.swExifAutoPalette.parent as android.view.View)
        val bandRowIndex = group.indexOfChild(binding.rowExifBandColor)

        assertThat(switchRowIndex).isLessThan(bandRowIndex)
    }
}
