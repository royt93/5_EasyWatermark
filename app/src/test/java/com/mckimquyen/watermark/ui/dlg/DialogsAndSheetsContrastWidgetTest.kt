package com.mckimquyen.watermark.ui.dlg

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.databinding.DlgCompressImgBinding
import com.mckimquyen.watermark.databinding.DlgExifBorderBinding
import com.mckimquyen.watermark.databinding.DlgSaveFileBinding
import com.mckimquyen.watermark.databinding.FBatchCaptionBottomSheetBinding
import com.mckimquyen.watermark.databinding.FPositionAnchorBottomSheetBinding
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogsAndSheetsContrastWidgetTest {

    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
    }

    @Test
    fun saveDialog_btnOpenGallery_hasAccessibleTonalColorAndSlideQualityColors() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = DlgSaveFileBinding.inflate(inflater)

        val expectedOnSecondaryContainer = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            Color.BLACK
        )
        assertThat(binding.btnOpenGallery.currentTextColor).isEqualTo(expectedOnSecondaryContainer)
        assertThat(binding.btnOpenGallery.iconTint?.defaultColor).isEqualTo(expectedOnSecondaryContainer)

        val primary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
        val surfaceVariant = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)

        binding.slideQuality.trackActiveTintList = ColorStateList.valueOf(primary)
        binding.slideQuality.trackInactiveTintList = ColorStateList.valueOf(surfaceVariant)
        binding.slideQuality.thumbTintList = ColorStateList.valueOf(primary)

        assertThat(binding.slideQuality.trackActiveTintList?.defaultColor).isEqualTo(primary)
        assertThat(binding.slideQuality.trackInactiveTintList?.defaultColor).isEqualTo(surfaceVariant)
        assertThat(binding.slideQuality.thumbTintList?.defaultColor).isEqualTo(primary)
    }

    @Test
    fun exifDialog_frameStyleHighlighterAndSlider_haveAccessibleContrast() {
        val buttons = mapOf(
            ExifFrameStyle.CLASSIC to MaterialButton(themedContext),
            ExifFrameStyle.POLAROID to MaterialButton(themedContext)
        )
        ExifFrameStyleHighlighter.apply(buttons, ExifFrameStyle.POLAROID)

        val expectedSelectedBg = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorPrimaryContainer,
            Color.BLACK
        )
        val expectedSelectedText = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            Color.BLACK
        )
        val expectedUnselectedText = MaterialColors.getColor(
            themedContext,
            com.google.android.material.R.attr.colorOnSurface,
            Color.DKGRAY
        )

        val polaroidBtn = buttons.getValue(ExifFrameStyle.POLAROID)
        val classicBtn = buttons.getValue(ExifFrameStyle.CLASSIC)

        assertThat(polaroidBtn.backgroundTintList?.defaultColor).isEqualTo(expectedSelectedBg)
        assertThat(polaroidBtn.currentTextColor).isEqualTo(expectedSelectedText)
        assertThat(classicBtn.currentTextColor).isEqualTo(expectedUnselectedText)

        val inflater = LayoutInflater.from(themedContext)
        val binding = DlgExifBorderBinding.inflate(inflater)

        val primary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
        val surfaceVariant = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)

        binding.slideExifBandThickness.trackActiveTintList = ColorStateList.valueOf(primary)
        binding.slideExifBandThickness.trackInactiveTintList = ColorStateList.valueOf(surfaceVariant)
        binding.slideExifBandThickness.thumbTintList = ColorStateList.valueOf(primary)

        assertThat(binding.slideExifBandThickness.trackActiveTintList?.defaultColor).isEqualTo(primary)
        assertThat(binding.slideExifBandThickness.trackInactiveTintList?.defaultColor).isEqualTo(surfaceVariant)
    }

    @Test
    fun positionAnchorDialog_buttonsAndMarginSlider_haveAccessibleTokens() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = FPositionAnchorBottomSheetBinding.inflate(inflater)

        assertThat(binding.btnAnchorTopLeft).isNotNull()
        assertThat(binding.btnAnchorCenter).isNotNull()
        assertThat(binding.btnAnchorBottomRight).isNotNull()
        assertThat(binding.slMargin).isNotNull()

        val primary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
        val surfaceVariant = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)

        binding.slMargin.trackActiveTintList = ColorStateList.valueOf(primary)
        binding.slMargin.trackInactiveTintList = ColorStateList.valueOf(surfaceVariant)
        binding.slMargin.thumbTintList = ColorStateList.valueOf(primary)

        assertThat(binding.slMargin.trackActiveTintList?.defaultColor).isEqualTo(primary)
        assertThat(binding.slMargin.trackInactiveTintList?.defaultColor).isEqualTo(surfaceVariant)
    }

    @Test
    fun batchCaptionDialog_applyButton_hasAccessibleColors() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = FBatchCaptionBottomSheetBinding.inflate(inflater)

        assertThat(binding.btnApplyCaptions).isNotNull()
        val onPrimary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        assertThat(binding.btnApplyCaptions.iconTint?.defaultColor).isEqualTo(onPrimary)
    }

    @Test
    fun compressImageDialog_buttons_haveAccessibleColors() {
        val inflater = LayoutInflater.from(themedContext)
        val binding = DlgCompressImgBinding.inflate(inflater)

        val primary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
        val onPrimary = MaterialColors.getColor(themedContext, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)

        assertThat(binding.btnCancel.currentTextColor).isEqualTo(primary)
        assertThat(binding.btnCompress.backgroundTintList?.defaultColor).isEqualTo(primary)
        assertThat(binding.btnCompress.currentTextColor).isEqualTo(onPrimary)
    }
}
