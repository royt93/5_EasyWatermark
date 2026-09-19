package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.AppDatabase
import com.mckimquyen.watermark.data.model.entity.Template
import com.mckimquyen.watermark.data.repo.TemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class TemplateManagementIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var templateRepo: TemplateRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        templateRepo = TemplateRepository(db.templateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun template_endToEnd_crud_operations() = runBlocking {
        // 1. Initial state is empty
        val initialList = templateRepo.getAllTemplate().first()
        assertThat(initialList).isEmpty()

        // 2. Add template
        val template1 = Template(
            id = 0,
            content = "© 2026 EasyWatermark Confidential",
            creationDate = Date(),
            lastModifiedDate = Date()
        )
        templateRepo.insertTemplate(template1)

        val listAfterInsert = templateRepo.getAllTemplate().first()
        assertThat(listAfterInsert).hasSize(1)
        val saved = listAfterInsert.first()
        assertThat(saved.content).isEqualTo("© 2026 EasyWatermark Confidential")

        // 3. Update template
        val updated = saved.copy(content = "© 2026 EasyWatermark Public", lastModifiedDate = Date())
        templateRepo.updateTemplate(updated)

        val listAfterUpdate = templateRepo.getAllTemplate().first()
        assertThat(listAfterUpdate).hasSize(1)
        assertThat(listAfterUpdate.first().content).isEqualTo("© 2026 EasyWatermark Public")

        // 4. Delete template
        templateRepo.deleteTemplate(listAfterUpdate.first())
        val listAfterDelete = templateRepo.getAllTemplate().first()
        assertThat(listAfterDelete).isEmpty()
    }
}
