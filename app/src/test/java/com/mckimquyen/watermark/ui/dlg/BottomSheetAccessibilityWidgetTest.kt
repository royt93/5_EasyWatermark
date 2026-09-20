package com.mckimquyen.watermark.ui.dlg

import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Option A Widget tests for Bottom Sheets:
 * - QrCodeBottomSheetFragment (f_qr_code_bottom_sheet.xml)
 * - ExifPbFragment (dlg_exif_border.xml)
 * - PositionAnchorBottomSheetFragment (f_position_anchor_bottom_sheet.xml)
 *
 * Verifies Material 3 styling, touch targets (>=48dp), and TalkBack accessibility attributes.
 */
@RunWith(RobolectricTestRunner::class)
class BottomSheetAccessibilityWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun qrCodeBottomSheet_hasClearTextIconAndAccessibleTouchTargets() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_qr_code_bottom_sheet, null, false)

        val tilContent = root.findViewById<TextInputLayout>(R.id.tilContent)
        val etContent = root.findViewById<android.widget.EditText>(R.id.etContent)
        val btnUseQrCode = root.findViewById<MaterialButton>(R.id.btnUseQrCode)

        assertThat(tilContent).isNotNull()
        assertThat(tilContent.endIconMode).isEqualTo(TextInputLayout.END_ICON_CLEAR_TEXT)
        assertThat(etContent.minimumHeight).isAtLeast(48)
        assertThat(btnUseQrCode.minimumHeight).isAtLeast(48)
    }

    @Test
    fun exifBorderBottomSheet_hasAccessibleButtonsSwitchesAndSlider() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.dlg_exif_border, null, false)

        val swExif = root.findViewById<MaterialSwitch>(R.id.swExif)
        val swExifSerifCaption = root.findViewById<MaterialSwitch>(R.id.swExifSerifCaption)
        val btnStyleClassic = root.findViewById<MaterialButton>(R.id.btnStyleClassic)
        val btnStylePolaroid = root.findViewById<MaterialButton>(R.id.btnStylePolaroid)
        val btnStyleFilmStrip = root.findViewById<MaterialButton>(R.id.btnStyleFilmStrip)
        val btnStyleMinimal = root.findViewById<MaterialButton>(R.id.btnStyleMinimal)
        val tvExifCustomizeReset = root.findViewById<TextView>(R.id.tvExifCustomizeReset)
        val flExifBandColor = root.findViewById<FrameLayout>(R.id.flExifBandColor)
        val slideExifBandThickness = root.findViewById<Slider>(R.id.slideExifBandThickness)

        // Switch descriptions
        assertThat(swExif.contentDescription).isNotNull()
        assertThat(swExif.contentDescription.toString()).isNotEmpty()
        assertThat(swExifSerifCaption.contentDescription).isNotNull()
        assertThat(swExifSerifCaption.contentDescription.toString()).isNotEmpty()

        // 4 frame style buttons minHeight >= 48dp
        assertThat(btnStyleClassic.minimumHeight).isAtLeast(48)
        assertThat(btnStylePolaroid.minimumHeight).isAtLeast(48)
        assertThat(btnStyleFilmStrip.minimumHeight).isAtLeast(48)
        assertThat(btnStyleMinimal.minimumHeight).isAtLeast(48)

        // Reset button touch target >= 48dp
        assertThat(tvExifCustomizeReset.minimumHeight).isAtLeast(48)

        // Color swatch container is 48dp, clickable, focusable, and has contentDescription
        assertThat(flExifBandColor).isNotNull()
        assertThat(flExifBandColor.isClickable).isTrue()
        assertThat(flExifBandColor.isFocusable).isTrue()
        assertThat(flExifBandColor.contentDescription).isNotNull()

        // Slider is focusable for screen readers
        assertThat(slideExifBandThickness.isFocusable).isTrue()
        assertThat(slideExifBandThickness.contentDescription).isNotNull()
    }

    @Test
    fun positionAnchorBottomSheet_anchorButtonsHaveLocalizedDescriptionsAndSliderIsAccessible() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_position_anchor_bottom_sheet, null, false)

        val anchorIds = listOf(
            R.id.btnAnchorTopLeft,
            R.id.btnAnchorTopCenter,
            R.id.btnAnchorTopRight,
            R.id.btnAnchorCenterLeft,
            R.id.btnAnchorCenter,
            R.id.btnAnchorCenterRight,
            R.id.btnAnchorBottomLeft,
            R.id.btnAnchorBottomCenter,
            R.id.btnAnchorBottomRight
        )

        anchorIds.forEach { id ->
            val button = root.findViewById<MaterialButton>(id)
            assertThat(button).isNotNull()
            assertThat(button.contentDescription).isNotNull()
            assertThat(button.contentDescription.toString()).isNotEmpty()
            // Height 64dp > 48dp
            assertThat(button.layoutParams.height).isAtLeast(48)
        }

        val slMargin = root.findViewById<Slider>(R.id.slMargin)
        assertThat(slMargin).isNotNull()
        assertThat(slMargin.isFocusable).isTrue()
        assertThat(slMargin.contentDescription).isNotNull()
    }
}
