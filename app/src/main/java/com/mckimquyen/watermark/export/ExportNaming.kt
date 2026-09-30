package com.mckimquyen.watermark.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.utils.AuthenticityKeyStore
import com.mckimquyen.watermark.utils.HashUtils
import com.mckimquyen.watermark.utils.LocationNameResolver
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
class ExportNaming @Inject constructor(
    /** IDEA-16: nguồn tên địa danh cho token {location}. */
    private val locationNameResolver: LocationNameResolver
) {

    /** Cho nơi không có DI (test, fragment tự tạo) — {location} luôn rỗng. Không dùng default param: Kotlin sinh thêm constructor rỗng mang @Inject, Dagger báo 2 constructor. */
    constructor() : this(LocationNameResolver.NONE)

    /** Cache theo uri hiện tại — preview gọi lại nhiều lần (mỗi ký tự gõ) không query lặp ContentResolver. */
    private var lastDisplayName: Pair<Uri, String>? = null

    /**
     * Resolve dynamic text tokens in the watermark text for a given image, per-image at export time
     * so batch jobs get per-photo values. No-op when the text has no '{' token.
     * Supported: {filename} {seq} {date} {model} {make} {iso} {fnumber} {exposure} {focal} {exif} {location} {recipient}
     */
    fun resolveTextTokens(
        text: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int,
        recipient: String? = null
    ): String {
        if (!text.contains('{')) return text
        return TextTokenResolver.resolve(text, buildBaseTokens(text, imageInfo, contentResolver, index, recipient))
    }

    /** Token dùng chung cho cả text watermark/tên file ([resolveTextTokens]) và QR động ([resolveQrContent]). */
    private fun buildBaseTokens(
        text: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int,
        recipient: String? = null
    ): Map<String, String> {
        val exif = imageInfo.exifModel
        val rawDate = exif?.dateTime?.takeIf { it.isNotBlank() }
        val parsedExifMs = rawDate?.let { com.mckimquyen.watermark.utils.bitmap.parseExifDateTime(it) }
        val effectiveMs = parsedExifMs ?: queryFileLastModified(contentResolver, imageInfo.uri) ?: System.currentTimeMillis()

        // FEAT-25: {date} chuẩn hoá yyyy-MM-dd nếu parse được, giữ rawDate nếu không parse được, fallback effectiveMs
        val date = parsedExifMs?.formatDate("yyyy-MM-dd") ?: rawDate ?: effectiveMs.formatDate("yyyy-MM-dd")
        val time = effectiveMs.formatDate("HH:mm")
        val datetime = effectiveMs.formatDate("yyyy-MM-dd HH:mm")

        return mapOf(
            "filename" to queryDisplayName(contentResolver, imageInfo.uri),
            "seq" to (index + 1).toString(),
            "seq3" to (index + 1).toString().padStart(3, '0'),
            "date" to date,
            "time" to time,
            "datetime" to datetime,
            "model" to exif?.getCameraName().orEmpty(),
            "make" to exif?.make.orEmpty(),
            "iso" to exif?.iso.orEmpty(),
            "fnumber" to exif?.fNumber.orEmpty(),
            "exposure" to exif?.exposureTime.orEmpty(),
            "focal" to exif?.focalLength.orEmpty(),
            "exif" to exif?.getFormattedExif().orEmpty(),
            // IDEA-16: reverse-geocode có thể gọi mạng — chỉ chạy khi text thật sự dùng token này.
            "location" to if (text.contains(LOCATION_TOKEN)) locationNameResolver.resolve(exif?.latitude, exif?.longitude) else "",
            // IDEA-10: mã hoặc tên người nhận cho token {recipient}
            "recipient" to recipient.orEmpty()
        )
    }

    /**
     * IDEA-07: resolve nội dung QR động cho 1 ảnh trong batch — thêm token {hash} (SHA-256 ảnh
     * gốc, đọc trực tiếp qua [contentResolver], KHÔNG dùng bitmap đã decode/downsample để hash
     * đúng nội dung file gốc) và {portfolio_link} vào cùng bộ token của [resolveTextTokens].
     * Đọc/hash lỗi (file bị xoá, không mở được stream...) → token {hash} rỗng, không chặn export.
     */
    fun resolveQrContent(
        template: String,
        imageInfo: ImageInfo,
        contentResolver: ContentResolver,
        index: Int,
        portfolioLink: String
    ): String {
        if (!template.contains('{')) return template
        val hash = try {
            contentResolver.openInputStream(imageInfo.uri)?.use { HashUtils.sha256(it) }.orEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        val tokens = buildBaseTokens(template, imageInfo, contentResolver, index) +
            mapOf("hash" to hash, "portfolio_link" to portfolioLink)
        return TextTokenResolver.resolve(template, tokens)
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
        outputFormat: Bitmap.CompressFormat,
        recipient: String? = null
    ): String {
        val pattern = outputNamePattern.trim()
        val resolvedBase = if (pattern.isEmpty()) {
            ""
        } else {
            // BUG-40: token EXIF ({exposure}/{fnumber}/{exif}) và chuỗi tự do ({filename}/
            // {recipient}/{location}) có thể chứa ký tự cấm hệ thống file — sanitize SAU khi
            // resolve token, TRƯỚC khi nối extension.
            sanitizeFileName(resolveTextTokens(pattern, imageInfo, contentResolver, index, recipient))
        }
        // BUG-AUDIT-2026-09-29: pattern không rỗng nhưng resolve+sanitize ra chuỗi rỗng (vd
        // "{filename}" trên ảnh có DISPLAY_NAME rỗng/chỉ có đuôi) từng lọt qua, sinh tên file ẩn
        // ".jpg" — dùng chung fallback timestamp để không bao giờ trả base rỗng. Kèm `index` (đã
        // có sẵn ở tham số hàm, cùng quy ước {seq}=index+1) để 2 ảnh cùng batch không đụng tên nếu
        // rơi đúng cùng 1 millisecond (review pass: timestamp thô không đủ phân biệt khi batch
        // nhanh/ảnh nhỏ) — áp dụng luôn cho case pattern rỗng hoàn toàn, cùng 1 fallback duy nhất.
        val base = resolvedBase.ifEmpty { "ewm_${System.currentTimeMillis()}_${index + 1}" }
        return "$base.${trapOutputExtension(outputFormat)}"
    }

    /**
     * FEAT-19: Ghép hậu tố phiên bản (_v2, _v3, ...) vào trước extension.
     * Ví dụ: "photo.jpg", version=2 -> "photo_v2.jpg"
     */
    fun buildVersionedName(originalName: String, version: Int): String {
        if (version <= 1) return originalName
        val dotIndex = originalName.lastIndexOf('.')
        return if (dotIndex != -1) {
            val name = originalName.substring(0, dotIndex)
            val ext = originalName.substring(dotIndex)
            "${name}_v$version$ext"
        } else {
            "${originalName}_v$version"
        }
    }

    /**
     * FEAT-19: Tìm tên tệp chưa bị trùng bằng cách tăng dần phiên bản (_v2, _v3, ...) cho tới khi
     * hàm [isNameTaken] trả về false.
     */
    fun resolveVersionedName(baseName: String, isNameTaken: (String) -> Boolean): String {
        if (!isNameTaken(baseName)) return baseName
        var version = 2
        while (true) {
            val candidate = buildVersionedName(baseName, version)
            if (!isNameTaken(candidate)) {
                return candidate
            }
            version++
        }
    }

    /**
     * FEAT-19: Truy vấn MediaStore để tìm Uri của file đã tồn tại cùng tên trong thư mục Pictures/WaterMarkCreator/.
     * Trả về Content Uri nếu tồn tại, null nếu không tìm thấy.
     */
    fun queryExistingMediaUri(
        contentResolver: ContentResolver,
        displayName: String,
        subFolder: String = com.mckimquyen.watermark.utils.FileUtils.outPutFolderName
    ): Uri? {
        val collection = android.provider.MediaStore.Images.Media.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
        )
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${android.provider.MediaStore.Images.Media.DISPLAY_NAME} = ? AND (${android.provider.MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} = ?)"
        val selectionArgs = arrayOf(
            displayName,
            "Pictures/$subFolder/",
            "Pictures/$subFolder"
        )
        return try {
            contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idIndex)
                    android.content.ContentUris.withAppendedId(collection, id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * FEAT-19: Kiểm tra xem tên file đã tồn tại trong MediaStore hay chưa.
     */
    fun isMediaFileExists(
        contentResolver: ContentResolver,
        displayName: String,
        subFolder: String = com.mckimquyen.watermark.utils.FileUtils.outPutFolderName
    ): Boolean = queryExistingMediaUri(contentResolver, displayName, subFolder) != null

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

    /**
     * IDEA-03: nhúng con dấu chứng thực vào EXIF ảnh đã lưu qua MediaStore/SAF (Android Q+).
     *
     * Gọi NGAY SAU [applyCopyrightExif] để hash phản ánh đúng file cuối cùng. Hash bỏ qua mọi segment
     * metadata (xem [JpegImageDigest]) nên việc chính hàm này ghi thêm EXIF KHÔNG làm con dấu tự sai.
     *
     * Chỉ JPEG: [JpegImageDigest] duyệt marker theo cấu trúc JPEG, format khác trả `null` và bỏ qua
     * im lặng. Mọi lỗi đều nuốt — không bao giờ chặn export vì metadata.
     */
    fun applyAuthenticityExif(
        contentResolver: ContentResolver,
        uri: Uri,
        owner: String,
        outputFormat: Bitmap.CompressFormat
    ) {
        if (outputFormat != Bitmap.CompressFormat.JPEG) return
        try {
            val hash = contentResolver.openInputStream(uri)?.use { JpegImageDigest.hashImageData(it) } ?: return
            val comment = buildStampComment(hash, owner) ?: return
            contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment)
                exif.saveAttributes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** IDEA-03: bản cho ảnh lưu theo đường dẫn file (Android < Q). Xem overload Uri ở trên. */
    fun applyAuthenticityExif(filePath: String, owner: String, outputFormat: Bitmap.CompressFormat) {
        if (outputFormat != Bitmap.CompressFormat.JPEG) return
        try {
            val hash = java.io.File(filePath).inputStream().use { JpegImageDigest.hashImageData(it) } ?: return
            val comment = buildStampComment(hash, owner) ?: return
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, comment)
            exif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Dựng chuỗi con dấu đã ký. `null` khi Keystore không dùng được — thà không có con dấu còn hơn
     * ghi một con dấu không ký được mà người xem lại tưởng là đã xác thực.
     */
    private fun buildStampComment(hash: String, owner: String): String? {
        val publicKey = AuthenticityKeyStore.publicKeyEncoded() ?: return null
        val unsigned = AuthenticityStamp.Stamp(
            hash = hash,
            timestampMs = System.currentTimeMillis(),
            owner = owner.trim(),
            publicKey = publicKey,
            signature = ""
        )
        val signature = AuthenticityKeyStore.sign(AuthenticityStamp.signedPayload(unsigned)) ?: return null
        return AuthenticityStamp.format(unsigned.copy(signature = signature))
    }

    companion object {
        const val LOCATION_TOKEN = "{location}"

        /**
         * FEAT-25: Truy vấn thời điểm sửa đổi hoặc chụp ảnh từ ContentResolver hoặc File Uri khi không có EXIF.
         */
        fun queryFileLastModified(contentResolver: ContentResolver, uri: Uri): Long? {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                    android.provider.MediaStore.Images.Media.DATE_TAKEN
                )
                return try {
                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val takenIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                            if (takenIdx >= 0 && !cursor.isNull(takenIdx)) {
                                val taken = cursor.getLong(takenIdx)
                                if (taken > 0) return@use taken
                            }
                            val modIdx = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                            if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                                val mod = cursor.getLong(modIdx)
                                if (mod > 0) return@use mod * 1000L
                            }
                        }
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        val lm = file.lastModified()
                        if (lm > 0) return lm
                    }
                }
            }
            return null
        }

        /** IDEA-07: template mặc định cho nội dung QR động khi user chưa tự đặt. */
        const val DEFAULT_QR_CONTENT_TEMPLATE = "{hash}|{date}|{portfolio_link}"

        /**
         * BUG-40: ký tự cấm hệ thống file/MediaStore/SAF (Windows + POSIX gộp) — nguồn regex DUY
         * NHẤT dùng chung với [com.mckimquyen.watermark.utils.ExportZipHelper.sanitizeAndDeduplicateEntryName],
         * tránh lặp lại quy tắc ở 2 nơi rồi lệch nhau dần theo thời gian.
         */
        private val FORBIDDEN_FILENAME_CHARS = "[/\\\\?%*:|\"<>]".toRegex()

        /**
         * BUG-40: token EXIF như `{exposure}`("1/125s")/`{fnumber}`("f/2.8") sinh ra `/` — nối
         * thẳng vào tên file MediaStore/legacy path làm export thất bại. Hàm thuần, không rút gọn
         * chuỗi rỗng/chỉ-toàn-ký-tự-cấm về fallback nào — caller tự quyết fallback theo ngữ cảnh
         * (xem [generateOutputName], [ExportZipHelper.sanitizeAndDeduplicateEntryName]).
         */
        fun sanitizeFileName(raw: String): String = raw.replace(FORBIDDEN_FILENAME_CHARS, "_")
    }
}
