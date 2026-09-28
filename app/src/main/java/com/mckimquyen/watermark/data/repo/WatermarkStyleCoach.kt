package com.mckimquyen.watermark.data.repo

import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import javax.inject.Inject

/**
 * IDEA-12: heuristic đếm tần suất thuần — KHÔNG dùng similarity mờ (weighted/fuzzy), chỉ match
 * exact signature (bỏ qua `id`/`timestamp`, 2 field không thuộc "gu"). Thuần Kotlin, không phụ
 * thuộc Android Context, gọi từ [WatermarkStyleHistoryRepository]/`MainViewModel`.
 *
 * `@Inject constructor()` dù không tham số — Hilt luôn tự cấp MỌI tham số constructor của
 * `MainViewModel` (bỏ qua giá trị default Kotlin), nên vẫn cần binding thật.
 */
class WatermarkStyleCoach @Inject constructor() {

    /** `null` nếu chưa đủ [MIN_SAMPLES] dòng lịch sử, hoặc không signature nào đạt [SUGGEST_THRESHOLD]. */
    fun suggest(recent: List<WatermarkStyleHistoryEntity>): WatermarkStyleHistoryEntity? {
        if (recent.size < MIN_SAMPLES) return null
        val (signature, count) = recent
            .groupingBy { it.copy(id = 0, timestamp = 0) } // bỏ 2 field không thuộc signature
            .eachCount()
            .maxByOrNull { it.value } ?: return null
        return signature.takeIf { count.toFloat() / recent.size >= SUGGEST_THRESHOLD }
    }

    /** Tránh gợi ý cái đang dùng sẵn — so sánh bỏ qua `id`/`timestamp`. */
    fun isDifferentFromCurrent(suggestion: WatermarkStyleHistoryEntity, current: WatermarkStyleHistoryEntity): Boolean =
        suggestion.copy(id = 0, timestamp = 0) != current.copy(id = 0, timestamp = 0)

    companion object {
        const val MIN_SAMPLES = 10
        const val SUGGEST_THRESHOLD = 0.6f
    }
}
