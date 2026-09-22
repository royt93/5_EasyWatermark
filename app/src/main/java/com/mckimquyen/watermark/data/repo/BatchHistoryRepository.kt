package com.mckimquyen.watermark.data.repo

import android.net.Uri
import com.mckimquyen.watermark.data.db.dao.BatchHistoryDao
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.export.BatchExportEngine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-04: bọc [BatchHistoryDao] — encode/decode danh sách Uri dạng chuỗi phân tách `\n` (xem doc
 * ở [BatchHistoryEntity]). Cố tình KHÔNG dùng chung helper encode/decode với
 * [WaterMarkRepository.parseRecentIconUris]/`pushToFrontOfRecentIcons` — 2 bảng khác nhau, logic
 * khác nhau (đây không cần MRU/giới hạn per-item), gộp chung chỉ tạo phụ thuộc chéo không cần thiết.
 */
@Singleton
class BatchHistoryRepository @Inject constructor(
    private val dao: BatchHistoryDao
) {

    val historyFlow: Flow<List<BatchHistoryEntity>> = dao.getAll()

    suspend fun record(
        inputUris: List<Uri>,
        outputUris: List<Uri>,
        failedInputUris: List<Uri>,
        settings: BatchExportEngine.ExportSettings
    ) {
        // Batch rỗng (không có ảnh nào input) không phải 1 lần export thật sự — không đáng ghi lịch sử.
        if (inputUris.isEmpty()) return
        dao.insert(
            BatchHistoryEntity(
                timestamp = System.currentTimeMillis(),
                inputUris = encodeUriList(inputUris),
                outputUris = encodeUriList(outputUris),
                failedInputUris = encodeUriList(failedInputUris),
                outputFormatOrdinal = settings.outputFormat.ordinal,
                compressLevel = settings.compressLevel,
                maxOutputLongEdge = settings.maxOutputLongEdge,
                copyright = settings.copyright,
                outputNamePattern = settings.outputNamePattern,
                conflictPolicyId = settings.conflictPolicy.id,
                outputDirectoryUri = settings.outputDirectoryUri?.toString()
            )
        )
        dao.trimOldest(MAX_HISTORY_ENTRIES)
    }

    suspend fun delete(entry: BatchHistoryEntity) = dao.deleteById(entry.id)

    companion object {
        /** FEAT-04: giữ tối đa 20 lần export gần nhất — tránh phình DB vô hạn qua thời gian dùng app. */
        const val MAX_HISTORY_ENTRIES = 20

        internal fun encodeUriList(uris: List<Uri>): String = uris.joinToString("\n") { it.toString() }

        internal fun decodeUriList(raw: String): List<Uri> =
            if (raw.isBlank()) {
                emptyList()
            } else {
                raw.split("\n").filter { it.isNotBlank() }.map { Uri.parse(it) }
            }
    }
}
