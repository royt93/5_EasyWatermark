package com.mckimquyen.watermark.data.repo

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import org.junit.Test

/**
 * IDEA-12: [WatermarkStyleCoach] thuần Kotlin, không đụng Android — JUnit thường, không cần
 * Robolectric. Bám đúng "Thiết kế kỹ thuật" ở ticket (MIN_SAMPLES=10, SUGGEST_THRESHOLD=0.6f,
 * so sánh exact signature, không similarity mờ).
 */
class WatermarkStyleCoachTest {

    private val coach = WatermarkStyleCoach()

    private fun signature(
        textColor: Int = 1,
        anchor: Int = 0,
        alpha: Int = 100
    ) = WatermarkStyleHistoryEntity(
        id = 0,
        timestamp = 0L,
        textColor = textColor,
        textStyleKey = 0,
        textTypefaceKey = 0,
        alpha = alpha,
        anchor = anchor,
        markModeValue = 0,
        exifFrameStyle = 0,
        textEffectStroke = false,
        textEffectShadow = false,
        textEffectPillBackground = false
    )

    /** id/timestamp khác nhau mỗi dòng lịch sử thật, nhưng KHÔNG thuộc "signature" — chỉ 2 field còn lại quyết định nhóm. */
    private fun historyRow(seq: Long, base: WatermarkStyleHistoryEntity) = base.copy(id = seq, timestamp = seq * 1000L)

    @Test
    fun suggest_atLeast10Samples_60PercentSameSignature_returnsThatSignature() {
        val majority = signature(textColor = 1)
        val minority = signature(textColor = 2)
        // 6/10 = 60% đúng ngưỡng
        val recent = (1..6).map { historyRow(it.toLong(), majority) } + (7..10).map { historyRow(it.toLong(), minority) }

        val result = coach.suggest(recent)

        assertThat(result?.copy(id = 0, timestamp = 0)).isEqualTo(majority)
    }

    @Test
    fun suggest_below60Percent_returnsNull() {
        val majority = signature(textColor = 1)
        val minority = signature(textColor = 2)
        // 5/10 = 50% < 60%
        val recent = (1..5).map { historyRow(it.toLong(), majority) } + (6..10).map { historyRow(it.toLong(), minority) }

        assertThat(coach.suggest(recent)).isNull()
    }

    @Test
    fun suggest_fewerThan10Samples_returnsNull() {
        val majority = signature(textColor = 1)
        val recent = (1..9).map { historyRow(it.toLong(), majority) }

        assertThat(coach.suggest(recent)).isNull()
    }

    @Test
    fun suggest_manyDistinctSignatures_picksOnlyTheOneOverThreshold_noMixing() {
        val a = signature(textColor = 1)
        val b = signature(textColor = 2)
        val c = signature(textColor = 3)
        // 6 a + 2 b + 2 c = 10 dòng, không signature nào khác trộn vào a (60%)
        val recent = (1..6).map { historyRow(it.toLong(), a) } +
            (7..8).map { historyRow(it.toLong(), b) } +
            (9..10).map { historyRow(it.toLong(), c) }

        val result = coach.suggest(recent)

        assertThat(result?.copy(id = 0, timestamp = 0)).isEqualTo(a)
    }

    @Test
    fun isDifferentFromCurrent_sameSignatureIgnoringIdAndTimestamp_returnsFalse() {
        val suggestion = signature(textColor = 1).copy(id = 5, timestamp = 999L)
        val current = signature(textColor = 1).copy(id = 0, timestamp = 0L)

        assertThat(coach.isDifferentFromCurrent(suggestion, current)).isFalse()
    }

    @Test
    fun isDifferentFromCurrent_differentSignature_returnsTrue() {
        val suggestion = signature(textColor = 1)
        val current = signature(textColor = 2)

        assertThat(coach.isDifferentFromCurrent(suggestion, current)).isTrue()
    }
}
