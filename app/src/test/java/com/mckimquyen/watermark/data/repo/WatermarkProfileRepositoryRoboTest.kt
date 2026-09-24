package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.Anchor
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.WatermarkLayer
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-06: [WatermarkProfileRepository] — round-trip [WaterMark] <-> [WatermarkProfileEntity]
 * (mọi field áp dụng lại phải khớp hệt bản gốc, trừ `recentIconUris` — cố ý không lưu, xem doc ở
 * [WatermarkProfileEntity]), và save/delete với fake DAO (mirror `BatchHistoryRepositoryRoboTest`).
 * Robolectric vì cần `Uri` thật.
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkProfileRepositoryRoboTest {

    private class FakeWatermarkProfileDao : WatermarkProfileDao {
        val inserted = mutableListOf<WatermarkProfileEntity>()
        val deletedIds = mutableListOf<Long>()
        override fun getAll(): Flow<List<WatermarkProfileEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: WatermarkProfileEntity): Long {
            inserted.add(entity)
            return inserted.size.toLong()
        }
        override suspend fun deleteById(id: Long) {
            deletedIds.add(id)
        }
    }

    private fun sampleWaterMark() = WaterMark(
        text = "Sample watermark",
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
        enableExif = true,
        exifFrameStyle = ExifFrameStyle.MINIMAL.ordinal,
        anchor = Anchor.BOTTOM_RIGHT.ordinal,
        marginPercent = 0.08f,
        exifBandColor = 0x112233,
        exifBandThicknessPercent = 0.12f,
        exifUseSerifCaption = true,
        textEffectStroke = true,
        textEffectShadow = true,
        textEffectPillBackground = true,
        recentIconUris = listOf(Uri.parse("content://media/recent.png")),
        extraLayers = listOf(
            WatermarkLayer(markMode = WaterMarkRepository.MarkMode.Image, iconUri = Uri.parse("content://media/logo.png"))
        )
    )

    @Test
    fun toEntity_thenToWaterMark_roundTripsEveryField_exceptRecentIconUris() {
        val original = sampleWaterMark()

        val entity = WatermarkProfileRepository.toEntity("Instagram", original)
        val restored = WatermarkProfileRepository.toWaterMark(entity)

        assertThat(entity.name).isEqualTo("Instagram")
        assertThat(restored.text).isEqualTo(original.text)
        assertThat(restored.textSize).isEqualTo(original.textSize)
        assertThat(restored.textColor).isEqualTo(original.textColor)
        assertThat(restored.textStyle).isEqualTo(original.textStyle)
        assertThat(restored.textTypeface).isEqualTo(original.textTypeface)
        assertThat(restored.alpha).isEqualTo(original.alpha)
        assertThat(restored.degree).isEqualTo(original.degree)
        assertThat(restored.hGap).isEqualTo(original.hGap)
        assertThat(restored.vGap).isEqualTo(original.vGap)
        assertThat(restored.iconUri).isEqualTo(original.iconUri)
        assertThat(restored.markMode).isEqualTo(original.markMode)
        assertThat(restored.enableBounds).isEqualTo(original.enableBounds)
        assertThat(restored.enableExif).isEqualTo(original.enableExif)
        assertThat(restored.exifFrameStyle).isEqualTo(original.exifFrameStyle)
        assertThat(restored.anchor).isEqualTo(original.anchor)
        assertThat(restored.marginPercent).isEqualTo(original.marginPercent)
        assertThat(restored.exifBandColor).isEqualTo(original.exifBandColor)
        assertThat(restored.exifBandThicknessPercent).isEqualTo(original.exifBandThicknessPercent)
        assertThat(restored.exifUseSerifCaption).isEqualTo(original.exifUseSerifCaption)
        assertThat(restored.textEffectStroke).isEqualTo(original.textEffectStroke)
        assertThat(restored.textEffectShadow).isEqualTo(original.textEffectShadow)
        assertThat(restored.textEffectPillBackground).isEqualTo(original.textEffectPillBackground)
        // Cố ý KHÔNG round-trip — xem doc ở WatermarkProfileEntity.
        assertThat(restored.recentIconUris).isEmpty()
        assertThat(restored.extraLayers).isEqualTo(original.extraLayers)
    }

    @Test
    fun toWaterMark_entityWithNullExtraLayersRaw_returnsEmptyList() {
        // FEAT-03 thêm SAU khi feature profile đã tồn tại — entity lưu trước đó có
        // `extraLayersRaw = null` (default), phải đọc thành list rỗng, không crash.
        val entity = WatermarkProfileRepository.toEntity("legacy", sampleWaterMark()).copy(extraLayersRaw = null)

        val restored = WatermarkProfileRepository.toWaterMark(entity)

        assertThat(restored.extraLayers).isEmpty()
    }

    @Test
    fun toEntity_thenToWaterMark_nullableExifOverrides_roundTripAsNull() {
        val original = sampleWaterMark().copy(
            exifBandColor = null,
            exifBandThicknessPercent = null,
            exifUseSerifCaption = null
        )

        val restored = WatermarkProfileRepository.toWaterMark(WatermarkProfileRepository.toEntity("x", original))

        assertThat(restored.exifBandColor).isNull()
        assertThat(restored.exifBandThicknessPercent).isNull()
        assertThat(restored.exifUseSerifCaption).isNull()
    }

    @Test
    fun toEntity_textMarkMode_roundTripsAsTextMode() {
        val original = sampleWaterMark().copy(markMode = WaterMarkRepository.MarkMode.Text)

        val restored = WatermarkProfileRepository.toWaterMark(WatermarkProfileRepository.toEntity("x", original))

        assertThat(restored.markMode).isEqualTo(WaterMarkRepository.MarkMode.Text)
    }

    @Test
    fun save_insertsMappedEntity() = runBlocking {
        val dao = FakeWatermarkProfileDao()
        val repo = WatermarkProfileRepository(dao)

        repo.save("Instagram", sampleWaterMark())

        assertThat(dao.inserted).hasSize(1)
        assertThat(dao.inserted.single().name).isEqualTo("Instagram")
        Unit
    }

    @Test
    fun delete_delegatesToDaoDeleteById() = runBlocking {
        val dao = FakeWatermarkProfileDao()
        val repo = WatermarkProfileRepository(dao)
        val entity = WatermarkProfileRepository.toEntity("x", sampleWaterMark()).copy(id = 7L)

        repo.delete(entity)

        assertThat(dao.deletedIds).containsExactly(7L)
        Unit
    }
}
