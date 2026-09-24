package com.mckimquyen.watermark.data.model

import android.graphics.Color
import android.net.Uri
import androidx.annotation.Keep
import com.mckimquyen.watermark.data.repo.WaterMarkRepository

/**
 * FEAT-03: 1 lớp watermark PHỤ (chồng thêm ngoài layer chính vốn là các field gốc của
 * [WaterMark]) — chỉ giữ tập field tối thiểu đủ cho vị trí/opacity/thứ tự riêng, CHỦ Ý không có
 * `textEffect*`/EXIF (những field đó chỉ có ý nghĩa cho layer chính, giữ layer phụ đơn giản để
 * hạn chế phình UI). Vị trí dùng neo 9-grid + margin% ([Anchor.toOffset]) — KHÔNG kéo-thả tay tự
 * do như layer chính, tránh phải viết lại hệ thống touch/gesture của `WaterMarkImageView`.
 */
@Keep
data class WatermarkLayer(
    val markMode: WaterMarkRepository.MarkMode,
    val text: String = "",
    val textSize: Float = WaterMarkRepository.DEFAULT_TEXT_SIZE,
    val textColor: Int = Color.WHITE,
    val textStyle: TextPaintStyle = TextPaintStyle.obtainSealedClass(0),
    val textTypeface: TextTypeface = TextTypeface.obtainSealedClass(0),
    val iconUri: Uri = Uri.EMPTY,
    val alpha: Int = 255,
    val degree: Float = 0f,
    val hGap: Int = 0,
    val vGap: Int = 0,
    val anchor: Int = Anchor.CENTER.ordinal,
    val marginPercent: Float = WaterMarkRepository.DEFAULT_MARGIN_PERCENT
) {
    /**
     * Build 1 [WaterMark] đầy đủ độc lập để tái dùng thẳng
     * `WaterMarkImageView.buildTextBitmapShader`/`buildIconBitmapShader` (2 hàm này chỉ nhận
     * `config: WaterMark`, không có overload cho [WatermarkLayer]). Mọi field ngoài tập của
     * layer phụ dùng default an toàn (không bounds debug, không EXIF, không text effect).
     */
    fun toWaterMark(): WaterMark = WaterMark(
        text = text,
        textSize = textSize,
        textColor = textColor,
        textStyle = textStyle,
        textTypeface = textTypeface,
        alpha = alpha,
        degree = degree,
        hGap = hGap,
        vGap = vGap,
        iconUri = iconUri,
        markMode = markMode,
        enableBounds = false,
        anchor = anchor,
        marginPercent = marginPercent
    )

    companion object {
        /** Uri.encode() escape hết "|"/"\n" thật trong field — an toàn làm delimiter. */
        private const val FIELD_DELIMITER = "|"
        private const val LAYER_DELIMITER = "\n"

        /**
         * Encode danh sách layer thành 1 chuỗi để lưu DataStore/Room (`String`/`String?`).
         * `text` là free-text CÓ THỂ chứa `\n`/`|` (khác Uri, xem
         * [WaterMarkRepository.parseRecentIconUris]) nên PHẢI percent-encode từng field bằng
         * [Uri.encode] trước khi join, không thể join thẳng như danh sách Uri.
         */
        fun serializeList(layers: List<WatermarkLayer>): String =
            layers.joinToString(LAYER_DELIMITER) { layer ->
                listOf(
                    layer.markMode.value.toString(),
                    layer.text,
                    layer.textSize.toString(),
                    layer.textColor.toString(),
                    layer.textStyle.serializeKey().toString(),
                    layer.textTypeface.serializeKey().toString(),
                    layer.iconUri.toString(),
                    layer.alpha.toString(),
                    layer.degree.toString(),
                    layer.hGap.toString(),
                    layer.vGap.toString(),
                    layer.anchor.toString(),
                    layer.marginPercent.toString()
                ).joinToString(FIELD_DELIMITER) { Uri.encode(it) }
            }

        /** Hàm thuần — parse ngược [serializeList], bỏ qua layer hỏng định dạng (không throw). */
        fun parseList(raw: String?): List<WatermarkLayer> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(LAYER_DELIMITER)
                .filter { it.isNotBlank() }
                .mapNotNull { parseOne(it) }
        }

        private fun parseOne(entry: String): WatermarkLayer? {
            val fields = entry.split(FIELD_DELIMITER).map { Uri.decode(it) }
            if (fields.size != FIELD_COUNT) return null
            return WatermarkLayer(
                markMode = if (fields[0].toIntOrNull() == WaterMarkRepository.MarkMode.Image.value) {
                    WaterMarkRepository.MarkMode.Image
                } else {
                    WaterMarkRepository.MarkMode.Text
                },
                text = fields[1],
                textSize = fields[2].toFloatOrNull() ?: WaterMarkRepository.DEFAULT_TEXT_SIZE,
                textColor = fields[3].toIntOrNull() ?: Color.WHITE,
                textStyle = TextPaintStyle.obtainSealedClass(fields[4].toIntOrNull() ?: 0),
                textTypeface = TextTypeface.obtainSealedClass(fields[5].toIntOrNull() ?: 0),
                iconUri = Uri.parse(fields[6]),
                alpha = fields[7].toIntOrNull() ?: 255,
                degree = fields[8].toFloatOrNull() ?: 0f,
                hGap = fields[9].toIntOrNull() ?: 0,
                vGap = fields[10].toIntOrNull() ?: 0,
                anchor = fields[11].toIntOrNull() ?: Anchor.CENTER.ordinal,
                marginPercent = fields[12].toFloatOrNull() ?: WaterMarkRepository.DEFAULT_MARGIN_PERCENT
            )
        }

        private const val FIELD_COUNT = 13
    }
}
