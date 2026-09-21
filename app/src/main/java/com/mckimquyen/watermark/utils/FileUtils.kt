package com.mckimquyen.watermark.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile

class FileUtils {
    companion object {

        const val outPutFolderName = "WaterMarkCreator"

        // ENH-33: giới hạn đệ quy khi bật "Include subfolders" — tránh quét quá sâu/quá nhiều ảnh
        // gây treo UI trên cây thư mục lớn (vd toàn bộ DCIM).
        const val RECURSIVE_SCAN_MAX_DEPTH = 5
        const val RECURSIVE_SCAN_MAX_FILES = 500

        /**
         * FEAT-08: liệt kê ảnh trong 1 cây thư mục SAF — mặc định chỉ lấy ảnh TRỰC TIẾP (không đệ
         * quy, đúng AC gốc). ENH-33: [includeSubfolders] bật đệ quy có giới hạn tầng/số ảnh (tuỳ
         * chọn "Include subfolders" ở dialog chọn thư mục, mặc định TẮT để giữ hành vi cũ).
         */
        @JvmStatic
        fun listImagesInTree(
            context: Context,
            treeUri: Uri,
            includeSubfolders: Boolean = false,
            maxDepth: Int = RECURSIVE_SCAN_MAX_DEPTH,
            maxFiles: Int = RECURSIVE_SCAN_MAX_FILES
        ): List<Uri> {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
            if (!includeSubfolders) {
                return filterImageUris(root.listFiles().toList())
            }
            return collectImagesRecursively(root, maxDepth, maxFiles)
        }

        /** Tách riêng khỏi [listImagesInTree] để test được logic lọc mà không cần SAF/DocumentsProvider thật. */
        @JvmStatic
        fun filterImageUris(children: List<DocumentFile>): List<Uri> {
            return children
                .filter { it.isFile && isImage(it.type) }
                .map { it.uri }
        }

        /**
         * ENH-33: duyệt BFS cây thư mục SAF, dừng khi đạt [maxFiles] ảnh hoặc quá [maxDepth] tầng
         * con — dừng sớm (không duyệt tiếp cây con khi đã đủ ảnh) thay vì thu thập hết rồi cắt, để
         * thật sự tránh quét quá sâu/quá nhiều trên cây lớn.
         */
        @JvmStatic
        internal fun collectImagesRecursively(root: DocumentFile, maxDepth: Int, maxFiles: Int): List<Uri> {
            val result = mutableListOf<Uri>()
            val queue = ArrayDeque<Pair<DocumentFile, Int>>()
            queue.add(root to 0)
            while (queue.isNotEmpty() && result.size < maxFiles) {
                val (dir, depth) = queue.removeFirst()
                for (child in dir.listFiles()) {
                    if (result.size >= maxFiles) break
                    if (child.isFile && isImage(child.type)) {
                        result.add(child.uri)
                    } else if (child.isDirectory && depth < maxDepth) {
                        queue.add(child to depth + 1)
                    }
                }
            }
            return result
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

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

        private fun isImage(mimeType: String?): Boolean {
            return mimeType?.startsWith("image") ?: false
        }

        fun isImage(resolver: ContentResolver, uri: Uri?): Boolean {
            if (uri == null) return false
            val mimeType = getFileTypeFromUri(resolver, uri)
            if (isImage(mimeType)) return true
            val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
            val path = uri.path?.lowercase().orEmpty()
            return ext in IMAGE_EXTENSIONS || IMAGE_EXTENSIONS.any { path.endsWith(".$it") }
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
