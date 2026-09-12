package com.mckimquyen.watermark.data.model

import android.net.Uri
import androidx.annotation.Keep
import com.mckimquyen.watermark.data.repo.WaterMarkRepository

@Keep
data class WaterMark(
    val text: String,
    val textSize: Float,
    val textColor: Int,
    val textStyle: TextPaintStyle,
    val textTypeface: TextTypeface,
    val alpha: Int,
    val degree: Float,
    val hGap: Int,
    val vGap: Int,
    val iconUri: Uri,
    val markMode: WaterMarkRepository.MarkMode,
    val enableBounds: Boolean,
    val enableExif: Boolean = false,
    val exifFrameStyle: Int = ExifFrameStyle.CLASSIC.ordinal,
    val anchor: Int = Anchor.CENTER.ordinal,
    val marginPercent: Float = 0.05f,
    /** FEAT-14 Custom Frame Builder — null = dùng màu/tỉ lệ/font mặc định của style đang chọn. */
    val exifBandColor: Int? = null,
    val exifBandThicknessPercent: Float? = null,
    val exifUseSerifCaption: Boolean? = null,
    /** FEAT-11 — viền/bóng/nền pill cho text watermark, độc lập, kết hợp tự do. */
    val textEffectStroke: Boolean = false,
    val textEffectShadow: Boolean = false,
    val textEffectPillBackground: Boolean = false
)
