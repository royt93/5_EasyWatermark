package com.mckimquyen.watermark.ui

import android.graphics.Color
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.FuncTitleModel
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter
import com.mckimquyen.watermark.ui.adapter.ColorPreviewAdapter.PreViewModel
import com.mckimquyen.watermark.ui.adapter.FuncPanelAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for Editor tool components:
 * - ColorPreviewAdapter interaction and selection state updates.
 * - FuncPanelAdapter integration with content function items.
 */
@RunWith(RobolectricTestRunner::class)
class EditorToolIntegrationTest {

    private val themedContext by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun colorPreviewAdapter_updatesSelectionStateCorrectly() {
        val colorList = arrayListOf(
            PreViewModel(color = Color.WHITE, resId = 0, selected = false),
            PreViewModel(color = Color.BLACK, resId = 0, selected = false),
            PreViewModel(color = Color.RED, resId = 0, selected = false),
            PreViewModel(color = Color.BLUE, resId = 0, selected = false)
        )

        val adapter = ColorPreviewAdapter(colorList)
        assertThat(adapter.itemCount).isEqualTo(4)

        // Select Red color
        adapter.updateSelectedColor(Color.RED)
        val redModel = colorList.first { it.color == Color.RED }
        assertThat(redModel.selected).isTrue()

        // Ensure other colors are unselected
        val whiteModel = colorList.first { it.color == Color.WHITE }
        assertThat(whiteModel.selected).isFalse()

        // Select White color
        adapter.updateSelectedColor(Color.WHITE)
        assertThat(whiteModel.selected).isTrue()
        assertThat(redModel.selected).isFalse()
    }

    @Test
    fun funcPanelAdapter_integratesContentFunctionsWithDistinctIcons() {
        val contentList = arrayListOf(
            FuncTitleModel(FuncTitleModel.FuncType.Text, "Văn bản", R.drawable.ic_func_text),
            FuncTitleModel(FuncTitleModel.FuncType.Icon, "Ảnh", R.drawable.ic_func_sticker),
            FuncTitleModel(FuncTitleModel.FuncType.Signature, "Chữ ký", R.drawable.ic_func_signature),
            FuncTitleModel(FuncTitleModel.FuncType.QRCode, "QR Code", R.drawable.ic_func_qr_code),
            FuncTitleModel(FuncTitleModel.FuncType.ExifBorder, "Khung EXIF", R.drawable.ic_func_frame)
        )

        val adapter = FuncPanelAdapter(themedContext, contentList)
        adapter.setHasStableIds(true)

        assertThat(adapter.itemCount).isEqualTo(5)
        assertThat(adapter.dataSet[2].iconRes).isEqualTo(R.drawable.ic_func_signature)
        assertThat(adapter.dataSet[4].iconRes).isEqualTo(R.drawable.ic_func_frame)

        // Verify all 5 icons are distinct
        val icons = adapter.dataSet.map { it.iconRes }
        assertThat(icons.distinct().size).isEqualTo(5)
    }
}
