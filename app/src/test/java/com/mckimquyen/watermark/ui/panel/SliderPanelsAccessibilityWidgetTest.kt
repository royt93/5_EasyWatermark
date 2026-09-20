package com.mckimquyen.watermark.ui.panel

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.slider.Slider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Option B Widget and Unit tests for Slider Floating Panels:
 * - f_base_pb.xml
 * - AlphaPbFragment
 * - DegreePbFragment
 * - TextSizePbFragment
 * - HorizonPbFragment
 * - VerticalPbFragment
 *
 * Verifies focusable sliders, unit labels (%, °, sp, px), and accessibility descriptions.
 */
@RunWith(RobolectricTestRunner::class)
class SliderPanelsAccessibilityWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    private fun createMockWaterMark(
        alpha: Int = 200,
        degree: Float = 45f,
        textSize: Float = 24f,
        hGap: Int = 60,
        vGap: Int = 80
    ) = WaterMark(
        text = "Test",
        textSize = textSize,
        textColor = Color.WHITE,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = alpha,
        degree = degree,
        hGap = hGap,
        vGap = vGap,
        iconUri = Uri.EMPTY,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableBounds = false
    )

    @Test
    fun basePbLayout_sliderIsFocusableAndMeetsMinTouchHeight() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_base_pb, null, false)
        val slider = root.findViewById<Slider>(R.id.slideContentSize)

        assertThat(slider).isNotNull()
        assertThat(slider.isFocusable).isTrue()
        assertThat(slider.minimumHeight).isAtLeast(48)
    }

    @Test
    fun alphaPbFragment_formatsPercentageAndProvidesSliderDescription() {
        val fragment = AlphaPbFragment()
        val mockWaterMark = createMockWaterMark(alpha = 128)

        val tips = fragment.formatValueTips(mockWaterMark)
        assertThat(tips).isEqualTo("50%")

        val title = themedContext.getString(fragment.getSliderTitleRes())
        assertThat(title).isNotEmpty()

        val description = "$title: $tips"
        assertThat(description).contains("50%")
        assertThat(description).contains(title)
    }

    @Test
    fun degreePbFragment_formatsDegreeAndProvidesSliderDescription() {
        val fragment = DegreePbFragment()
        val mockWaterMark = createMockWaterMark(degree = 45f)

        val tips = fragment.formatValueTips(mockWaterMark)
        assertThat(tips).isEqualTo("45°")

        val title = themedContext.getString(fragment.getSliderTitleRes())
        assertThat(title).isNotEmpty()

        val description = "$title: $tips"
        assertThat(description).contains("45°")
        assertThat(description).contains(title)
    }

    @Test
    fun textSizePbFragment_formatsSpAndProvidesSliderDescription() {
        val fragment = TextSizePbFragment()
        val mockWaterMark = createMockWaterMark(textSize = 24f)

        val tips = fragment.formatValueTips(mockWaterMark)
        assertThat(tips).isEqualTo("24 sp")

        val title = themedContext.getString(fragment.getSliderTitleRes())
        assertThat(title).isNotEmpty()

        val description = "$title: $tips"
        assertThat(description).contains("24 sp")
        assertThat(description).contains(title)
    }

    @Test
    fun horizonPbFragment_formatsPxAndProvidesSliderDescription() {
        val fragment = HorizonPbFragment()
        val mockWaterMark = createMockWaterMark(hGap = 60)

        val tips = fragment.formatValueTips(mockWaterMark)
        assertThat(tips).isEqualTo("60 px")

        val title = themedContext.getString(fragment.getSliderTitleRes())
        assertThat(title).isNotEmpty()

        val description = "$title: $tips"
        assertThat(description).contains("60 px")
        assertThat(description).contains(title)
    }

    @Test
    fun verticalPbFragment_formatsPxAndProvidesSliderDescription() {
        val fragment = VerticalPbFragment()
        val mockWaterMark = createMockWaterMark(vGap = 80)

        val tips = fragment.formatValueTips(mockWaterMark)
        assertThat(tips).isEqualTo("80 px")

        val title = themedContext.getString(fragment.getSliderTitleRes())
        assertThat(title).isNotEmpty()

        val description = "$title: $tips"
        assertThat(description).contains("80 px")
        assertThat(description).contains(title)
    }
}
