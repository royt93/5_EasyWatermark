package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.net.Uri
import com.mckimquyen.watermark.data.backup.BackupRestoreEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Đề xuất F (`doc/feat.md`): backup/restore Template (Room) + Signature (file `.webp`) qua SAF Uri. */
@Singleton
class BackupRestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateRepo: TemplateRepository,
    private val signatureRepo: SignatureRepository
) {

    suspend fun backupTo(destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val templates = templateRepo.getAllTemplate().first()
            val signatureFiles = signatureRepo.getAllSignatures().map { it.file }
            val output = context.contentResolver.openOutputStream(destUri) ?: return@withContext false
            output.use { BackupRestoreEngine.writeBackup(it, templates, signatureFiles) }
            true
        } catch (e: CancellationException) {
            // BUG-AUDIT-2026-09-29: CancellationException là subclass của Exception — catch chung
            // bên dưới sẽ nuốt mất, phá cooperative cancellation (user rời màn hình giữa lúc backup
            // đang chạy trong viewModelScope). Phải ném lại để coroutine dừng đúng lúc.
            // ponytail: không có test tự động cho nhánh này — cần huỷ Job ĐÚNG lúc đang chạy giữa
            // withContext(Dispatchers.IO) (dispatcher thật, hop sang thread pool), không có hook nào
            // để chèn cancel() tại điểm chính xác mà không tạo test timing-race giả. Nếu sau này cần,
            // thêm tham số Dispatcher inject được (giống pattern DelegatingWorkerFactory ở test worker)
            // để dùng TestDispatcher điều khiển thời điểm cancel tất định.
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreFrom(srcUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(srcUri) ?: return@withContext false
            val backup = input.use { BackupRestoreEngine.readBackup(it) }
            // Dedup theo content: restore lặp lại cùng 1 file backup (retry, hoặc máy đích đã có
            // sẵn template đó) không được nhân đôi — Template.id luôn = 0 (autoGenerate PK mới)
            // nên không có cách nào khác để nhận biết "đã tồn tại" ngoài so content.
            // BUG-AUDIT-2026-09-29: dùng MutableSet + cập nhật NGAY sau mỗi insert — trước đây
            // tính 1 lần trước vòng lặp nên 2 template trùng content NẰM TRONG CÙNG 1 file backup
            // không dedup lẫn nhau (chỉ dedup với DB hiện có), cả 2 đều lọt qua filter.
            val existingContents = templateRepo.getAllTemplate().first().map { it.content }.toMutableSet()
            backup.templates.forEach { template ->
                if (existingContents.add(template.content)) {
                    templateRepo.insertTemplate(template)
                }
            }
            backup.signatureFiles.forEach { (name, bytes) -> signatureRepo.importSignatureBytes(name, bytes) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
