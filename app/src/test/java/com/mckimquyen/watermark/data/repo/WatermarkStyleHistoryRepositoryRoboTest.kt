package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-12: [WatermarkStyleHistoryRepository] — mapping [WaterMark] -> [WatermarkStyleHistoryEntity]
 * (chỉ "style signature", KHÔNG text/iconUri) + record/recent với fake DAO. Robolectric vì cần
 * `Uri` thật (mirror `WatermarkProfileRepositoryRoboTest`).
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkStyleHistoryRepositoryRoboTest {

    private class FakeDao : WatermarkStyleHistoryDao {
        val inserted = mutableListOf<WatermarkStyleHistoryEntity>()
        var prunedKeep: Int? = null
        var recentRequested: Int? = null
        var recentResult: List<WatermarkStyleHistoryEntity> = emptyList()

        override suspend fun insert(entity: WatermarkStyleHistoryEntity): Long {
            inserted.add(entity)
            return inserted.size.toLong()
        }

        override suspend fun recent(n: Int): List<WatermarkStyleHistoryEntity> {
            recentRequested = n
            return recentResult
        }

        override suspend fun pruneKeepLatest(keep: Int) {
            prunedKeep = keep
        }
    }

    private fun sampleWaterMark() = WaterMark(
        text = "will not be saved",
        textSize = 18f,
        textColor = 0xFF00FF,
        textStyle = TextPaintStyle.Stroke,
        textTypeface = TextTypeface.Bold,
        alpha = 200,
        degree = 45f,
        hGap = 30,
        vGap = 40,
        iconUri = Uri.parse("content://media/icon.png"),
        markMode = WaterMarkRepository.MarkMode.Image,
        enableBounds = true,
        exifFrameStyle = ExifFrameStyle.MINIMAL.ordinal,
        anchor = Anchor.BOTTOM_RIGHT.ordinal,
        textEffectStroke = true,
        textEffectShadow = true,
        textEffectPillBackground = true
    )

    @Test
    fun toEntity_mapsOnlyStyleSignatureFields_notTextOrIcon() {
        val entity = WatermarkStyleHistoryRepository.toEntity(sampleWaterMark())

        assertThat(entity.textColor).isEqualTo(sampleWaterMark().textColor)
        assertThat(entity.textStyleKey).isEqualTo(sampleWaterMark().textStyle.serializeKey())
        assertThat(entity.textTypefaceKey).isEqualTo(sampleWaterMark().textTypeface.serializeKey())
        assertThat(entity.alpha).isEqualTo(sampleWaterMark().alpha)
        assertThat(entity.anchor).isEqualTo(sampleWaterMark().anchor)
        assertThat(entity.markModeValue).isEqualTo(sampleWaterMark().markMode.value)
        assertThat(entity.exifFrameStyle).isEqualTo(sampleWaterMark().exifFrameStyle)
        assertThat(entity.textEffectStroke).isEqualTo(sampleWaterMark().textEffectStroke)
        assertThat(entity.textEffectShadow).isEqualTo(sampleWaterMark().textEffectShadow)
        assertThat(entity.textEffectPillBackground).isEqualTo(sampleWaterMark().textEffectPillBackground)
    }

    @Test
    fun record_insertsMappedEntity_thenPrunesKeepingMax50() = runBlocking {
        val dao = FakeDao()
        val repo = WatermarkStyleHistoryRepository(dao)

        repo.record(sampleWaterMark())

        assertThat(dao.inserted).hasSize(1)
        assertThat(dao.inserted.single().textColor).isEqualTo(sampleWaterMark().textColor)
        assertThat(dao.prunedKeep).isEqualTo(50)
        Unit
    }

    @Test
    fun recent_delegatesToDao() = runBlocking {
        val dao = FakeDao()
        val expected = listOf(WatermarkStyleHistoryRepository.toEntity(sampleWaterMark()))
        dao.recentResult = expected
        val repo = WatermarkStyleHistoryRepository(dao)

        val result = repo.recent(10)

        assertThat(dao.recentRequested).isEqualTo(10)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun currentSignature_ignoresTimestamp_matchesRecordedEntitySignature() {
        val recorded = WatermarkStyleHistoryRepository.toEntity(sampleWaterMark()).copy(id = 3L, timestamp = 12345L)

        val current = WatermarkStyleHistoryRepository.currentSignature(sampleWaterMark())

        assertThat(WatermarkStyleCoach().isDifferentFromCurrent(recorded, current)).isFalse()
    }
}
