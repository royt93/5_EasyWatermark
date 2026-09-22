package com.mckimquyen.watermark.ui

import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.watermark.data.model.ConflictPolicy
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.model.entity.BatchHistoryEntity
import com.mckimquyen.watermark.data.repo.BatchHistoryRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FEAT-04: màn hình lịch sử batch export (`BatchHistoryActivity`) — độc lập với `MainViewModel`
 * (không có `ViewInfo`/canvas sống ở đây), chỉ đọc/ghi qua repository singleton.
 */
@HiltViewModel
class BatchHistoryViewModel @Inject constructor(
    private val batchHistoryRepo: BatchHistoryRepository,
    private val waterMarkRepo: WaterMarkRepository,
    private val userConfigRepo: UserConfigRepository
) : ViewModel() {

    val historyFlow = batchHistoryRepo.historyFlow

    fun delete(entry: BatchHistoryEntity) {
        viewModelScope.launch {
            batchHistoryRepo.delete(entry)
        }
    }

    /**
     * AC3 "chạy lại": khôi phục đúng danh sách ảnh input + cấu hình export của [entry] vào
     * repository dùng chung, rồi gọi [onRestored] để Activity đóng lại, trả user về editor
     * (`MainActivity`) — KHÔNG tự export ngầm, vì không có `ViewInfo`/canvas sống ở màn hình này để
     * derive watermark thật, user cần xem lại rồi tự bấm Export như luồng bình thường.
     */
    fun rerun(entry: BatchHistoryEntity, onRestored: () -> Unit) {
        viewModelScope.launch {
            restoreEntry(entry)
            onRestored()
        }
    }

    /** Tách khỏi [rerun] để test trực tiếp bằng `runBlocking`, không cần mock `viewModelScope`. */
    internal suspend fun restoreEntry(entry: BatchHistoryEntity) {
        val images = BatchHistoryRepository.decodeUriList(entry.inputUris).map { ImageInfo(uri = it) }
        waterMarkRepo.updateImageList(images)
        userConfigRepo.updateFormat(UserConfigRepository.resolveOutputFormat(entry.outputFormatOrdinal, Build.VERSION.SDK_INT))
        userConfigRepo.updateCompressLevel(entry.compressLevel)
        userConfigRepo.updateMaxLongEdge(entry.maxOutputLongEdge)
        userConfigRepo.updateCopyright(entry.copyright)
        userConfigRepo.updateOutputNamePattern(entry.outputNamePattern)
        userConfigRepo.updateConflictPolicy(ConflictPolicy.fromId(entry.conflictPolicyId))
        userConfigRepo.updateOutputDirectoryUri(entry.outputDirectoryUri?.let(Uri::parse))
    }
}
