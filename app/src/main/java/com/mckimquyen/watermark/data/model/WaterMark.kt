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
    val textEffectPillBackground: Boolean = false,
    /** FEAT-24: N icon/logo gần đây nhất user đã chọn, mới nhất đứng đầu — cho quick-pick. */
    val recentIconUris: List<Uri> = emptyList(),
    /**
     * FEAT-03: lớp watermark PHỤ, chồng thêm ngoài layer chính (các field trên) — tối đa
     * [WaterMarkRepository.MAX_EXTRA_LAYERS]. index 0 vẽ trước (dưới cùng), index cuối vẽ sau
     * (trên cùng) — z-order = thứ tự trong list.
     */
    val extraLayers: List<WatermarkLayer> = emptyList(),
    /**
     * IDEA-06 — chỉ áp dụng cho layer CHÍNH ở [WaterMarkRepository.MarkMode.Text]: tự đảo
     * [textColor] đen/trắng + nâng sàn [alpha] theo độ sáng vùng ảnh dưới watermark, KHÔNG ghi đè
     * [textColor]/[alpha] đang lưu — chỉ ảnh hưởng bản render hiện tại (xem
     * `WaterMarkImageView.applyAutoContrastIfEnabled`).
     */
    val autoContrastEnabled: Boolean = false
)
