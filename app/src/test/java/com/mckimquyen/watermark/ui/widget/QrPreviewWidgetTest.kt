package com.mckimquyen.watermark.ui.widget

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.utils.QrCodeGenerator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Widget test (Robolectric): inflate layout QR bottom sheet, đẩy QR bitmap vào preview ImageView
 * và xác nhận view + binding hoạt động.
 */
@RunWith(RobolectricTestRunner::class)
class QrPreviewWidgetTest {

    private fun inflate(): View {
        val themed = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MyApp
        )
        return LayoutInflater.from(themed).inflate(R.layout.f_qr_code_bottom_sheet, null, false)
    }

    @Test
    fun layout_inflates_withPreviewImageView() {
        val root = inflate()
        val preview = root.findViewById<ImageView>(R.id.ivPreview)
        assertThat(preview).isNotNull()
        assertThat(preview.drawable).isNull()
    }

    @Test
    fun preview_showsDrawable_afterSettingQrBitmap() {
        val root = inflate()
        val preview = root.findViewById<ImageView>(R.id.ivPreview)
        val qr = QrCodeGenerator.generate("https://example.com", size = 200)
        assertThat(qr).isNotNull()

        preview.setImageBitmap(qr)

        assertThat(preview.drawable).isNotNull()
        assertThat(preview.drawable.intrinsicWidth).isGreaterThan(0)
    }
}
