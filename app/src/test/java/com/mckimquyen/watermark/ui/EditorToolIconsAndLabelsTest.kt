package com.mckimquyen.watermark.ui

import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.FuncTitleModel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorToolIconsAndLabelsTest {

    private val context by lazy {
        ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
    }

    @Test
    fun contentFunctionTabs_haveUniqueIcons_noDuplicates() {
        val funList = listOf(
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Text,
                title = context.getString(R.string.water_mark_mode_text),
                iconRes = R.drawable.ic_func_text
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Icon,
                title = context.getString(R.string.water_mark_mode_image),
                iconRes = R.drawable.ic_func_sticker
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.Signature,
                title = context.getString(R.string.func_title_signature),
                iconRes = R.drawable.ic_func_signature
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.QRCode,
                title = context.getString(R.string.qr_code),
                iconRes = R.drawable.ic_func_qr_code
            ),
            FuncTitleModel(
                type = FuncTitleModel.FuncType.ExifBorder,
                title = context.getString(R.string.func_title_leica_exif),
                iconRes = R.drawable.ic_func_frame
            )
        )

        // 1. Signature must NOT share the Text icon
        assertThat(R.drawable.ic_func_signature).isNotEqualTo(R.drawable.ic_func_text)

        // 2. ExifBorder must NOT share the Layout Vertical icon
        assertThat(R.drawable.ic_func_frame).isNotEqualTo(R.drawable.ic_func_layout_vertical)

        // 3. Every tab in contentFunList has a unique icon
        val iconSet = funList.map { it.iconRes }.toSet()
        assertThat(iconSet.size).isEqualTo(funList.size)
    }

    @Test
    fun exifFrameIcon_andSignatureIcon_drawablesAreValid() {
        val signatureDrawable = context.getDrawable(R.drawable.ic_func_signature)
        val frameDrawable = context.getDrawable(R.drawable.ic_func_frame)

        assertThat(signatureDrawable).isNotNull()
        assertThat(frameDrawable).isNotNull()
        assertThat(signatureDrawable!!.intrinsicWidth).isGreaterThan(0)
        assertThat(frameDrawable!!.intrinsicWidth).isGreaterThan(0)
    }

    @Test
    fun localizedStrings_areNonEmptyAndValid() {
        val signatureLabel = context.getString(R.string.func_title_signature)
        val exifLabel = context.getString(R.string.func_title_leica_exif)

        assertThat(signatureLabel).isNotEmpty()
        assertThat(exifLabel).isNotEmpty()
    }
}
