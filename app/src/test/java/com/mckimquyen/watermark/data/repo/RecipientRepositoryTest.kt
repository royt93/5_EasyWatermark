package com.mckimquyen.watermark.data.repo

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.dao.RecipientDao
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.export.stego.StegoPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * IDEA-10: phần đáng sai nhất của ticket — [RecipientRepository.findMatching] là thứ quyết định
 * "ảnh rò rỉ này gửi cho ai". Sai ở đây nghĩa là chỉ nhầm người, hậu quả nặng hơn là không tra ra.
 * Dùng fake DAO (không Room, thuần JVM) theo khuôn `BatchHistoryRepositoryRoboTest`.
 */
class RecipientRepositoryTest {

    private val owner = "Roy Studio"

    private class FakeRecipientDao(private var items: List<Recipient> = emptyList()) : RecipientDao {
        val inserted = mutableListOf<Recipient>()
        val updated = mutableListOf<Recipient>()
        val deleted = mutableListOf<Recipient>()
        val deletedIds = mutableListOf<Long>()

        override fun getAll(): Flow<List<Recipient>> = flowOf(items)

        override suspend fun getAllList(): List<Recipient> = items

        override suspend fun getById(id: Long): Recipient? = items.firstOrNull { it.id == id }

        override suspend fun getByCode(code: String): Recipient? = items.firstOrNull { it.code == code }

        override suspend fun insert(recipient: Recipient): Long {
            inserted.add(recipient)
            items = items + recipient
            return inserted.size.toLong()
        }

        override suspend fun update(recipient: Recipient) {
            updated.add(recipient)
        }

        override suspend fun delete(recipient: Recipient) {
            deleted.add(recipient)
        }

        override suspend fun deleteById(id: Long) {
            deletedIds.add(id)
        }
    }

    private val clientA = Recipient(id = 1, name = "Khách hàng A", code = "VIP-A")
    private val clientB = Recipient(id = 2, name = "Khách hàng B", code = "VIP-B")

    private fun repoWith(vararg items: Recipient) = RecipientRepository(FakeRecipientDao(items.toList()))

    @Test
    fun `tra dung nguoi nhan tu dau van tay stego`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        val leakedId = StegoPayload.ownerIdOf("$owner#${clientB.code}")

        val found = repo.findMatching(stegoOwnerId = leakedId, currentOwner = owner)

        assertThat(found).isEqualTo(clientB)
    }

    @Test
    fun `khong co nguoi nhan nao khop thi tra null chu khong doan bua`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        val strangerId = StegoPayload.ownerIdOf("$owner#KHONG-TON-TAI")

        assertThat(repo.findMatching(stegoOwnerId = strangerId, currentOwner = owner)).isNull()
    }

    @Test
    fun `danh sach rong thi tra null khong nem loi`() = runBlocking {
        val repo = repoWith()
        assertThat(repo.findMatching(stegoOwnerId = 12345, currentOwner = owner)).isNull()
    }

    @Test
    fun `tra dung nguoi nhan tu chuoi owner trong EXIF`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        // Đúng định dạng BatchExportEngine ghi vào con dấu: "<copyright> [<code>]".
        val found = repo.findMatching(ownerText = "$owner [${clientA.code}]", currentOwner = owner)
        assertThat(found).isEqualTo(clientA)
    }

    @Test
    fun `owner EXIF khong chua ma nao thi tra null`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        assertThat(repo.findMatching(ownerText = owner, currentOwner = owner)).isNull()
    }

    @Test
    fun `owner EXIF rong khong gay khop nham`() = runBlocking {
        val repo = repoWith(clientA)
        assertThat(repo.findMatching(ownerText = "", currentOwner = owner)).isNull()
        assertThat(repo.findMatching(ownerText = null, currentOwner = owner)).isNull()
    }

    @Test
    fun `uu tien stego khi ca hai nguon deu co du lieu`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        // EXIF nói A, pixel nói B — pixel đáng tin hơn (sống sót re-encode, khó sửa hơn EXIF text).
        val found = repo.findMatching(
            ownerText = "$owner [${clientA.code}]",
            stegoOwnerId = StegoPayload.ownerIdOf("$owner#${clientB.code}"),
            currentOwner = owner
        )
        assertThat(found).isEqualTo(clientB)
    }

    @Test
    fun `khong truyen gi ca thi tra null`() = runBlocking {
        val repo = repoWith(clientA)
        assertThat(repo.findMatching(currentOwner = owner)).isNull()
    }

    @Test
    fun `save ghi vao dao`() {
        val dao = FakeRecipientDao()
        val repo = RecipientRepository(dao)
        runBlocking { repo.save(clientA) }
        assertThat(dao.inserted).containsExactly(clientA)
    }

    @Test
    fun `delete goi dao`() {
        val dao = FakeRecipientDao(listOf(clientA))
        val repo = RecipientRepository(dao)
        runBlocking { repo.delete(clientA) }
        assertThat(dao.deleted).containsExactly(clientA)
    }

    @Test
    fun `getByCode tra ve dung nguoi de kiem tra trung ma`() = runBlocking {
        val repo = repoWith(clientA, clientB)
        assertThat(repo.getByCode("VIP-B")).isEqualTo(clientB)
        assertThat(repo.getByCode("KHONG-CO")).isNull()
    }
}
