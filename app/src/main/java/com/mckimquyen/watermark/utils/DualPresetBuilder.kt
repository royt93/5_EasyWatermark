package com.mckimquyen.watermark.utils

import com.mckimquyen.watermark.data.model.DualWatermarkPreset
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.repo.WaterMarkRepository

/**
 * FEAT-26: Logic áp dụng mẫu dấu kép (Dual Preset) lên cấu hình [WaterMark].
 * Tận dụng hạ tầng đa lớp FEAT-03 ([WatermarkLayer]).
 */
object DualPresetBuilder {

    const val DEFAULT_FALLBACK_TEXT = "Watermark Creator"

    /**
     * Tạo cấu hình [WaterMark] mới bằng cách gắn layer chính (Text) theo [preset.primaryAnchor]
     * và layer phụ (Image) theo [preset.secondaryAnchor].
     */
    fun applyPreset(
        current: WaterMark,
        preset: DualWatermarkPreset,
        defaultText: String? = null
    ): WaterMark {
        val resolvedText = when {
            current.text.isNotBlank() -> current.text
            !defaultText.isNullOrBlank() -> defaultText
            else -> DEFAULT_FALLBACK_TEXT
        }

        val secondaryLayer = WatermarkLayer(
            markMode = WaterMarkRepository.MarkMode.Image,
            iconUri = current.iconUri,
            anchor = preset.secondaryAnchor.ordinal,
            marginPercent = preset.secondaryMarginPercent,
            alpha = preset.secondaryAlpha
        )

        // Giữ các layer phụ hiện có khác nếu có, nhưng thay thế hoặc đặt secondaryLayer lên đầu
        val existingExtraLayers = current.extraLayers
        val newExtraLayers = if (existingExtraLayers.isEmpty()) {
            listOf(secondaryLayer)
        } else {
            // Thay thế layer phụ đầu tiên bằng secondaryLayer, giữ các layer còn lại
            val mutable = existingExtraLayers.toMutableList()
            mutable[0] = secondaryLayer
            mutable.take(WaterMarkRepository.MAX_EXTRA_LAYERS)
        }

        return current.copy(
            markMode = WaterMarkRepository.MarkMode.Text,
            text = resolvedText,
            anchor = preset.primaryAnchor.ordinal,
            marginPercent = preset.primaryMarginPercent,
            extraLayers = newExtraLayers
        )
    }
}
