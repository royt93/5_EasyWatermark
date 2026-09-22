package com.mckimquyen.watermark.data.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FEAT-04: 1 dòng = 1 lần batch export đã chạy xong (thành công hoặc lỗi 1 phần) — đủ dữ liệu để
 * "chạy lại" (khôi phục đúng danh sách ảnh input + cấu hình export) mà không cần lưu lại ảnh input
 * (chỉ lưu Uri, chọn lại từ Gallery/MediaStore lúc cần). Danh sách Uri lưu dạng chuỗi phân tách
 * `\n` (Uri không chứa newline thật, chỉ percent-encode `%0A`) — cùng cách `WaterMarkRepository`
 * lưu `recentIconUris` (FEAT-24), không cần bảng phụ/quan hệ 1-nhiều cho việc đơn giản này.
 *
 * DB riêng ([com.mckimquyen.watermark.data.db.BatchHistoryDatabase]), KHÔNG chung với
 * [com.mckimquyen.watermark.data.db.AppDatabase] — `AppDatabase` được tạo từ asset
 * (`createFromAsset`, xem `di/AppModule.kt`), thêm entity/đổi version vào đó rủi ro migration cao
 * không cần thiết cho tính năng độc lập này.
 */
@Entity(tableName = "batch_history")
data class BatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Toàn bộ ảnh trong batch (kể cả bị skip) — dùng để khôi phục nguyên batch khi "chạy lại". */
    val inputUris: String,
    /** Chỉ ảnh xuất thành công. */
    val outputUris: String,
    /** Chỉ ảnh input bị lỗi (không xuất được) — AC "xem được ảnh lỗi". */
    val failedInputUris: String,
    val outputFormatOrdinal: Int,
    val compressLevel: Int,
    val maxOutputLongEdge: Int,
    val copyright: String,
    val outputNamePattern: String,
    val conflictPolicyId: Int,
    /** null = mặc định Pictures/WaterMarkCreator/ (FEAT-15). */
    val outputDirectoryUri: String? = null
)
