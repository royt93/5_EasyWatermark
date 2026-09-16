package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-13: [BatchCaptionBSDialogFragment] ép kiểu `requireContext() as MainActivity` trực tiếp
 * (giống [SaveImageBSDialogFragment]) — launch thật không khả thi trong JVM test của repo này
 * (xem giải thích chi tiết ở [DlgSaveFileLayoutRoboTest]). Test dưới đây kiểm chứng trực tiếp cấu
 * trúc layout đã inflate (không qua Fragment) — logic validate/apply thật đã test riêng ở
 * `BatchCaptionParserTest`/`WaterMarkRepositoryCaptionRoboTest`/`MainViewModelBatchCaptionRoboTest`.
 */
@RunWith(RobolectricTestRunner::class)
class BatchCaptionLayoutRoboTest {

    private fun inflateRoot(): View {
        val context: Context = ApplicationProvider.getApplicationContext()
        val themed = ContextThemeWrapper(context, R.style.Theme_MyApp)
        val parent = FrameLayout(themed)
        return LayoutInflater.from(themed).inflate(R.layout.f_batch_caption_bottom_sheet, parent, false)
    }

    @Test
    fun requiredViews_existInHierarchy() {
        val root = inflateRoot()

        assertThat(root.findViewById<View>(R.id.etCaptions)).isNotNull()
        assertThat(root.findViewById<View>(R.id.tilCaptions)).isNotNull()
        assertThat(root.findViewById<View>(R.id.btnApplyCaptions)).isNotNull()
        assertThat(root.findViewById<View>(R.id.tvSubtitle)).isNotNull()
    }

    @Test
    fun captionInput_supportsMultilineText() {
        val root = inflateRoot()
        val editText = root.findViewById<TextInputEditText>(R.id.etCaptions)

        assertThat(editText.isSingleLine).isFalse()
    }

    @Test
    fun captionInput_usesExpectedHint() {
        val root = inflateRoot()
        val til = root.findViewById<TextInputLayout>(R.id.tilCaptions)

        assertThat(til.hint.toString()).isEqualTo(root.context.getString(R.string.batch_caption_hint))
    }
}
