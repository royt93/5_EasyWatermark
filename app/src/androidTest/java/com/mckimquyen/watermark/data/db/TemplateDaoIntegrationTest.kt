package com.mckimquyen.watermark.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.model.entity.Template
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Integration test (instrumented): kiểm chứng [TemplateDao] với Room DB in-memory thật.
 */
@RunWith(AndroidJUnit4::class)
class TemplateDaoIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TemplateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.templateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun template(content: String, createdAt: Long): Template =
        Template(
            id = 0,
            content = content,
            creationDate = Date(createdAt),
            lastModifiedDate = Date(createdAt)
        )

    @Test
    fun insert_thenGetAll_returnsInsertedTemplate() = runBlocking {
        dao.insertTemplate(template("watermark-A", 1_000L))

        val all = dao.getAllTemplate().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().content).isEqualTo("watermark-A")
    }

    @Test
    fun getAll_ordersByCreationDateDesc() = runBlocking {
        dao.insertTemplate(template("old", 1_000L))
        dao.insertTemplate(template("new", 5_000L))

        val all = dao.getAllTemplate().first()

        assertThat(all.map { it.content }).containsExactly("new", "old").inOrder()
    }

    @Test
    fun update_changesContent() = runBlocking {
        dao.insertTemplate(template("before", 1_000L))
        val inserted = dao.getAllTemplate().first().first()

        dao.updateTemplate(inserted.copy(content = "after"))

        val all = dao.getAllTemplate().first()
        assertThat(all).hasSize(1)
        assertThat(all.first().content).isEqualTo("after")
    }

    @Test
    fun delete_removesTemplate() = runBlocking {
        dao.insertTemplate(template("to-delete", 1_000L))
        val inserted = dao.getAllTemplate().first().first()

        dao.deleteTemplate(inserted)

        assertThat(dao.getAllTemplate().first()).isEmpty()
    }
}
