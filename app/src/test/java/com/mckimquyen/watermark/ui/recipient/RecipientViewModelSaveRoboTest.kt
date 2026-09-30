package com.mckimquyen.watermark.ui.recipient

import android.database.sqlite.SQLiteConstraintException
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.RecipientDao
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.data.repo.RecipientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * P1 review pass 8: `save()` check-then-act (`getByCode` rồi `update`/`insert`) không atomic —
 * 2 lần gọi gần như đồng thời cùng `code` có thể cả 2 vượt qua check rồi 1 trong 2 ghi Room ném
 * `SQLiteConstraintException` (unique index `code`, xem `Recipient.kt`) thẳng trong
 * `viewModelScope.launch` -> crash thay vì báo lỗi trùng mã cho user.
 */
@RunWith(RobolectricTestRunner::class)
class RecipientViewModelSaveRoboTest {

    /** DAO giả lập đúng hành vi Room thật: `update()` mặc định `OnConflictStrategy.ABORT` -> ném
     * `SQLiteConstraintException` khi vi phạm unique index, dù `getByCode` phía trên không thấy. */
    private class ThrowingOnWriteDao : RecipientDao {
        override fun getAll(): Flow<List<Recipient>> = flowOf(emptyList())
        override suspend fun getAllList(): List<Recipient> = emptyList()
        override suspend fun getById(id: Long): Recipient? = null
        override suspend fun getByCode(code: String): Recipient? = null // race: chưa thấy trùng
        override suspend fun insert(recipient: Recipient): Long =
            throw SQLiteConstraintException("UNIQUE constraint failed: recipient.code")
        override suspend fun update(recipient: Recipient): Unit =
            throw SQLiteConstraintException("UNIQUE constraint failed: recipient.code")
        override suspend fun delete(recipient: Recipient) = Unit
        override suspend fun deleteById(id: Long) = Unit
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `update dung race constraint thi bao that bai khong crash`() {
        val viewModel = RecipientViewModel(RecipientRepository(ThrowingOnWriteDao()))
        var result: Boolean? = null

        viewModel.save(Recipient(id = 1, name = "A", code = "DUP")) { result = it }
        idle()

        assertThat(result).isFalse()
    }

    @Test
    fun `insert dung race constraint thi bao that bai khong crash`() {
        val viewModel = RecipientViewModel(RecipientRepository(ThrowingOnWriteDao()))
        var result: Boolean? = null

        viewModel.save(Recipient(id = 0, name = "A", code = "DUP")) { result = it }
        idle()

        assertThat(result).isFalse()
    }
}
