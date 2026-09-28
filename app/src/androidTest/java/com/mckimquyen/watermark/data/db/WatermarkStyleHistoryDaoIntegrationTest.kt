package com.mckimquyen.watermark.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkStyleHistoryDao
import com.mckimquyen.watermark.data.model.entity.WatermarkStyleHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-12: kiểm chứng [WatermarkStyleHistoryDao] với Room DB in-memory thật, mirror
 * `WatermarkProfileDaoIntegrationTest` (quy ước Room DAO test của repo này).
 */
@RunWith(AndroidJUnit4::class)
class WatermarkStyleHistoryDaoIntegrationTest {

    private lateinit var db: WatermarkProfileDatabase
    private lateinit var dao: WatermarkStyleHistoryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WatermarkProfileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.watermarkStyleHistoryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(timestamp: Long, textColor: Int = 0) = WatermarkStyleHistoryEntity(
        id = 0,
        timestamp = timestamp,
        textColor = textColor,
        textStyleKey = 0,
        textTypefaceKey = 0,
        alpha = 255,
        anchor = 4,
        markModeValue = 0,
        exifFrameStyle = 0,
        textEffectStroke = false,
        textEffectShadow = false,
        textEffectPillBackground = false
    )

    @Test
    fun insert_thenRecent_returnsInsertedRow() = runBlocking {
        dao.insert(entity(1_000L, textColor = 0xAABBCC))

        val recent = dao.recent(10)

        assertThat(recent).hasSize(1)
        assertThat(recent.first().textColor).isEqualTo(0xAABBCC)
    }

    @Test
    fun recent_ordersByTimestampDesc_andRespectsLimit() = runBlocking {
        dao.insert(entity(1_000L, textColor = 1))
        dao.insert(entity(3_000L, textColor = 3))
        dao.insert(entity(2_000L, textColor = 2))

        val recent = dao.recent(2)

        assertThat(recent.map { it.textColor }).containsExactly(3, 2).inOrder()
    }

    @Test
    fun pruneKeepLatest_removesOlderRowsBeyondKeepCount() = runBlocking {
        repeat(5) { dao.insert(entity((it + 1) * 1_000L, textColor = it)) }

        dao.pruneKeepLatest(3)

        val remaining = dao.recent(100)
        assertThat(remaining).hasSize(3)
        // giữ 3 mới nhất: textColor 4,3,2 (timestamp 5000,4000,3000)
        assertThat(remaining.map { it.textColor }).containsExactly(4, 3, 2).inOrder()
    }
}
