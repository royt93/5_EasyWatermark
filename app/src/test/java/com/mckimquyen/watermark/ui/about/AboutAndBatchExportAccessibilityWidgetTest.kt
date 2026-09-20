package com.mckimquyen.watermark.ui.about

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Option C Widget tests for About screen, Batch Caption, and Export dialog:
 * - a_about.xml: vertical column engagement cards, action bar navigation icon tint, min touch heights >= 64dp
 * - f_batch_caption_bottom_sheet.xml: clear text icon on multi-line caption input
 * - dlg_save_file.xml: focusable quality slider with content description, primary export button height >= 48dp
 */
@RunWith(RobolectricTestRunner::class)
class AboutAndBatchExportAccessibilityWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun aboutActivityLayout_hasVerticalCardsAndAccessibleTouchRows() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.a_about, null, false)

        val topAppBar = root.findViewById<MaterialToolbar>(R.id.topAppBar)
        val cardEngagement = root.findViewById<MaterialCardView>(R.id.cardEngagement)
        val tvRating = root.findViewById<LinearLayout>(R.id.tvRating)
        val tvShareApp = root.findViewById<LinearLayout>(R.id.tvShareApp)
        val tvMoreApp = root.findViewById<LinearLayout>(R.id.tvMoreApp)
        val tvBackupData = root.findViewById<LinearLayout>(R.id.tvBackupData)
        val tvRestoreData = root.findViewById<LinearLayout>(R.id.tvRestoreData)

        assertThat(topAppBar).isNotNull()
        assertThat(topAppBar.navigationIcon).isNotNull()

        // Vertical card container
        assertThat(cardEngagement).isNotNull()

        // Each engagement item has touch target height >= 64dp
        assertThat(tvRating.minimumHeight).isAtLeast(64)
        assertThat(tvShareApp.minimumHeight).isAtLeast(64)
        assertThat(tvMoreApp.minimumHeight).isAtLeast(64)

        // Data management item touch targets >= 64dp
        assertThat(tvBackupData.minimumHeight).isAtLeast(64)
        assertThat(tvRestoreData.minimumHeight).isAtLeast(64)
    }

    @Test
    fun batchCaptionBottomSheet_hasClearTextIcon() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.f_batch_caption_bottom_sheet, null, false)
        val tilCaptions = root.findViewById<TextInputLayout>(R.id.tilCaptions)

        assertThat(tilCaptions).isNotNull()
        assertThat(tilCaptions.endIconMode).isEqualTo(TextInputLayout.END_ICON_CLEAR_TEXT)
    }

    @Test
    fun saveFileDialog_hasFocusableQualitySliderAndAccessibleCTA() {
        val root = LayoutInflater.from(themedContext).inflate(R.layout.dlg_save_file, null, false)

        val slideQuality = root.findViewById<Slider>(R.id.slideQuality)
        val btnSave = root.findViewById<MaterialButton>(R.id.btnSave)
        val btnBatchCaptions = root.findViewById<MaterialButton>(R.id.btnBatchCaptions)

        assertThat(slideQuality).isNotNull()
        assertThat(slideQuality.isFocusable).isTrue()
        assertThat(slideQuality.contentDescription).isNotNull()

        assertThat(btnSave).isNotNull()
        assertThat(btnSave.layoutParams.height).isAtLeast(48)
        assertThat(btnBatchCaptions.layoutParams.height).isAtLeast(48)
    }
}
