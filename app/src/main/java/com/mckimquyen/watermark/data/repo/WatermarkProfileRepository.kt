package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-06: bọc [WatermarkProfileDao] — chuyển đổi 2 chiều [WaterMark] <-> [WatermarkProfileEntity].
 * KHÔNG lưu `recentIconUris` (xem doc ở [WatermarkProfileEntity]).
 */
@Singleton
class WatermarkProfileRepository @Inject constructor(
    private val dao: WatermarkProfileDao
) {

    val profilesFlow: Flow<List<WatermarkProfileEntity>> = dao.getAll()

    suspend fun save(name: String, mark: WaterMark) {
        dao.insert(toEntity(name, mark))
    }

    suspend fun delete(entity: WatermarkProfileEntity) = dao.deleteById(entity.id)

    companion object {
        internal fun toEntity(name: String, mark: WaterMark) = WatermarkProfileEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            text = mark.text,
            textSize = mark.textSize,
            textColor = mark.textColor,
            textStyleKey = mark.textStyle.serializeKey(),
            textTypefaceKey = mark.textTypeface.serializeKey(),
            alpha = mark.alpha,
            degree = mark.degree,
            hGap = mark.hGap,
            vGap = mark.vGap,
            iconUri = mark.iconUri.toString(),
            markModeValue = mark.markMode.value,
            enableBounds = mark.enableBounds,
            enableExif = mark.enableExif,
            exifFrameStyle = mark.exifFrameStyle,
            anchor = mark.anchor,
            marginPercent = mark.marginPercent,
            exifBandColor = mark.exifBandColor,
            exifBandThicknessPercent = mark.exifBandThicknessPercent,
            exifUseSerifCaption = mark.exifUseSerifCaption,
            textEffectStroke = mark.textEffectStroke,
            textEffectShadow = mark.textEffectShadow,
            textEffectPillBackground = mark.textEffectPillBackground
        )

        /**
         * `recentIconUris` luôn `emptyList()` — [WaterMarkRepository.applyWaterMark] không đụng
         * `KEY_RECENT_ICON_URIS` (MRU icon dùng chung toàn app, không phải 1 phần của profile), nên
         * giá trị field này trên object trả về không có tác dụng khi áp dụng.
         */
        internal fun toWaterMark(entity: WatermarkProfileEntity) = WaterMark(
            text = entity.text,
            textSize = entity.textSize,
            textColor = entity.textColor,
            textStyle = TextPaintStyle.obtainSealedClass(entity.textStyleKey),
            textTypeface = TextTypeface.obtainSealedClass(entity.textTypefaceKey),
            alpha = entity.alpha,
            degree = entity.degree,
            hGap = entity.hGap,
            vGap = entity.vGap,
            iconUri = Uri.parse(entity.iconUri),
            markMode = if (entity.markModeValue == WaterMarkRepository.MarkMode.Image.value) {
                WaterMarkRepository.MarkMode.Image
            } else {
                WaterMarkRepository.MarkMode.Text
            },
            enableBounds = entity.enableBounds,
            enableExif = entity.enableExif,
            exifFrameStyle = entity.exifFrameStyle,
            anchor = entity.anchor,
            marginPercent = entity.marginPercent,
            exifBandColor = entity.exifBandColor,
            exifBandThicknessPercent = entity.exifBandThicknessPercent,
            exifUseSerifCaption = entity.exifUseSerifCaption,
            textEffectStroke = entity.textEffectStroke,
            textEffectShadow = entity.textEffectShadow,
            textEffectPillBackground = entity.textEffectPillBackground,
            recentIconUris = emptyList()
        )
    }
}
