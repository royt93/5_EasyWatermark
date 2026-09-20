package com.mckimquyen.watermark.ui.dlg

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomSheetDragHandleAndPillWidgetTest {

    private lateinit var themedContext: ContextThemeWrapper
    private lateinit var inflater: LayoutInflater

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(app, R.style.Theme_MyApp)
        inflater = LayoutInflater.from(themedContext)
    }

    @Test
    fun allBottomSheets_haveM3DragHandleWithPillDimensions() {
        val bottomSheetLayouts = listOf(
            R.layout.dlg_save_file,
            R.layout.f_position_anchor_bottom_sheet,
            R.layout.f_qr_code_bottom_sheet,
            R.layout.f_signature_bottom_sheet,
            R.layout.f_batch_caption_bottom_sheet,
            R.layout.dlg_exif_border,
            R.layout.dlg_edit_text,
            R.layout.dlg_edit_template,
            R.layout.dlg_edit_text_template_list
        )

        val density = themedContext.resources.displayMetrics.density
        val expectedWidthPx = (32 * density).toInt()
        val expectedHeightPx = (4 * density).toInt()

        for (layoutRes in bottomSheetLayouts) {
            val root = inflater.inflate(layoutRes, null)
            val handle = root.findViewById<View>(R.id.vHandle)
            assertThat(handle).isNotNull()

            val lp = handle.layoutParams
            assertThat(lp.width).isEqualTo(expectedWidthPx)
            assertThat(lp.height).isEqualTo(expectedHeightPx)
        }
    }

    @Test
    fun allPrimaryCtaButtons_haveM3PillCornerRadius() {
        // 1. dlg_save_file -> btnSave
        val saveRoot = inflater.inflate(R.layout.dlg_save_file, null)
        val btnSave = saveRoot.findViewById<MaterialButton>(R.id.btnSave)
        assertThat(btnSave).isNotNull()
        val density = themedContext.resources.displayMetrics.density
        assertThat(btnSave.cornerRadius).isAtLeast((24 * density).toInt())

        // 2. f_qr_code_bottom_sheet -> btnUseQrCode
        val qrRoot = inflater.inflate(R.layout.f_qr_code_bottom_sheet, null)
        val btnUseQrCode = qrRoot.findViewById<MaterialButton>(R.id.btnUseQrCode)
        assertThat(btnUseQrCode).isNotNull()
        assertThat(btnUseQrCode.cornerRadius).isAtLeast((24 * density).toInt())

        // 3. f_signature_bottom_sheet -> btnSaveSignature
        val sigRoot = inflater.inflate(R.layout.f_signature_bottom_sheet, null)
        val btnSaveSignature = sigRoot.findViewById<MaterialButton>(R.id.btnSaveSignature)
        assertThat(btnSaveSignature).isNotNull()
        assertThat(btnSaveSignature.cornerRadius).isAtLeast((24 * density).toInt())

        // 4. f_batch_caption_bottom_sheet -> btnApplyCaptions
        val captionRoot = inflater.inflate(R.layout.f_batch_caption_bottom_sheet, null)
        val btnApplyCaptions = captionRoot.findViewById<MaterialButton>(R.id.btnApplyCaptions)
        assertThat(btnApplyCaptions).isNotNull()
        assertThat(btnApplyCaptions.cornerRadius).isAtLeast((24 * density).toInt())

        // 5. dlg_edit_text -> btnConfirm
        val editTextRoot = inflater.inflate(R.layout.dlg_edit_text, null)
        val btnConfirmText = editTextRoot.findViewById<MaterialButton>(R.id.btnConfirm)
        assertThat(btnConfirmText).isNotNull()
        assertThat(btnConfirmText.cornerRadius).isAtLeast((24 * density).toInt())

        // 6. dlg_edit_template -> btnConfirm
        val editTplRoot = inflater.inflate(R.layout.dlg_edit_template, null)
        val btnConfirmTpl = editTplRoot.findViewById<MaterialButton>(R.id.btnConfirm)
        assertThat(btnConfirmTpl).isNotNull()
        assertThat(btnConfirmTpl.cornerRadius).isAtLeast((24 * density).toInt())

        // 7. dlg_edit_text_template_list -> btnAdd
        val tplListRoot = inflater.inflate(R.layout.dlg_edit_text_template_list, null)
        val btnAdd = tplListRoot.findViewById<MaterialButton>(R.id.btnAdd)
        assertThat(btnAdd).isNotNull()
        assertThat(btnAdd.cornerRadius).isAtLeast((24 * density).toInt())

        // 8. dlg_compress_img -> btnCompress
        val compressRoot = inflater.inflate(R.layout.dlg_compress_img, null)
        val btnCompress = compressRoot.findViewById<MaterialButton>(R.id.btnCompress)
        assertThat(btnCompress).isNotNull()
        assertThat(btnCompress.cornerRadius).isAtLeast((24 * density).toInt())
    }
}
