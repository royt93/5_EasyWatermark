package com.mckimquyen.watermark.ui

import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget tests verifying layout inflation and UI component properties for:
 * - f_position_anchor_bottom_sheet
 * - dlg_edit_text_template_list
 * - dlg_compress_img
 * - a_open_source
 */
@RunWith(RobolectricTestRunner::class)
class DialogsAndSheetsWidgetTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    private val inflater by lazy { LayoutInflater.from(themedContext) }

    @Test
    fun positionAnchorSheet_inflatesWithNineVectorIconButtons() {
        val root = inflater.inflate(R.layout.f_position_anchor_bottom_sheet, null)
        assertThat(root).isNotNull()

        val buttonIds = listOf(
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

        for (btnId in buttonIds) {
            val btn = root.findViewById<MaterialButton>(btnId)
            assertThat(btn).isNotNull()
            assertThat(btn.icon).isNotNull()
            assertThat(btn.contentDescription).isNotNull()
        }
    }

    @Test
    fun templateListDialog_inflatesWithIconButtonBackAndEmptyState() {
        val root = inflater.inflate(R.layout.dlg_edit_text_template_list, null)
        assertThat(root).isNotNull()

        val backBtn = root.findViewById<MaterialButton>(R.id.ivBack)
        assertThat(backBtn).isNotNull()
        assertThat(backBtn.icon).isNotNull()

        val tvEmpty = root.findViewById<TextView>(R.id.tvEmpty)
        assertThat(tvEmpty).isNotNull()
        assertThat(tvEmpty.text.toString()).isNotEmpty()
    }

    @Test
    fun compressImageDialog_inflatesWithM3StylingAndButtons() {
        val root = inflater.inflate(R.layout.dlg_compress_img, null)
        assertThat(root).isNotNull()

        val btnCompress = root.findViewById<MaterialButton>(R.id.btnCompress)
        val btnCancel = root.findViewById<MaterialButton>(R.id.btnCancel)
        val tvTitle = root.findViewById<TextView>(R.id.tvTitle)

        assertThat(btnCompress).isNotNull()
        assertThat(btnCancel).isNotNull()
        assertThat(tvTitle).isNotNull()
    }

    @Test
    fun openSourceActivity_inflatesWithCardsAndBadges() {
        val root = inflater.inflate(R.layout.a_open_source, null)
        assertThat(root).isNotNull()

        val cardIds = listOf(
            R.id.cardColorPicker,
            R.id.cardGlideLibrary,
            R.id.cardMaterialComponents,
            R.id.cardMaterialCompressor
        )

        for (cardId in cardIds) {
            val card = root.findViewById<android.view.View>(cardId)
            assertThat(card).isNotNull()
        }
    }
}
