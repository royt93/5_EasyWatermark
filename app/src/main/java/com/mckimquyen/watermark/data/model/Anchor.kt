package com.mckimquyen.watermark.data.model

/**
 * Preset neo vị trí watermark theo lưới 3x3, dùng khi tileMode = CLAMP.
 * [toOffset] quy đổi sang offsetX/offsetY chuẩn hóa 0..1 mà [ImageInfo] đang dùng,
 * trừ đi kích thước watermark (wmFracW/wmFracH, tỉ lệ so với drawableBounds) để
 * cạnh phải/dưới không bị tràn mép ảnh.
 */
enum class Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    fun toOffset(marginPercent: Float, wmFracW: Float, wmFracH: Float): Pair<Float, Float> {
        val offsetX = when (this) {
            TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> marginPercent
            TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5f - wmFracW / 2f
            else -> 1f - marginPercent - wmFracW
        }
        val offsetY = when (this) {
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> marginPercent
            CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5f - wmFracH / 2f
            else -> 1f - marginPercent - wmFracH
        }
        return offsetX.coerceIn(0f, 1f) to offsetY.coerceIn(0f, 1f)
    }

    companion object {
        fun obtain(ordinal: Int): Anchor = entries.getOrElse(ordinal) { CENTER }
    }
}
