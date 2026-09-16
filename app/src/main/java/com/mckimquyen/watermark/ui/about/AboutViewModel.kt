package com.mckimquyen.watermark.ui.about

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.mckimquyen.cmonet.CMonet
import com.mckimquyen.watermark.data.repo.BackupRestoreRepository
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.ktx.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val waterMarkRepository: WaterMarkRepository,
    private val backupRestoreRepository: BackupRestoreRepository,
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
}
