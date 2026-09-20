package com.mckimquyen.watermark.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.MaterialColors
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.ui.base.BasePBFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TextStyleAndTypographyWidgetTest {

    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
    }

    @Test
    fun textPaintStyleAdapter_selectedAndUnselected_haveDistinctColorsAndBackgrounds() {
        val list = arrayListOf(
            TextPaintStyleAdapter.TextPaintStyleModel(TextPaintStyle.Fill, "Fill"),
            TextPaintStyleAdapter.TextPaintStyleModel(TextPaintStyle.Stroke, "Stroke")
        )
        val adapter = TextPaintStyleAdapter(list, initPaintStyle = TextPaintStyle.Fill)
        val parent = FrameLayout(themedContext)

        val holder0 = adapter.onCreateViewHolder(parent, 0) as TextPaintStyleAdapter.TypefaceHolder
        val holder1 = adapter.onCreateViewHolder(parent, 0) as TextPaintStyleAdapter.TypefaceHolder

        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        val expectedSelectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.BLACK
        )
        val expectedUnselectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.DKGRAY
        )

        // Selected item (pos 0)
        assertThat(holder0.tvPreview.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder0.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)

        // Unselected item (pos 1)
        assertThat(holder1.tvPreview.currentTextColor).isEqualTo(expectedUnselectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button)

        // Click pos 1 to toggle
        holder1.root.performClick()
        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        assertThat(holder1.tvPreview.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)
        assertThat(holder0.tvPreview.currentTextColor).isEqualTo(expectedUnselectedColor)
        assertThat(shadowOf(holder0.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button)
    }

    @Test
    fun textTypefaceAdapter_selectedAndUnselected_haveDistinctColorsAndBackgrounds() {
        val list = arrayListOf(
            TextTypefaceAdapter.TextTypefaceModel(TextTypeface.Normal, "Normal"),
            TextTypefaceAdapter.TextTypefaceModel(TextTypeface.Bold, "Bold")
        )
        val adapter = TextTypefaceAdapter(list, initTypeface = TextTypeface.Normal)
        val parent = FrameLayout(themedContext)

        val holder0 = adapter.onCreateViewHolder(parent, 0) as TextTypefaceAdapter.TypefaceHolder
        val holder1 = adapter.onCreateViewHolder(parent, 0) as TextTypefaceAdapter.TypefaceHolder

        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        val expectedSelectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.BLACK
        )
        val expectedUnselectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.DKGRAY
        )

        // Normal (selected)
        assertThat(holder0.tvPreview.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder0.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)

        // Bold (unselected)
        assertThat(holder1.tvPreview.currentTextColor).isEqualTo(expectedUnselectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button)

        // Click pos 1
        holder1.root.performClick()
        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        assertThat(holder1.tvPreview.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)
        assertThat(holder0.tvPreview.currentTextColor).isEqualTo(expectedUnselectedColor)
        assertThat(shadowOf(holder0.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button)
    }

    @Test
    fun textEffectAdapter_checkedAndUnchecked_haveAccessibleColorsAndStates() {
        val list = arrayListOf(
            TextEffectAdapter.TextEffectModel("Bold", true),
            TextEffectAdapter.TextEffectModel("Italic", false)
        )
        var toggledPos = -1
        var toggledState = false
        val adapter = TextEffectAdapter(list) { pos, enabled ->
            toggledPos = pos
            toggledState = enabled
        }
        val parent = FrameLayout(themedContext)

        val holder0 = adapter.onCreateViewHolder(parent, 0)
        val holder1 = adapter.onCreateViewHolder(parent, 0)

        adapter.onBindViewHolder(holder0, 0)
        adapter.onBindViewHolder(holder1, 1)

        val expectedSelectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.BLACK
        )
        val expectedUnselectedColor = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.DKGRAY
        )

        // Item 0 is enabled
        assertThat(holder0.tvLabel.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder0.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)

        // Item 1 is disabled
        assertThat(holder1.tvLabel.currentTextColor).isEqualTo(expectedUnselectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button)

        // Toggle Item 1
        holder1.root.performClick()
        assertThat(toggledPos).isEqualTo(1)
        assertThat(toggledState).isTrue()
        assertThat(holder1.tvLabel.currentTextColor).isEqualTo(expectedSelectedColor)
        assertThat(shadowOf(holder1.root.background).createdFromResId).isEqualTo(R.drawable.bg_glass_button_checked)
    }

    @Test
    fun basePBFragment_sliderHasHighContrastActiveInactiveTracksAndThumb() {
        val inflater = LayoutInflater.from(themedContext)
        val b = com.mckimquyen.watermark.databinding.FBasePbBinding.inflate(inflater, null, false)
        BasePBFragment.applySliderColors(b)

        val slider = b.slideContentSize
        val tvProgress = b.tvProgressVertical

        val expectedPrimary = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorPrimary,
            Color.BLACK
        )
        val expectedSurfaceVariant = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorSurfaceVariant,
            Color.LTGRAY
        )
        val expectedOnSurface = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.BLACK
        )

        assertThat(slider.trackActiveTintList?.defaultColor).isEqualTo(expectedPrimary)
        assertThat(slider.trackInactiveTintList?.defaultColor).isEqualTo(expectedSurfaceVariant)
        assertThat(slider.thumbTintList?.defaultColor).isEqualTo(expectedPrimary)
        assertThat(tvProgress.currentTextColor).isEqualTo(expectedOnSurface)
    }
}
