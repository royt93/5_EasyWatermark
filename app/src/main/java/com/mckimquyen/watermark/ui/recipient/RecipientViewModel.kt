package com.mckimquyen.watermark.ui.recipient

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.watermark.data.model.entity.Recipient
import com.mckimquyen.watermark.data.repo.RecipientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * IDEA-10: ViewModel dùng chung cho màn quản lý và bottom sheet chọn người nhận.
 */
@HiltViewModel
class RecipientViewModel @Inject constructor(
    private val repository: RecipientRepository
) : ViewModel() {

    val recipients: StateFlow<List<Recipient>> = repository.recipientsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Lưu người nhận mới hoặc cập nhật. Trả kết quả qua [onResult]:
     * true = thành công, false = mã trùng.
     */
    fun save(recipient: Recipient, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val existing = repository.getByCode(recipient.code)
            // Mã bị trùng với người khác (không phải chính nó khi đang sửa) → từ chối.
            if (existing != null && existing.id != recipient.id) {
                onResult(false)
                return@launch
            }
            // P1 review pass 8: check-then-act ở trên không atomic — 2 lần save() gần như đồng
            // thời cùng code có thể cả 2 vượt qua check rồi 1 trong 2 ghi Room ném
            // SQLiteConstraintException (unique index `code`). Bọc try/catch để báo lỗi trùng mã
            // cho user thay vì để crash thẳng trong viewModelScope.
            try {
                if (recipient.id == 0L) {
                    repository.save(recipient)
                } else {
                    repository.update(recipient)
                }
            } catch (e: SQLiteConstraintException) {
                onResult(false)
                return@launch
            }
            onResult(true)
        }
    }

    fun delete(recipient: Recipient) {
        viewModelScope.launch {
            repository.delete(recipient)
        }
    }
}
