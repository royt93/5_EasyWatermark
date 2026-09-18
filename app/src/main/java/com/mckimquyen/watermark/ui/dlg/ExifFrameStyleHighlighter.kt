package com.mckimquyen.watermark.ui.dlg

import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
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
            val strokeColor = MaterialColors.getColor(
                button,
                if (isSelected) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutlineVariant
            )
            button.strokeColor = ColorStateList.valueOf(strokeColor)
            button.strokeWidth = ((if (isSelected) 2f else 1f) * button.resources.displayMetrics.density).toInt()
        }
    }
}
