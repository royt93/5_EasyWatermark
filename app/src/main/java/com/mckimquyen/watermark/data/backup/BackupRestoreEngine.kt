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
            while (entry != null) {
                val name = entry.name
                val bytes = zip.readBytes()
                when {
                    name.startsWith(TEMPLATES_DIR) -> parseTemplateEntry(bytes)?.let { templates += it }
                    name.startsWith(SIGNATURES_DIR) -> signatures += name.removePrefix(SIGNATURES_DIR) to bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return RestoredBackup(templates, signatures)
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
