package com.mckimquyen.watermark.utils

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.mckimquyen.watermark.AppLog
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * FEAT-20: Đóng gói hàng loạt ảnh vừa export thành 1 file ZIP để chia sẻ qua Sharesheet
 * (Intent.ACTION_SEND với MIME application/zip).
 */
object ExportZipHelper {

    private const val TAG = "ExportZipHelper"
    private const val ZIP_CACHE_DIR = "zip_cache"
    private const val BUFFER_SIZE = 16384

    /**
     * Lấy thư mục cache chuyên dụng cho export ZIP: [cacheDir]/zip_cache.
     */
    fun getZipCacheDir(context: Context): File {
        val dir = File(context.cacheDir, ZIP_CACHE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Truy vấn tên hiển thị (display name) từ Uri nếu có (MediaStore / SAF / ContentProvider).
     * Fallback sang lastPathSegment.
     */
    fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            kotlin.runCatching {
                val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) {
                                return name
                            }
                        }
                    }
                }
            }
        }
        return uri.lastPathSegment
    }

    /**
     * Chuẩn hoá tên entry trong file ZIP:
     * 1. Chống Zip Slip / Path Traversal: chỉ lấy File(name).name.
     * 2. Thay thế ký tự cấm hệ thống bằng '_'.
     * 3. Xử lý trùng lặp: nếu [usedNames] đã có tên này, tự động đánh số tăng dần (ví dụ photo_2.jpg).
     */
    fun sanitizeAndDeduplicateEntryName(
        rawName: String?,
        fallbackIndex: Int,
        usedNames: MutableSet<String>
    ): String {
        val baseRaw = rawName?.let { File(it).name }?.trim().orEmpty()
        val sanitized = if (baseRaw.isBlank()) {
            "watermark_image_$fallbackIndex.jpg"
        } else {
            baseRaw.replace("[/\\\\?%*:|\"<>]".toRegex(), "_")
        }

        var candidate = sanitized
        var counter = 1
        val dotIndex = candidate.lastIndexOf('.')
        val (namePart, extPart) = if (dotIndex > 0) {
            candidate.substring(0, dotIndex) to candidate.substring(dotIndex)
        } else {
            candidate to ""
        }

        while (usedNames.contains(candidate)) {
            counter++
            candidate = "${namePart}_$counter$extPart"
        }
        usedNames.add(candidate)
        return candidate
    }

    /**
     * Nén danh sách các [uris] thành 1 tệp ZIP lưu tại [destZipFile].
     *
     * @param resolver [ContentResolver] dùng để đọc luồng ảnh.
     * @param uris Danh sách Uri cần nén.
     * @param destZipFile Tệp đích ghi dữ liệu nén.
     * @param resolveName Tuỳ chọn override tên entry (chủ yếu dùng cho testing).
     * @return [destZipFile] nếu có ít nhất 1 ảnh nén thành công; null nếu không có file nào được nén.
     */
    fun createZipArchive(
        resolver: ContentResolver,
        uris: List<Uri>,
        destZipFile: File,
        resolveName: ((Uri, Int) -> String?)? = null
    ): File? {
        if (uris.isEmpty()) return null
        val usedNames = mutableSetOf<String>()
        var successCount = 0

        destZipFile.parentFile?.let {
            if (!it.exists()) it.mkdirs()
        }

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use { zipOut ->
                uris.forEachIndexed { index, uri ->
                    val rawName = resolveName?.invoke(uri, index + 1) ?: queryDisplayName(resolver, uri)
                    val entryName = sanitizeAndDeduplicateEntryName(rawName, index + 1, usedNames)

                    var inputStream: InputStream? = null
                    try {
                        inputStream = resolver.openInputStream(uri)
                        if (inputStream != null) {
                            val zipEntry = ZipEntry(entryName)
                            zipOut.putNextEntry(zipEntry)
                            inputStream.copyTo(zipOut, BUFFER_SIZE)
                            zipOut.closeEntry()
                            successCount++
                        }
                    } catch (e: Exception) {
                        AppLog.d(TAG, "Failed to read uri $uri into zip: ${e.message}")
                    } finally {
                        kotlin.runCatching { inputStream?.close() }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.d(TAG, "Failed to create zip archive: ${e.message}")
            kotlin.runCatching { destZipFile.delete() }
            return null
        }

        if (successCount == 0) {
            kotlin.runCatching { destZipFile.delete() }
            return null
        }
        return destZipFile
    }

    /**
     * Cấp URI an toàn qua FileProvider để gửi qua Sharesheet.
     */
    fun getShareableZipUri(context: Context, zipFile: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    /**
     * Tạo Intent chia sẻ cho tệp ZIP với MIME type application/zip.
     */
    fun createShareZipIntent(context: Context, zipUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            clipData = ClipData.newUri(context.contentResolver, "Exported ZIP", zipUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
