package com.mckimquyen.watermark.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.mckimquyen.watermark.R

/**
 * FEAT-26: Preset kết hợp 2 lớp watermark đồng thời:
 * - Lớp chính (Primary): Text bản quyền / thương hiệu (neo theo [primaryAnchor])
 * - Lớp phụ (Secondary): Logo / Icon thương hiệu (neo theo [secondaryAnchor])
 */
@Keep
enum class DualWatermarkPreset(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val primaryAnchor: Anchor,
    val secondaryAnchor: Anchor,
    val primaryMarginPercent: Float = DEFAULT_MARGIN_PERCENT,
    val secondaryMarginPercent: Float = DEFAULT_MARGIN_PERCENT,
    val secondaryAlpha: Int = DEFAULT_SECONDARY_ALPHA,
    @DrawableRes val iconRes: Int = R.drawable.ic_func_layers
) {
    /** 1. Thương hiệu & Bản quyền: Logo Top-Right, Text Bottom-Right (chuẩn e-commerce) */
    BRAND_COPYRIGHT(
        id = "brand_copyright",
        titleRes = R.string.dual_preset_brand_copyright_title,
        descRes = R.string.dual_preset_brand_copyright_desc,
        primaryAnchor = Anchor.BOTTOM_RIGHT,
        secondaryAnchor = Anchor.TOP_RIGHT
    ),

    /** 2. Cân bằng đường chéo: Logo Top-Left, Text Bottom-Right */
    DIAGONAL_BALANCE(
        id = "diagonal_balance",
        titleRes = R.string.dual_preset_diagonal_title,
        descRes = R.string.dual_preset_diagonal_desc,
        primaryAnchor = Anchor.BOTTOM_RIGHT,
        secondaryAnchor = Anchor.TOP_LEFT
    ),

    /** 3. Nhãn & Chân trang: Logo Top-Right, Text Bottom-Center */
    STAMP_FOOTER(
        id = "stamp_footer",
        titleRes = R.string.dual_preset_stamp_footer_title,
        descRes = R.string.dual_preset_stamp_footer_desc,
        primaryAnchor = Anchor.BOTTOM_CENTER,
        secondaryAnchor = Anchor.TOP_RIGHT
    ),

    /** 4. Thanh kép chân ảnh: Logo Bottom-Left, Text Bottom-Right */
    BOTTOM_DUAL(
        id = "bottom_dual",
        titleRes = R.string.dual_preset_bottom_dual_title,
        descRes = R.string.dual_preset_bottom_dual_desc,
        primaryAnchor = Anchor.BOTTOM_RIGHT,
        secondaryAnchor = Anchor.BOTTOM_LEFT
    );

    companion object {
        const val DEFAULT_MARGIN_PERCENT: Float = 0.04f
        const val DEFAULT_SECONDARY_ALPHA: Int = 230

        fun fromId(id: String?): DualWatermarkPreset =
            entries.firstOrNull { it.id == id } ?: BRAND_COPYRIGHT
    }
}
