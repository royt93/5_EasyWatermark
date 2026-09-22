package com.mckimquyen.watermark.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.watermark.data.model.entity.WatermarkProfileEntity
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.data.repo.WatermarkProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FEAT-06: màn hình danh sách "hồ sơ" watermark (`WatermarkProfileActivity`) — độc lập với
 * `MainViewModel`, chỉ đọc/ghi qua repository singleton.
 */
@HiltViewModel
class WatermarkProfileViewModel @Inject constructor(
    private val profileRepo: WatermarkProfileRepository,
    private val waterMarkRepo: WaterMarkRepository
) : ViewModel() {

    val profilesFlow = profileRepo.profilesFlow

    /** AC1 "lưu profile" — snapshot cấu hình watermark HIỆN TẠI đang áp dụng trong editor. */
    fun saveCurrentAsProfile(name: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            saveCurrentAsProfileNow(name)
            onSaved()
        }
    }

    fun delete(entity: WatermarkProfileEntity) {
        viewModelScope.launch {
            profileRepo.delete(entity)
        }
    }

    /** AC2 "áp dụng" — khôi phục đúng toàn bộ cấu hình đã lưu, rồi báo Activity đóng lại. */
    fun apply(entity: WatermarkProfileEntity, onApplied: () -> Unit) {
        viewModelScope.launch {
            applyNow(entity)
            onApplied()
        }
    }

    /** Tách khỏi [saveCurrentAsProfile] để test trực tiếp bằng `runBlocking`, không cần mock `viewModelScope`. */
    internal suspend fun saveCurrentAsProfileNow(name: String) {
        val current = waterMarkRepo.waterMark.first()
        profileRepo.save(name, current)
    }

    /** Tách khỏi [apply] — cùng lý do trên. */
    internal suspend fun applyNow(entity: WatermarkProfileEntity) {
        waterMarkRepo.applyWaterMark(WatermarkProfileRepository.toWaterMark(entity))
    }
}
