package com.mckimquyen.watermark.data.repo

import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IDEA-12: bọc [WatermarkStyleHistoryDao] — ghi 1 "style signature" mỗi lần batch export thành
 * công + đọc N dòng gần nhất cho [WatermarkStyleCoach].
 */
@Singleton
class WatermarkStyleHistoryRepository @Inject constructor(
    private val dao: WatermarkStyleHistoryDao
) {

    /** Ghi lại + prune giữ tối đa [MAX_ROWS] dòng (đủ cho [WatermarkStyleCoach.MIN_SAMPLES], tránh DB phình vô hạn). */
    suspend fun record(mark: WaterMark) {
        dao.insert(toEntity(mark))
        dao.pruneKeepLatest(MAX_ROWS)
    }

    suspend fun recent(n: Int = MAX_ROWS): List<WatermarkStyleHistoryEntity> = dao.recent(n)

    companion object {
        private const val MAX_ROWS = 50

        internal fun toEntity(mark: WaterMark) = WatermarkStyleHistoryEntity(
            timestamp = System.currentTimeMillis(),
            textColor = mark.textColor,
            textStyleKey = mark.textStyle.serializeKey(),
            textTypefaceKey = mark.textTypeface.serializeKey(),
            alpha = mark.alpha,
            anchor = mark.anchor,
            markModeValue = mark.markMode.value,
            exifFrameStyle = mark.exifFrameStyle,
            textEffectStroke = mark.textEffectStroke,
            textEffectShadow = mark.textEffectShadow,
            textEffectPillBackground = mark.textEffectPillBackground
        )

        /** Signature của [current] để so [WatermarkStyleCoach.isDifferentFromCurrent] — `id`/`timestamp` không có ý nghĩa, để 0. */
        internal fun currentSignature(mark: WaterMark) = toEntity(mark).copy(timestamp = 0)
    }
}
