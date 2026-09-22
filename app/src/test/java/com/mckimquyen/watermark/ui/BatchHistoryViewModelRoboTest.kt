package com.mckimquyen.watermark.ui

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestUserDataStore
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-04 AC3 "chạy lại": [BatchHistoryViewModel.restoreEntry] phải khôi phục ĐÚNG danh sách ảnh
 * input + toàn bộ cấu hình export đã lưu trong 1 [BatchHistoryEntity] — dùng DataStore cô lập
 * ([newTestWaterMarkDataStore]/[newTestUserDataStore]) thay vì singleton thật, xem lý do ở
 * `testutil/TestDataStores.kt`.
 */
@RunWith(RobolectricTestRunner::class)
class BatchHistoryViewModelRoboTest {

    private class NoopBatchHistoryDao : BatchHistoryDao {
        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())
        override suspend fun insert(entity: BatchHistoryEntity): Long = 0
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun trimOldest(keepCount: Int) = Unit
    }

    private fun entry() = BatchHistoryEntity(
        id = 1L,
        timestamp = 1_000L,
        inputUris = "content://media/1\ncontent://media/2",
        outputUris = "content://media/1",
        failedInputUris = "content://media/2",
        outputFormatOrdinal = android.graphics.Bitmap.CompressFormat.PNG.ordinal,
        // UserConfigRepository chỉ chấp nhận compressLevel là bội số của 20 (xem
        // `userPreferences` map: `savedValue % 20 != 0` → fallback DEFAULT_COMPRESS_LEVEL).
        compressLevel = 60,
        maxOutputLongEdge = 1600,
        copyright = "roy93",
        outputNamePattern = "{filename}_v2",
        conflictPolicyId = ConflictPolicy.OVERWRITE.id,
        outputDirectoryUri = "content://tree/custom-dir"
    )

    @Test
    fun restoreEntry_restoresImageListAndAllExportSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val userConfigRepo = UserConfigRepository(newTestUserDataStore(context))
        val batchHistoryRepo = BatchHistoryRepository(NoopBatchHistoryDao())
        val viewModel = BatchHistoryViewModel(batchHistoryRepo, waterMarkRepo, userConfigRepo)

        viewModel.restoreEntry(entry())

        assertThat(waterMarkRepo.imageInfoList.map { it.uri })
            .containsExactly(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
            .inOrder()
        val prefs = userConfigRepo.userPreferences.first()
        assertThat(prefs.outputFormat).isEqualTo(android.graphics.Bitmap.CompressFormat.PNG)
        assertThat(prefs.compressLevel).isEqualTo(60)
        assertThat(prefs.maxOutputLongEdge).isEqualTo(1600)
        assertThat(prefs.copyright).isEqualTo("roy93")
        assertThat(prefs.outputNamePattern).isEqualTo("{filename}_v2")
        assertThat(prefs.conflictPolicy).isEqualTo(ConflictPolicy.OVERWRITE)
        assertThat(prefs.outputDirectoryUri).isEqualTo(Uri.parse("content://tree/custom-dir"))
    }

    @Test
    fun restoreEntry_nullOutputDirectoryUri_clearsCustomDirectory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        val userConfigRepo = UserConfigRepository(newTestUserDataStore(context))
        userConfigRepo.updateOutputDirectoryUri(Uri.parse("content://tree/old"))
        val batchHistoryRepo = BatchHistoryRepository(NoopBatchHistoryDao())
        val viewModel = BatchHistoryViewModel(batchHistoryRepo, waterMarkRepo, userConfigRepo)

        viewModel.restoreEntry(entry().copy(outputDirectoryUri = null))

        assertThat(userConfigRepo.userPreferences.first().outputDirectoryUri).isNull()
    }
}
