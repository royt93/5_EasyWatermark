package com.mckimquyen.watermark.data.repo

import com.mckimquyen.watermark.data.db.dao.RecipientDao
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.export.stego.StegoPayload
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IDEA-10: Repository quản lý danh sách người nhận và đối chiếu dấu vết (fingerprint).
 */
@Singleton
class RecipientRepository @Inject constructor(
    private val recipientDao: RecipientDao
) {
    val recipientsFlow: Flow<List<Recipient>> = recipientDao.getAll()

    suspend fun getAllList(): List<Recipient> = recipientDao.getAllList()

    suspend fun getById(id: Long): Recipient? = recipientDao.getById(id)

    suspend fun getByCode(code: String): Recipient? = recipientDao.getByCode(code)

    suspend fun save(recipient: Recipient): Long = recipientDao.insert(recipient)

    suspend fun update(recipient: Recipient) = recipientDao.update(recipient)

    suspend fun delete(recipient: Recipient) = recipientDao.delete(recipient)

    suspend fun deleteById(id: Long) = recipientDao.deleteById(id)

    /**
     * Đối chiếu dấu vết (từ ownerId của InvisibleWatermark hoặc text owner của EXIF stamp)
     * để tìm xem có khớp với người nhận nào trong hệ thống không.
     *
     * Hỗ trợ 2 dạng:
     * 1. [stegoOwnerId]: So sánh với StegoPayload.ownerIdOf("${owner}#${recipient.code}") hoặc StegoPayload.ownerIdOf(recipient.code).
     * 2. [ownerText]: So sánh chuỗi trực tiếp xem có chứa recipient.code hoặc recipient.name hay không.
     */
    suspend fun findMatching(
        ownerText: String? = null,
        stegoOwnerId: Int? = null,
        currentOwner: String = ""
    ): Recipient? {
        val list = recipientDao.getAllList()
        if (list.isEmpty()) return null

        // 1. Kiểm tra qua stegoOwnerId (IDEA-02)
        if (stegoOwnerId != null) {
            for (recipient in list) {
                // Kiểm tra hash kết hợp owner#code
                val combinedHash = StegoPayload.ownerIdOf("$currentOwner#${recipient.code}")
                if (combinedHash == stegoOwnerId) return recipient

                // Kiểm tra hash chỉ gồm code
                val codeOnlyHash = StegoPayload.ownerIdOf(recipient.code)
                if (codeOnlyHash == stegoOwnerId) return recipient

                // Kiểm tra hash gồm name
                val nameOnlyHash = StegoPayload.ownerIdOf(recipient.name)
                if (nameOnlyHash == stegoOwnerId) return recipient
            }
        }

        // 2. Kiểm tra qua chuỗi EXIF Owner (IDEA-03)
        if (!ownerText.isNullOrBlank()) {
            for (recipient in list) {
                if (ownerText.contains("[${recipient.code}]", ignoreCase = true) ||
                    ownerText.contains("(${recipient.code})", ignoreCase = true) ||
                    ownerText.equals(recipient.code, ignoreCase = true) ||
                    containsAsWord(ownerText, recipient.name)
                ) {
                    return recipient
                }
            }
        }

        return null
    }

    companion object {
        /**
         * BUG-AUDIT-2026-09-29: `contains(name, ignoreCase = true)` thô từng match cả khi [name]
         * chỉ là substring của 1 từ khác (vd tên ngắn/phổ biến) — gán nhầm người nhận. Chỉ coi là
         * khớp khi 2 đầu vị trí match KHÔNG phải chữ/số (ranh giới từ thật, không dùng regex `\b`
         * vì mặc định không nhận diện đúng ký tự có dấu tiếng Việt).
         */
        internal fun containsAsWord(haystack: String, needle: String): Boolean {
            if (needle.isBlank()) return false
            var fromIndex = 0
            while (true) {
                val idx = haystack.indexOf(needle, fromIndex, ignoreCase = true)
                if (idx < 0) return false
                val beforeOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
                val afterIdx = idx + needle.length
                val afterOk = afterIdx >= haystack.length || !haystack[afterIdx].isLetterOrDigit()
                if (beforeOk && afterOk) return true
                fromIndex = idx + 1
            }
        }
    }
}
