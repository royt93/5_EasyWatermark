package com.mckimquyen.watermark.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.RecipientDao
import com.mckimquyen.watermark.data.model.entity.Recipient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-10: kiểm chứng [RecipientDao] với Room DB in-memory thật, đặc biệt là ràng buộc `unique`
 * trên cột `code` — sai chỗ này thì 2 khách hàng có thể lỡ trùng mã, phá vỡ toàn bộ mục tiêu truy
 * nguồn rò rỉ (mirror `WatermarkProfileDaoIntegrationTest`).
 */
@RunWith(AndroidJUnit4::class)
class RecipientDaoIntegrationTest {

    private lateinit var db: RecipientDatabase
    private lateinit var dao: RecipientDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, RecipientDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recipientDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recipient(name: String, code: String, timestamp: Long) = Recipient(
        id = 0,
        name = name,
        code = code,
        notes = null,
        timestamp = timestamp
    )

    @Test
    fun insert_thenGetAll_returnsInsertedRecipient() = runBlocking {
        dao.insert(recipient("Khách A", "VIP-A", 1_000L))

        val all = dao.getAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().name).isEqualTo("Khách A")
        assertThat(all.first().code).isEqualTo("VIP-A")
    }

    @Test
    fun getAll_ordersByTimestampDesc() = runBlocking {
        dao.insert(recipient("old", "C1", 1_000L))
        dao.insert(recipient("newest", "C3", 3_000L))
        dao.insert(recipient("mid", "C2", 2_000L))

        val all = dao.getAll().first()

        assertThat(all.map { it.name }).containsExactly("newest", "mid", "old").inOrder()
    }

    @Test
    fun getByCode_traDungNguoiNhan() = runBlocking {
        dao.insert(recipient("Khách A", "VIP-A", 1_000L))
        dao.insert(recipient("Khách B", "VIP-B", 2_000L))

        val found = dao.getByCode("VIP-B")

        assertThat(found?.name).isEqualTo("Khách B")
    }

    @Test
    fun getByCode_khongTonTai_traNull() = runBlocking {
        assertThat(dao.getByCode("KHONG-CO")).isNull()
    }

    @Test
    fun maTrungNhau_bangUniqueIndexThayTheBanCu_khongTaoBanGhiMoi() = runBlocking {
        // OnConflictStrategy.REPLACE + unique index trên `code`: đây chính là hàng rào chống trùng mã
        // người nhận — nếu hàng rào này gãy, 2 khách hàng khác nhau có thể lỡ chung 1 mã.
        dao.insert(recipient("Khách A gốc", "VIP-A", 1_000L))
        dao.insert(recipient("Khách A đổi tên", "VIP-A", 2_000L))

        val all = dao.getAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().name).isEqualTo("Khách A đổi tên")
    }

    @Test
    fun deleteById_xoaDungNguoiNhan() {
        val (keepId, all) = runBlocking {
            val keepId = dao.insert(recipient("keep", "K1", 1_000L))
            val removeId = dao.insert(recipient("remove", "R1", 2_000L))
            dao.deleteById(removeId)
            keepId to dao.getAll().first()
        }
        assertThat(all.map { it.id }).containsExactly(keepId)
    }

    @Test
    fun update_suaDungBanGhi() = runBlocking {
        val id = dao.insert(recipient("Khách A", "VIP-A", 1_000L))
        val updated = recipient("Khách A (VIP)", "VIP-A", 1_000L).copy(id = id)

        dao.update(updated)

        val found = dao.getById(id)
        assertThat(found?.name).isEqualTo("Khách A (VIP)")
    }
}
