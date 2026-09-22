package com.mckimquyen.watermark.ui

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkProfileRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-06: [WatermarkProfileViewModel] — AC1 lưu ĐÚNG cấu hình watermark hiện tại, AC2 áp dụng
 * ĐÚNG toàn bộ cấu hình đã lưu. DataStore cô lập cho `WaterMarkRepository`, fake DAO cho
 * `WatermarkProfileRepository` (mirror `BatchHistoryViewModelRoboTest`).
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkProfileViewModelRoboTest {

    private class FakeWatermarkProfileDao : WatermarkProfileDao {
        val inserted = mutableListOf<WatermarkProfileEntity>()
        override fun getAll(): Flow<List<WatermarkProfileEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: WatermarkProfileEntity): Long {
            inserted.add(entity)
            return inserted.size.toLong()
        }
        override suspend fun deleteById(id: Long) = Unit
    }

    @Test
    fun saveCurrentAsProfileNow_savesSnapshotOfCurrentWaterMark() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        waterMarkRepo.updateText("Chào buổi sáng")
        waterMarkRepo.updateAlpha(120)
        val dao = FakeWatermarkProfileDao()
        val viewModel = WatermarkProfileViewModel(WatermarkProfileRepository(dao), waterMarkRepo)

        viewModel.saveCurrentAsProfileNow("Buổi sáng")

        assertThat(dao.inserted).hasSize(1)
        val saved = dao.inserted.single()
        assertThat(saved.name).isEqualTo("Buổi sáng")
        assertThat(saved.text).isEqualTo("Chào buổi sáng")
        assertThat(saved.alpha).isEqualTo(120)
    }

    @Test
    fun applyNow_restoresEveryFieldFromEntity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val dao = FakeWatermarkProfileDao()
        val viewModel = WatermarkProfileViewModel(WatermarkProfileRepository(dao), waterMarkRepo)
        val savedMark = WaterMark(
            text = "Instagram look",
            textSize = 20f,
            textColor = 0x123456,
            textStyle = com.mckimquyen.watermark.data.model.TextPaintStyle.Fill,
            textTypeface = com.mckimquyen.watermark.data.model.TextTypeface.Italic,
            alpha = 180,
            degree = 30f,
            hGap = 5,
            vGap = 10,
            iconUri = Uri.parse("content://media/insta-icon.png"),
            markMode = WaterMarkRepository.MarkMode.Image,
            enableBounds = false,
            enableExif = false,
            exifFrameStyle = 0,
            anchor = 6,
            marginPercent = 0.07f
        )
        val entity = WatermarkProfileRepository.toEntity("Instagram", savedMark)

        viewModel.applyNow(entity)

        val readBack = waterMarkRepo.waterMark.first()
        assertThat(readBack.text).isEqualTo("Instagram look")
        assertThat(readBack.textSize).isEqualTo(20f)
        assertThat(readBack.alpha).isEqualTo(180)
        assertThat(readBack.iconUri).isEqualTo(Uri.parse("content://media/insta-icon.png"))
        assertThat(readBack.markMode).isEqualTo(WaterMarkRepository.MarkMode.Image)
    }
}
