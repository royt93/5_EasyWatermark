package com.mckimquyen.watermark.ui.about

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.data.repo.BackupRestoreRepository
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.export.AuthenticityVerifier
import com.mckimquyen.watermark.export.stego.HiddenWatermarkReader
import com.mckimquyen.watermark.export.stego.InvisibleWatermark
import com.mckimquyen.watermark.utils.ktx.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val waterMarkRepository: WaterMarkRepository,
    private val backupRestoreRepository: BackupRestoreRepository,
    private val userConfigRepository: UserConfigRepository,
    memorySettingRepo: MemorySettingRepo
) : ViewModel() {

    val waterMark = waterMarkRepository.waterMark.asLiveData()

    val palette = memorySettingRepo.paletteFlow.asLiveData()

    fun toggleBounds(enable: Boolean) {
        launch {
            waterMarkRepository.toggleBounds(enable)
        }
    }

    fun toggleSupportDynamicColor(enable: Boolean) {
        if (enable) {
            CMonet.forceSupportDynamicColor()
        } else {
            CMonet.disableSupportDynamicColor()
        }
    }

    /** Đề xuất F: xuất Template + Signature ra file zip user chọn qua SAF. */
    fun backupTo(destUri: Uri, onResult: (Boolean) -> Unit) {
        launch {
            val success = backupRestoreRepository.backupTo(destUri)
            onResult(success)
        }
    }

    /** Đề xuất F: nhập lại Template + Signature từ file zip user chọn qua SAF. */
    fun restoreFrom(srcUri: Uri, onResult: (Boolean) -> Unit) {
        launch {
            val success = backupRestoreRepository.restoreFrom(srcUri)
            onResult(success)
        }
    }

    /**
     * IDEA-03: kiểm tra con dấu chứng thực của ảnh [srcUri].
     *
     * Đọc file + hash + xác minh chữ ký đều chạy trên [Dispatchers.IO] (ảnh lớn có thể mất vài trăm
     * ms), [onResult] trả về Main để gọi trực tiếp sang UI được.
     */
    fun verifyAuthenticity(
        contentResolver: ContentResolver,
        srcUri: Uri,
        onResult: (VerifyReport) -> Unit
    ) {
        launch {
            // Tên chủ sở hữu đang cấu hình, để đối chiếu với ID rút gọn đọc từ lớp ẩn.
            val currentOwner = userConfigRepository.userPreferences.first().copyright
            val report = withContext(Dispatchers.IO) {
                VerifyReport(
                    stamp = AuthenticityVerifier.verify(contentResolver, srcUri),
                    // IDEA-02: đọc song song, KHÔNG phụ thuộc kết quả EXIF ở trên — ca đáng giá nhất
                    // chính là EXIF đã bị nền tảng xoá sạch mà lớp ẩn trong pixel vẫn còn.
                    hidden = HiddenWatermarkReader.read(contentResolver, srcUri),
                    currentOwner = currentOwner
                )
            }
            onResult(report)
        }
    }

    /** Kết quả gộp của hai tầng độc lập: con dấu EXIF (IDEA-03) và watermark ẩn trong pixel (IDEA-02). */
    data class VerifyReport(
        val stamp: AuthenticityVerifier.Result,
        val hidden: InvisibleWatermark.Result?,
        /** Tên chủ sở hữu đang đặt trong cấu hình export — để đối chiếu với ID trong lớp ẩn. */
        val currentOwner: String = ""
    )
}
