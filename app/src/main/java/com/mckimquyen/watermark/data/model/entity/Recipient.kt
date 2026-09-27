package com.mckimquyen.watermark.data.model.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * IDEA-10: Thực thể Người nhận (Recipient) phục vụ gắn dấu vân tay vào ảnh xuất (Fingerprint Batch).
 * Mỗi người nhận có một [code] định danh độc nhất (unique) để nhúng vào watermark hoặc tra cứu nguồn rò rỉ.
 */
@Entity(
    tableName = "recipient",
    indices = [Index(value = ["code"], unique = true)]
)
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val notes: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
