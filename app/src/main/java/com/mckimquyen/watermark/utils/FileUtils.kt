package com.mckimquyen.watermark.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile

class FileUtils {
    companion object {

        const val outPutFolderName = "WaterMarkCreator"

        /**
         * FEAT-08: liệt kê ảnh TRỰC TIẾP trong 1 cây thư mục SAF (không đệ quy subfolder, đúng AC) —
         * dùng [DocumentFile.getType] (đã có sẵn từ cursor liệt kê cây, không cần query
         * `ContentResolver` thêm lần nữa cho từng file như [isImage]).
         */
        @JvmStatic
        fun listImagesInTree(context: Context, treeUri: Uri): List<Uri> {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            return filterImageUris(root.listFiles().toList())
        }

        /** Tách riêng khỏi [listImagesInTree] để test được logic lọc mà không cần SAF/DocumentsProvider thật. */
        @JvmStatic
        fun filterImageUris(children: List<DocumentFile>): List<Uri> {
            return children
                .filter { it.isFile && isImage(it.type) }
                .map { it.uri }
        }

        /**
         * 获取文件类型
         */
        @JvmStatic
        @Throws(SecurityException::class)
        fun getFileTypeFromUri(resolver: ContentResolver, uri: Uri?): String? {
            if (uri == null) {
                return null
            }
            return when {
                uri.scheme == "content" && resolver.getType(uri) != null -> {
                    resolver.getType(uri)
                }

                else -> {
                    // content provider 无法通过下面的方式获取到信息
                    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                }
            }
        }

        private fun isImage(mimeType: String?): Boolean {
            return mimeType?.startsWith("image") ?: false
        }

        fun isImage(resolver: ContentResolver, uri: Uri?): Boolean {
            val mimeType = getFileTypeFromUri(resolver, uri)
            return isImage(mimeType)
        }

        /**
         * ENH-29: Dọn dẹp các file cache tạm (*_temp_*) để tránh tích luỹ rác không giới hạn.
         * Giữ lại tối đa [maxRetainedFiles] file mới nhất và xoá các file cũ hơn [maxAgeMs].
         *
         * @return Số lượng file đã được xoá thành công.
         */
        @JvmStatic
        fun cleanOldTempFiles(
            directory: java.io.File,
            maxRetainedFiles: Int = 3,
            maxAgeMs: Long = 24 * 60 * 60 * 1000L,
            nowMs: Long = System.currentTimeMillis()
        ): Int {
            if (!directory.exists() || !directory.isDirectory) return 0
            val files = directory.listFiles() ?: return 0
            val tempFiles = files.filter { it.isFile && it.name.contains("_temp_") }
                .sortedByDescending { it.lastModified() }

            var deletedCount = 0
            tempFiles.forEachIndexed { index, file ->
                val isOld = (nowMs - file.lastModified()) > maxAgeMs
                val exceedsRetainedCount = index >= maxRetainedFiles
                if (isOld || exceedsRetainedCount) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            return deletedCount
        }
    }
}
