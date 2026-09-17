package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.mckimquyen.watermark.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Không phải `data class` vì Kotlin bắt buộc mọi tham số constructor của data class phải là
 * `val`/`var` — `uriProvider` chỉ dùng để khởi tạo `uri` LAZY, không phải property. `uri` tính lazy
 * vì `FileProvider.getUriForFile()` chỉ nên gọi khi caller thật sự cần URI (ví dụ hiển thị
 * ảnh/trả kết quả), không phải mỗi lần build `SignatureModel` (test dedup/sanitize path không
 * cần URI, tránh gọi FileProvider thừa).
 */
class SignatureModel(
    val file: File,
    val dateModified: Long,
    uriProvider: () -> Uri
) {
    val uri: Uri by lazy(uriProvider)
}

@Singleton
class SignatureRepository @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        /**
         * BUG-34: `Bitmap.compress()` trả `Boolean` báo thành công/thất bại thật — trước đây bị
         * bỏ qua, để lại file rỗng/hỏng + trả `SignatureModel` giả khi encode thất bại (bitmap
         * hỏng, hết dung lượng...). Hàm thuần (không phụ thuộc Bitmap/Android runtime thật) để dễ
         * unit test, theo đúng pattern [com.mckimquyen.watermark.data.model.MediaStoreWriteResolver]
         * (BUG-19) — xoá file rác nếu ghi thất bại, không để lại signature hỏng trên đĩa.
         */
        internal fun resolveWriteResult(file: File, compressSucceeded: Boolean): Boolean {
            if (!compressSucceeded) {
                file.delete()
            }
            return compressSucceeded
        }
    }

    private val signatureDir: File
        get() {
            val dir = File(context.filesDir, "signatures")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private fun fileToContentUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }

    suspend fun getAllSignatures(): List<SignatureModel> = withContext(Dispatchers.IO) {
        val files = signatureDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.filter { it.isFile && it.name.endsWith(".webp") }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                SignatureModel(
                    file = file,
                    dateModified = file.lastModified(),
                    uriProvider = { fileToContentUri(file) }
                )
            }
    }

    suspend fun saveSignature(bitmap: Bitmap): SignatureModel? = withContext(Dispatchers.IO) {
        try {
            val fileName = "signature_${System.currentTimeMillis()}.webp"
            val file = File(signatureDir, fileName)
            val compressed = FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
            }
            if (!resolveWriteResult(file, compressed)) {
                return@withContext null
            }
            return@withContext SignatureModel(
                file = file,
                dateModified = file.lastModified(),
                uriProvider = { fileToContentUri(file) }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteSignature(model: SignatureModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (model.file.exists()) {
            model.file.delete()
        } else {
            false
        }
    }

    /**
     * Đề xuất F: ghi lại 1 signature từ backup (`BackupRestoreEngine`) — nếu trùng tên file với
     * signature đang có (restore lần 2, hoặc trùng tên do đồng bộ trước ENH), thêm hậu tố số thay
     * vì ghi đè, tránh mất signature cũ.
     */
    suspend fun importSignatureBytes(fileName: String, bytes: ByteArray): SignatureModel? = withContext(Dispatchers.IO) {
        try {
            // Zip-slip guard: fileName đến từ tên entry trong file zip backup do user chọn qua SAF
            // (không tin cậy) — File(...).name chỉ lấy phần tên cuối cùng, loại bỏ mọi "../" hay
            // đường dẫn tuyệt đối, không cho ghi ra ngoài signatureDir.
            val safeName = File(fileName).name.ifBlank { "signature_${System.currentTimeMillis()}.webp" }
            var target = File(signatureDir, safeName)
            var suffix = 1
            while (target.exists()) {
                target = File(signatureDir, "${safeName.substringBeforeLast('.')}_$suffix.${safeName.substringAfterLast('.')}")
                suffix++
            }
            target.writeBytes(bytes)
            return@withContext SignatureModel(
                file = target,
                dateModified = target.lastModified(),
                uriProvider = { fileToContentUri(target) }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
