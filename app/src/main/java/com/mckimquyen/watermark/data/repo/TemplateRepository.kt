package com.mckimquyen.watermark.data.repo

import android.util.Log
import com.mckimquyen.watermark.data.db.dao.TemplateDao
import com.mckimquyen.watermark.data.model.entity.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao?
) {

    fun checkIfIsDaoNull(): Boolean {
        return templateDao == null
    }

    /**
     * BUG-26: Room mở kết nối thật/copy asset DB (`createFromAsset`) LAZY tại QUERY ĐẦU TIÊN,
     * không phải lúc `build()` — `AppModule.provideYourDatabase()` chỉ try/catch được `build()`,
     * nên asset DB hỏng/thiếu chỉ lộ ra khi Flow này thực sự chạy query, và `checkIfIsDaoNull()`
     * (chỉ kiểm tra `templateDao == null`) không phát hiện được trường hợp này. `.catch` bảo vệ
     * đúng chỗ exception thật sự xảy ra — bên trong quá trình collect Flow.
     */
    fun getAllTemplate(): Flow<List<Template>> {
        return (templateDao?.getAllTemplate() ?: flow { emit(listOf()) })
            .catch { e ->
                Log.e("TemplateRepository", "getAllTemplate failed", e)
                emit(listOf())
            }
    }

    suspend fun insertTemplate(template: Template): Unit = withContext(Dispatchers.IO) {
        try {
            templateDao?.insertTemplate(template)
        } catch (e: Exception) {
            Log.e("TemplateRepository", "insertTemplate failed", e)
        }
    }

    suspend fun deleteTemplate(template: Template): Unit = withContext(Dispatchers.IO) {
        try {
            templateDao?.deleteTemplate(template)
        } catch (e: Exception) {
            Log.e("TemplateRepository", "deleteTemplate failed", e)
        }
    }

    suspend fun updateTemplate(template: Template): Unit = withContext(Dispatchers.IO) {
        try {
            templateDao?.updateTemplate(template)
        } catch (e: Exception) {
            Log.e("TemplateRepository", "updateTemplate failed", e)
        }
    }
}
