package com.mckimquyen.watermark.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri

/**
 * FEAT-21: Hỗ trợ trích xuất URI hình ảnh từ Clipboard hệ thống để đóng dấu nhanh.
 */
object ClipboardImageHelper {

    /**
     * Kiểm tra xem trong Clipboard hiện tại có dữ liệu hình ảnh hợp lệ hay không.
     */
    fun hasImage(context: Context, clipDataOverride: ClipData? = null): Boolean {
        val clipData = clipDataOverride ?: run {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard?.hasPrimaryClip() == true) clipboard.primaryClip else null
        } ?: return false
        return extractImageUris(context, clipData).isNotEmpty()
    }

    /**
     * Trích xuất danh sách các [Uri] hình ảnh từ [ClipData].
     * Hỗ trợ cả hai trường hợp:
     * 1. Item mang scheme `content` hoặc `file` với MIME type hình ảnh.
     * 2. Item chứa chuỗi URL/URI hình ảnh hợp lệ.
     */
    fun extractImageUris(context: Context, clipData: ClipData?): List<Uri> {
        if (clipData == null || clipData.itemCount == 0) return emptyList()
        val resolver = context.contentResolver
        val result = mutableListOf<Uri>()

        for (i in 0 until clipData.itemCount) {
            val item = clipData.getItemAt(i) ?: continue

            // 1. Kiểm tra Uri trực tiếp từ ClipData.Item
            val uri = item.uri
            if (uri != null) {
                if (isImageUri(resolver, uri, clipData)) {
                    result.add(uri)
                    continue
                }
            }

            // 2. Fallback: Kiểm tra text nếu text là một Uri trỏ đến ảnh
            val text = item.text?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.startsWith("content://") || text.startsWith("file://"))) {
                kotlin.runCatching {
                    val parsedUri = Uri.parse(text)
                    if (isImageUri(resolver, parsedUri, clipData)) {
                        result.add(parsedUri)
                    }
                }
            }
        }
        return result
    }

    private fun isImageUri(
        resolver: android.content.ContentResolver,
        uri: Uri,
        clipData: ClipData
    ): Boolean {
        // 1. Kiểm tra qua FileUtils
        if (FileUtils.isImage(resolver, uri)) {
            return true
        }

        // 2. Kiểm tra qua clipData description mime type
        if (clipData.description.hasMimeType("image/*")) {
            return true
        }

        // 3. Fallback: kiểm tra scheme và ContentResolver trực tiếp
        val type = kotlin.runCatching { resolver.getType(uri) }.getOrNull()
        if (type?.startsWith("image", ignoreCase = true) == true) {
            return true
        }

        // 4. Fallback: file extension
        val path = uri.path?.lowercase().orEmpty()
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".bmp") || path.endsWith(".gif")
    }
}
