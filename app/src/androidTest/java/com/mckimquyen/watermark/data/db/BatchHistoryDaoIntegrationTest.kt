package com.mckimquyen.watermark.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-04: kiểm chứng [BatchHistoryDao] với Room DB in-memory thật, mirror
 * [TemplateDaoIntegrationTest] (Room DAO test dành cho androidTest theo quy ước repo này).
 */
@RunWith(AndroidJUnit4::class)
class BatchHistoryDaoIntegrationTest {

    private lateinit var db: BatchHistoryDatabase
    private lateinit var dao: BatchHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, BatchHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.batchHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(timestamp: Long) = BatchHistoryEntity(
        id = 0,
        timestamp = timestamp,
        inputUris = "content://media/1",
        outputUris = "content://media/1",
        failedInputUris = "",
        outputFormatOrdinal = 0,
        compressLevel = 80,
        maxOutputLongEdge = 0,
        copyright = "",
        outputNamePattern = "",
        conflictPolicyId = 0
    )

    @Test
    fun insert_thenGetAll_returnsInsertedEntry() = runBlocking {
        dao.insert(entity(1_000L))

        val all = dao.getAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().timestamp).isEqualTo(1_000L)
    }

    @Test
    fun getAll_ordersByTimestampDesc() = runBlocking {
        dao.insert(entity(1_000L))
        dao.insert(entity(3_000L))
        dao.insert(entity(2_000L))

        val all = dao.getAll().first()

        assertThat(all.map { it.timestamp }).containsExactly(3_000L, 2_000L, 1_000L).inOrder()
    }

    @Test
    fun deleteById_removesOnlyThatEntry() = runBlocking {
        val keepId = dao.insert(entity(1_000L))
        val removeId = dao.insert(entity(2_000L))

        dao.deleteById(removeId)

        val all = dao.getAll().first()
        assertThat(all.map { it.id }).containsExactly(keepId)
    }

    @Test
    fun trimOldest_keepsOnlyMostRecentEntries() = runBlocking {
        repeat(5) { i -> dao.insert(entity((i + 1) * 1_000L)) }

        dao.trimOldest(2)

        val all = dao.getAll().first()
        assertThat(all.map { it.timestamp }).containsExactly(5_000L, 4_000L).inOrder()
    }

    @Test
    fun trimOldest_fewerRowsThanKeepCount_keepsAll() = runBlocking {
        dao.insert(entity(1_000L))
        dao.insert(entity(2_000L))

        dao.trimOldest(20)

        assertThat(dao.getAll().first()).hasSize(2)
    }
}
