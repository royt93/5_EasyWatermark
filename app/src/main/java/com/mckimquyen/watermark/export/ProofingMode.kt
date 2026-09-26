package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.mckimquyen.watermark.AppLog
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.utils.FileUtils
import java.io.File

/** IDEA-13: cấu hình watermark proof lớn + index HTML gửi khách duyệt ảnh. */
object ProofingMode {

    data class Entry(val sequence: Int, val fileName: String)

    const val INDEX_FILE_NAME = "proof_index.html"
    const val PROOF_TEXT_SIZE = 48f
    const val PROOF_ALPHA = 180
    const val PROOF_DEGREE = -30f

    // Gap nhỏ: ô tile = khung chữ × (1 + gap%) — gap lớn làm ô vượt cả ảnh, chữ (vẽ giữa ô) rơi ra ngoài.
    const val PROOF_HORIZONTAL_GAP = 20
    const val PROOF_VERTICAL_GAP = 20
    private const val DEFAULT_PROOF_TEXT = "PROOF"
    private const val DOCUMENTS_RELATIVE_PATH = "Documents/${FileUtils.outPutFolderName}"
    private const val LOG_TAG = "ProofingMode"

    /**
     * Ép cấu hình proofing tối thiểu: text lớn lặp toàn ảnh, số thứ tự riêng từng ảnh, bỏ layer/QR/EXIF.
     * ponytail: các thông số cố định; thêm núm chỉnh khi smoke test người dùng cho thấy cần tuỳ biến.
     */
    fun overrideConfig(base: WaterMark): WaterMark = base.copy(
        // Text CỐ ĐỊNH ngắn (không dùng base.text): text dài của user khi xoay -30° tạo ô tile
        // rộng hơn cả ảnh nên phần "#001" bị đẩy ra ngoài mép — khách không thấy số thứ tự.
        text = withSeq(DEFAULT_PROOF_TEXT),
        textSize = PROOF_TEXT_SIZE,
        alpha = PROOF_ALPHA,
        degree = PROOF_DEGREE,
        hGap = PROOF_HORIZONTAL_GAP,
        vGap = PROOF_VERTICAL_GAP,
        markMode = WaterMarkRepository.MarkMode.Text,
        enableExif = false,
        extraLayers = emptyList(),
        qrDynamicEnabled = false,
        qrContentTemplate = "",
        qrPortfolioLink = ""
    )

    fun withSeq(text: String): String = "${text.trim()}  #{seq3}"

    /** HTML tự chứa CSS, tham chiếu ảnh tương đối cùng thư mục; không sinh thumbnail phụ. */
    fun buildHtml(entries: List<Entry>): String = buildString {
        append("<!doctype html><html lang=\"vi\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>Client Proofs</title><style>")
        append("body{font-family:system-ui,sans-serif;margin:24px;background:#f7f7f7;color:#222}")
        append("h1{font-size:24px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:16px}")
        append("figure{margin:0;padding:12px;background:#fff;border-radius:12px;box-shadow:0 2px 8px #0002}")
        append("img{display:block;width:100%;height:auto;border-radius:8px}figcaption{padding-top:8px;font-weight:700}")
        append("</style></head><body><h1>Client Proofs</h1><div class=\"grid\">")
        entries.forEach { entry ->
            val fileName = escapeHtml(entry.fileName)
            append("<figure><img src=\"").append(fileName).append("\" alt=\"Proof #")
                .append(formatSequence(entry.sequence)).append("\"><figcaption>#")
                .append(formatSequence(entry.sequence)).append(" · ").append(fileName)
                .append("</figcaption></figure>")
        }
        append("</div></body></html>")
    }

    suspend fun writeIndex(context: Context, entries: List<Entry>, outputDirectoryUri: Uri?): Uri? {
        if (entries.isEmpty()) return null
        val html = buildHtml(entries)
        return try {
            when {
                outputDirectoryUri != null -> writeSaf(context, outputDirectoryUri, html)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> writeMediaStore(context.contentResolver, html)
                else -> writeLegacy(html)
            }
        } catch (e: Exception) {
            AppLog.d(LOG_TAG, "Không ghi được proof index: ${e.message}")
            null
        }
    }

    internal fun writeIntoDocumentTree(root: DocumentFile, resolver: ContentResolver, entries: List<Entry>): Uri? {
        root.findFile(INDEX_FILE_NAME)?.delete()
        val target = root.createFile("text/html", INDEX_FILE_NAME) ?: return null
        val html = buildHtml(entries)
        val success = resolver.openOutputStream(target.uri)?.use { it.write(html.toByteArray()) } != null
        return target.uri.takeIf { success }
    }

    private fun writeSaf(context: Context, treeUri: Uri, html: String): Uri? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        root.findFile(INDEX_FILE_NAME)?.delete()
        val target = root.createFile("text/html", INDEX_FILE_NAME) ?: return null
        context.contentResolver.openOutputStream(target.uri)?.use { it.write(html.toByteArray()) } ?: return null
        return target.uri
    }

    private fun writeMediaStore(resolver: ContentResolver, html: String): Uri? {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "$DOCUMENTS_RELATIVE_PATH/"
        // MediaStore tự đổi tên khi trùng (proof_index (1).html) — xoá bản cũ cùng thư mục trước
        // để export lần sau ghi đè đúng 1 file index duy nhất.
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(relativePath, INDEX_FILE_NAME)
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, INDEX_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) } ?: return null
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(html: String): Uri? {
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), FileUtils.outPutFolderName)
        if (!dir.exists() && !dir.mkdirs()) return null
        return Uri.fromFile(File(dir, INDEX_FILE_NAME).apply { writeText(html) })
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun formatSequence(sequence: Int): String = sequence.toString().padStart(3, '0')
}
