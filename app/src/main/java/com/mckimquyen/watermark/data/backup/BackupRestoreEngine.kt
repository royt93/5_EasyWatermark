package com.mckimquyen.watermark.data.backup

import com.mckimquyen.watermark.data.model.entity.Template
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Kết quả đọc lại 1 file backup — chưa ghi vào DB/disk, xem [BackupRestoreEngine.readBackup]. */
data class RestoredBackup(
    val templates: List<Template>,
    val signatureFiles: List<Pair<String, ByteArray>>
)

/**
 * Đề xuất F (`doc/feat.md`): Backup/Restore Template & Signature — serialize thuần
 * `java.util.zip` (không cần thư viện JSON, không đụng Android framework) nên test được bằng
 * JUnit thường, không cần Robolectric. Mỗi Template ghi thành 1 file text riêng (2 dòng đầu là
 * epoch millis của creationDate/lastModifiedDate, phần còn lại là content nguyên văn) — tránh
 * hẳn việc phải escape ký tự đặc biệt (content là text watermark, có thể chứa xuống dòng).
 */
object BackupRestoreEngine {

    private const val TEMPLATES_DIR = "templates/"
    private const val SIGNATURES_DIR = "signatures/"

    /** BUG-31: file backup đến từ SAF (input không tin cậy) — giới hạn kích thước/entry và tổng
     * số entry để chặn OOM/zip-bomb (entry nén nhỏ nhưng giải nén ra khổng lồ). 20MB đủ cho chữ
     * ký lớn nhất hợp lý; 500 entry đủ cho vài trăm template/signature thật, chặn tấn công dạng
     * hàng chục nghìn entry rỗng. */
    private const val MAX_ENTRY_SIZE_BYTES = 20L * 1024 * 1024
    private const val MAX_ENTRY_COUNT = 500

    fun writeBackup(output: OutputStream, templates: List<Template>, signatureFiles: List<File>) {
        ZipOutputStream(output).use { zip ->
            templates.forEachIndexed { index, template ->
                zip.putNextEntry(ZipEntry("$TEMPLATES_DIR$index.txt"))
                val header = "${template.creationDate?.time ?: ""}\n${template.lastModifiedDate?.time ?: ""}\n"
                zip.write(header.toByteArray(Charsets.UTF_8))
                zip.write((template.content ?: "").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            signatureFiles.forEach { file ->
                zip.putNextEntry(ZipEntry("$SIGNATURES_DIR${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun readBackup(input: InputStream): RestoredBackup {
        val templates = mutableListOf<Template>()
        val signatures = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    zip.closeEntry()
                    break
                }
                val name = entry.name
                val bytes = readEntryBounded(zip, MAX_ENTRY_SIZE_BYTES)
                if (bytes != null) {
                    when {
                        name.startsWith(TEMPLATES_DIR) -> parseTemplateEntry(bytes)?.let { templates += it }
                        name.startsWith(SIGNATURES_DIR) -> signatures += name.removePrefix(SIGNATURES_DIR) to bytes
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return RestoredBackup(templates, signatures)
    }

    /**
     * Đọc toàn bộ entry hiện tại của [zip] theo từng chunk nhỏ — dừng ngay và trả `null` (bỏ qua
     * entry, KHÔNG giữ phần đã đọc) ngay khi vượt [maxBytes], thay vì gọi `readBytes()` vốn cấp
     * phát bộ nhớ không giới hạn theo dữ liệu ĐÃ GIẢI NÉN (không theo kích thước nén trong header
     * — đây là lý do zip-bomb khai thác được `readBytes()` gốc).
     */
    private fun readEntryBounded(zip: ZipInputStream, maxBytes: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = zip.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun parseTemplateEntry(bytes: ByteArray): Template? {
        val text = bytes.toString(Charsets.UTF_8)
        val firstNl = text.indexOf('\n')
        val secondNl = text.indexOf('\n', firstNl + 1)
        if (firstNl < 0 || secondNl < 0) return null
        val creation = text.substring(0, firstNl).toLongOrNull()
        val lastMod = text.substring(firstNl + 1, secondNl).toLongOrNull()
        val content = text.substring(secondNl + 1)
        return Template(
            id = 0,
            content = content,
            creationDate = creation?.let { Date(it) },
            lastModifiedDate = lastMod?.let { Date(it) }
        )
    }
}
