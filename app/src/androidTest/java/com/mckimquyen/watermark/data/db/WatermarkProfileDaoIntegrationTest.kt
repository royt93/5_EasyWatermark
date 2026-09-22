package com.mckimquyen.watermark.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.WatermarkProfileDao
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-06: kiểm chứng [WatermarkProfileDao] với Room DB in-memory thật, mirror
 * `BatchHistoryDaoIntegrationTest`/`TemplateDaoIntegrationTest` (quy ước Room DAO test của repo này).
 */
@RunWith(AndroidJUnit4::class)
class WatermarkProfileDaoIntegrationTest {

    private lateinit var db: WatermarkProfileDatabase
    private lateinit var dao: WatermarkProfileDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WatermarkProfileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.watermarkProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(name: String, createdAt: Long) = WatermarkProfileEntity(
        id = 0,
        name = name,
        createdAt = createdAt,
        text = "x",
        textSize = 14f,
        textColor = 0,
        textStyleKey = 0,
        textTypefaceKey = 0,
        alpha = 255,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = "",
        markModeValue = 0,
        enableBounds = false,
        enableExif = false,
        exifFrameStyle = 0,
        anchor = 4,
        marginPercent = 0.05f
    )

    @Test
    fun insert_thenGetAll_returnsInsertedProfile() = runBlocking {
        dao.insert(entity("Instagram", 1_000L))

        val all = dao.getAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().name).isEqualTo("Instagram")
    }

    @Test
    fun getAll_ordersByCreatedAtDesc() = runBlocking {
        dao.insert(entity("old", 1_000L))
        dao.insert(entity("newest", 3_000L))
        dao.insert(entity("mid", 2_000L))

        val all = dao.getAll().first()

        assertThat(all.map { it.name }).containsExactly("newest", "mid", "old").inOrder()
    }

    @Test
    fun deleteById_removesOnlyThatProfile() = runBlocking {
        val keepId = dao.insert(entity("keep", 1_000L))
        val removeId = dao.insert(entity("remove", 2_000L))

        dao.deleteById(removeId)

        val all = dao.getAll().first()
        assertThat(all.map { it.id }).containsExactly(keepId)
    }
}
