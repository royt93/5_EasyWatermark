package com.mckimquyen.watermark.ui.dlg

import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.model.ExifFrameStyle

/**
 * Tô viền nút style đang chọn trong [ExifPbFragment] — tách khỏi Fragment để test được bằng
 * Robolectric mà không cần Activity/Hilt (theo cách [com.mckimquyen.watermark.utils.TextTokenResolver]
 * được tách ra để unit test thuần).
 */
object ExifFrameStyleHighlighter {

    fun apply(buttons: Map<ExifFrameStyle, MaterialButton>, selected: ExifFrameStyle) {
        buttons.forEach { (style, button) ->
            val isSelected = style == selected
            val strokeColorRes = if (isSelected) R.color.glass_text_primary else R.color.glass_border
            button.strokeColor = ContextCompat.getColorStateList(button.context, strokeColorRes)
            button.strokeWidth = ((if (isSelected) 2f else 1f) * button.resources.displayMetrics.density).toInt()
        }
    }
}
