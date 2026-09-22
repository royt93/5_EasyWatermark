package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.export.BatchExportEngine
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-04: [BatchHistoryRepository] với fake [BatchHistoryDao] (mirror [TemplateRepositoryCorruptedDbTest])
 * — Robolectric vì cần `Uri` thật cho encode/decode (xem [BatchHistoryRepositoryEncodingRoboTest]).
 */
@RunWith(RobolectricTestRunner::class)
class BatchHistoryRepositoryRoboTest {

    private class FakeBatchHistoryDao : BatchHistoryDao {
        val inserted = mutableListOf<BatchHistoryEntity>()
        val trimCalls = mutableListOf<Int>()
        val deletedIds = mutableListOf<Long>()

        override fun getAll(): Flow<List<BatchHistoryEntity>> = flowOf(emptyList())

        override suspend fun insert(entity: BatchHistoryEntity): Long {
            inserted.add(entity)
            return inserted.size.toLong()
        }

        override suspend fun deleteById(id: Long) {
            deletedIds.add(id)
        }

        override suspend fun trimOldest(keepCount: Int) {
            trimCalls.add(keepCount)
        }
    }

    private fun settings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val waterMarkRepo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        BatchExportEngine.ExportSettings(
            config = waterMarkRepo.waterMark.first(),
            outputFormat = android.graphics.Bitmap.CompressFormat.PNG,
            compressLevel = 90,
            maxOutputLongEdge = 2048,
            copyright = "roy93",
            outputNamePattern = "{filename}_wm",
            conflictPolicy = ConflictPolicy.RENAME_VERSION,
            outputDirectoryUri = Uri.parse("content://tree/custom")
        )
    }

    @Test
    fun record_nonEmptyInput_insertsEntityWithSettingsMapped_thenTrims() = runBlocking {
        val dao = FakeBatchHistoryDao()
        val repo = BatchHistoryRepository(dao)
        val input = listOf(Uri.parse("content://media/1"), Uri.parse("content://media/2"))
        val output = listOf(Uri.parse("content://media/1"))
        val failed = listOf(Uri.parse("content://media/2"))

        repo.record(input, output, failed, settings())

        assertThat(dao.inserted).hasSize(1)
        val entity = dao.inserted.single()
        assertThat(BatchHistoryRepository.decodeUriList(entity.inputUris)).containsExactlyElementsIn(input)
        assertThat(BatchHistoryRepository.decodeUriList(entity.outputUris)).containsExactlyElementsIn(output)
        assertThat(BatchHistoryRepository.decodeUriList(entity.failedInputUris)).containsExactlyElementsIn(failed)
        assertThat(entity.outputFormatOrdinal).isEqualTo(android.graphics.Bitmap.CompressFormat.PNG.ordinal)
        assertThat(entity.compressLevel).isEqualTo(90)
        assertThat(entity.maxOutputLongEdge).isEqualTo(2048)
        assertThat(entity.copyright).isEqualTo("roy93")
        assertThat(entity.outputNamePattern).isEqualTo("{filename}_wm")
        assertThat(entity.conflictPolicyId).isEqualTo(ConflictPolicy.RENAME_VERSION.id)
        assertThat(entity.outputDirectoryUri).isEqualTo("content://tree/custom")
        assertThat(dao.trimCalls).containsExactly(BatchHistoryRepository.MAX_HISTORY_ENTRIES)
        Unit
    }

    @Test
    fun record_emptyInput_doesNotInsert() = runBlocking {
        val dao = FakeBatchHistoryDao()
        val repo = BatchHistoryRepository(dao)

        repo.record(emptyList(), emptyList(), emptyList(), settings())

        assertThat(dao.inserted).isEmpty()
        assertThat(dao.trimCalls).isEmpty()
    }

    @Test
    fun delete_delegatesToDaoDeleteById() = runBlocking {
        val dao = FakeBatchHistoryDao()
        val repo = BatchHistoryRepository(dao)
        val entry = BatchHistoryEntity(
            id = 42L,
            timestamp = 0L,
            inputUris = "",
            outputUris = "",
            failedInputUris = "",
            outputFormatOrdinal = 0,
            compressLevel = 90,
            maxOutputLongEdge = 0,
            copyright = "",
            outputNamePattern = "",
            conflictPolicyId = ConflictPolicy.KEEP_BOTH.id
        )

        repo.delete(entry)

        assertThat(dao.deletedIds).containsExactly(42L)
        Unit
    }
}
