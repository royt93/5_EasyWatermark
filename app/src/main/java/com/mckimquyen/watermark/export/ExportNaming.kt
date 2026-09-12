package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.utils.TextTokenResolver
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
import com.mckimquyen.watermark.utils.ktx.formatDate
import javax.inject.Inject

/**
 * ENH-01: trích xuất khỏi `MainViewModel` (nguyên vẹn, không đổi logic) — export batch giờ chạy
 * trong `BatchExportWorker` (không có instance `MainViewModel`), cần các hàm resolve tên
 * file/token/copyright dùng chung được ở cả Worker lẫn `MainViewModel.resolvePreviewText` (preview
 * gõ text trong editor). Nhận `outputNamePattern`/`outputFormat`/`copyright` qua tham số thay vì
 * đọc trực tiếp từ `UserConfigRepository` — vì đây không phải ViewModel nên không có
 * `viewModelScope`/`StateFlow` cache sẵn; `MainViewModel`/`BatchExportEngine` tự đọc 1 lần rồi
 * truyền vào, tránh query DataStore lặp lại không cần thiết mỗi ảnh trong batch.
 */
class ExportNaming @Inject constructor() {

    /** Cache theo uri hiện tại — preview gọi lại nhiều lần (mỗi ký tự gõ) không query lặp ContentResolver. */
    private var lastDisplayName: Pair<Uri, String>? = null

    /**
     * Resolve dynamic text tokens in the watermark text for a given image, per-image at export time
     * so batch jobs get per-photo values. No-op when the text has no '{' token.
     * Supported: {filename} {seq} {date} {model} {make} {iso} {fnumber} {exposure} {focal} {exif}
     */
    fun resolveTextTokens(
        text: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int
    ): String {
        if (!text.contains('{')) return text
        val exif = imageInfo.exifModel
        val date = exif?.dateTime?.takeIf { it.isNotBlank() }
            ?: System.currentTimeMillis().formatDate("yyyy-MM-dd")
        val tokens = mapOf(
            "filename" to queryDisplayName(contentResolver, imageInfo.uri),
            "seq" to (index + 1).toString(),
            "date" to date,
            "model" to exif?.getCameraName().orEmpty(),
            "make" to exif?.make.orEmpty(),
            "iso" to exif?.iso.orEmpty(),
            "fnumber" to exif?.fNumber.orEmpty(),
            "exposure" to exif?.exposureTime.orEmpty(),
            "focal" to exif?.focalLength.orEmpty(),
            "exif" to exif?.getFormattedExif().orEmpty()
        )
        return TextTokenResolver.resolve(text, tokens)
    }

    fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String {
        lastDisplayName?.let { (cachedUri, cachedName) -> if (cachedUri == uri) return cachedName }
        val name = try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    (if (nameIndex >= 0) cursor.getString(nameIndex) else null)?.substringBeforeLast('.')
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringBeforeLast('.').orEmpty()
        } catch (e: Exception) {
            uri.lastPathSegment?.substringBeforeLast('.').orEmpty()
        }
        lastDisplayName = uri to name
        return name
    }

    /**
     * Tên file xuất — mặc định "ewm_{timestamp}" nếu user chưa đặt [outputNamePattern] (rỗng);
     * nếu có pattern, resolve token qua đúng [resolveTextTokens] đang dùng cho text watermark
     * (vd "{filename}_wm_{seq}"). Phần đuôi file luôn theo [trapOutputExtension].
     */
    fun generateOutputName(
        contentResolver: ContentResolver,
        imageInfo: ImageInfo,
        index: Int,
        outputNamePattern: String,
        outputFormat: Bitmap.CompressFormat
    ): String {
        val pattern = outputNamePattern.trim()
        val base = if (pattern.isEmpty()) {
            "ewm_${System.currentTimeMillis()}"
        } else {
            resolveTextTokens(pattern, imageInfo, contentResolver, index)
        }
        return "$base.${trapOutputExtension(outputFormat)}"
    }

    fun trapOutputExtension(outputFormat: Bitmap.CompressFormat): String {
        return OutputImageUtils.extensionFor(outputFormat)
    }

    /** Định dạng có hỗ trợ ghi EXIF (androidx ExifInterface): JPEG / WEBP / PNG. */
    fun supportsExifWrite(outputFormat: Bitmap.CompressFormat): Boolean = outputFormat != Bitmap.CompressFormat.PNG

    /** Nhúng copyright vào EXIF cho ảnh đã lưu qua MediaStore (Android Q+). */
    fun applyCopyrightExif(
        contentResolver: ContentResolver,
        uri: Uri,
        copyright: String,
        outputFormat: Bitmap.CompressFormat
    ) {
        val text = copyright.trim()
        if (text.isEmpty() || !supportsExifWrite(outputFormat)) return
        try {
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_COPYRIGHT, text)
                exif.setAttribute(ExifInterface.TAG_ARTIST, text)
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Nhúng copyright vào EXIF cho ảnh lưu theo đường dẫn file (Android < Q). */
    fun applyCopyrightExif(filePath: String, copyright: String, outputFormat: Bitmap.CompressFormat) {
        val text = copyright.trim()
        if (text.isEmpty() || !supportsExifWrite(outputFormat)) return
        try {
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, text)
            exif.setAttribute(ExifInterface.TAG_ARTIST, text)
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
