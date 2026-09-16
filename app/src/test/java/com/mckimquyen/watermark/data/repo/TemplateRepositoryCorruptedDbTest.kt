package com.mckimquyen.watermark.data.repo

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.model.entity.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Date

/**
 * BUG-26: Room mở kết nối thật/copy asset DB (`createFromAsset`) LAZY tại QUERY ĐẦU TIÊN, không
 * phải lúc `build()` — `AppModule.provideYourDatabase()` chỉ try/catch được `build()`. Nếu asset
 * DB hỏng/thiếu, exception chỉ xảy ra khi query THẬT SỰ chạy (ở đây mô phỏng bằng fake [TemplateDao]
 * ném exception thay vì query SQLite thật — không cần Robolectric/DB thật để tái hiện đúng lỗi).
 */
class TemplateRepositoryCorruptedDbTest {

    private class ThrowingTemplateDao : TemplateDao {
        override fun getAllTemplate(): Flow<List<Template>> = flow {
            throw SQLiteException("simulated corrupted asset DB")
        }

        override suspend fun insertTemplate(template: Template) {
            throw SQLiteException("simulated corrupted asset DB")
        }

        override suspend fun deleteTemplate(template: Template) {
            throw SQLiteException("simulated corrupted asset DB")
        }

        override suspend fun updateTemplate(template: Template) {
            throw SQLiteException("simulated corrupted asset DB")
        }
    }

    private fun template() = Template(id = 0, content = "x", creationDate = Date(), lastModifiedDate = Date())

    @Test
    fun getAllTemplate_daoThrows_doesNotCrash_emitsEmptyList() = runBlocking {
        val repo = TemplateRepository(ThrowingTemplateDao())

        val result = repo.getAllTemplate().first()

        assertThat(result).isEmpty()
    }

    @Test
    fun insertTemplate_daoThrows_doesNotCrash() = runBlocking {
        val repo = TemplateRepository(ThrowingTemplateDao())

        repo.insertTemplate(template())
        Unit
    }

    @Test
    fun deleteTemplate_daoThrows_doesNotCrash() = runBlocking {
        val repo = TemplateRepository(ThrowingTemplateDao())

        repo.deleteTemplate(template())
        Unit
    }

    @Test
    fun updateTemplate_daoThrows_doesNotCrash() = runBlocking {
        val repo = TemplateRepository(ThrowingTemplateDao())

        repo.updateTemplate(template())
        Unit
    }

    @Test
    fun getAllTemplate_nullDao_stillEmitsEmptyList() = runBlocking {
        val repo = TemplateRepository(null)

        val result = repo.getAllTemplate().first()

        assertThat(result).isEmpty()
    }
}
