package com.mckimquyen.watermark.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

/**
 * Sinh ảnh QR code từ một chuỗi nội dung (URL bản quyền, liên hệ, portfolio...).
 * Bitmap xuất ra được đẩy vào đúng luồng Image watermark (giống Signature).
 */
object QrCodeGenerator {

    const val DEFAULT_SIZE = 512
    private const val DEFAULT_MARGIN = 1

    /** Thư mục QR dưới cacheDir (preview/tạm, bị dọn) và dưới filesDir (QR đã xác nhận, bền). */
    const val QR_DIR_NAME = "qrcodes"

    /** BUG-64: tên file QR bền — KHÔNG chứa `_temp_` để [FileUtils.cleanOldTempFiles] không bao giờ khớp. */
    const val PERSISTENT_FILE_PREFIX = "qr_"

    /** Path đầu tiên của URI FileProvider cũ (`cache-path name="qr_codes"` trong filepaths.xml). */
    const val LEGACY_QR_URI_PATH_PREFIX = "/qr_codes/"

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val PARTIAL_SUFFIX = ".part"
    private const val PNG_QUALITY = 100
    private const val MAX_RANDOM_SUFFIX = 9999

    /**
     * Encode [content] thành QR bitmap vuông cạnh [size]px.
     *
     * @param content nội dung cần mã hoá; nếu rỗng/blank thì trả về null.
     * @param size cạnh bitmap (px), bị ép tối thiểu 1.
     * @param foreground màu các ô đen của QR.
     * @param background màu nền — mặc định TRẮNG ĐẶC để QR hiện rõ và máy quét đọc được
     *        (QR nền trong suốt / tương phản kém thường không quét được).
     * @return [Bitmap] QR hoặc null nếu nội dung rỗng / encode thất bại.
     */
    fun generate(
        content: String,
        size: Int = DEFAULT_SIZE,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        val side = size.coerceAtLeast(1)
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to DEFAULT_MARGIN,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, side, side, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) foreground else background
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Ghi [bitmap] thành PNG trong cache dir (`qrcodes/`) rồi trả content:// Uri qua FileProvider.
     * [prefix] phân biệt file QR tĩnh (`qr_temp_`) vs động (`qr_dyn_`, IDEA-07 BatchExportEngine) —
     * dọn dẹp định kỳ qua [FileUtils.cleanOldTempFiles]. CHỈ cho dữ liệu tạm: URI này có thể chết bất kỳ
     * lúc nào, đừng lưu vào DataStore/profile (dùng [saveToFiles], BUG-64).
     */
    fun saveToCache(context: Context, bitmap: Bitmap, prefix: String): Uri? =
        writePng(context, bitmap, context.cacheDir, prefix)

    /** BUG-64: như [saveToCache] nhưng ghi vào `filesDir/qrcodes/` — kho bền cho QR user đã xác nhận. */
    fun saveToFiles(context: Context, bitmap: Bitmap, prefix: String): Uri? =
        writePng(context, bitmap, context.filesDir, prefix)

    private fun writePng(context: Context, bitmap: Bitmap, root: File, prefix: String): Uri? {
        if (bitmap.isRecycled) return null
        return try {
            val dir = File(root, QR_DIR_NAME)
            dir.mkdirs()
            val file = File(dir, "$prefix${System.currentTimeMillis()}_${(0..MAX_RANDOM_SUFFIX).random()}.png")
            val compressed = FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, fos)
            }
            // BUG-66: `compress()` trả false (encoder/ghi thất bại) mà không ném — trước đây vẫn cấp URI
            // cho file rỗng/hỏng và báo thành công. Cùng pattern BUG-34 (SignatureRepository).
            if (!keepFileIfWritten(file, compressed)) return null
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * BUG-64: URI QR cũ (trỏ `cacheDir`, đã lưu trong DataStore/MRU/profile) → copy sang kho bền nếu file
     * còn, `null` nếu file đã mất. URI không phải QR cache cũ trả NGUYÊN VẸN (ảnh gallery, QR đã bền...).
     * Chỉ dựa vào việc mở stream qua chính provider của app, không suy đường dẫn ngược.
     */
    fun promoteLegacyCacheUri(context: Context, uri: Uri): Uri? {
        if (!isLegacyCacheQrUri(context, uri)) return uri
        return try {
            val dir = File(context.filesDir, QR_DIR_NAME).apply { mkdirs() }
            val name = "$PERSISTENT_FILE_PREFIX${System.currentTimeMillis()}_${(0..MAX_RANDOM_SUFFIX).random()}.png"
            val target = File(dir, name)
            // Ghi file tạm rồi rename: tiến trình chết giữa chừng chỉ để lại `.part` (không ai tham chiếu),
            // không bao giờ để URI bền trỏ vào PNG dang dở.
            val partial = File(dir, "$name$PARTIAL_SUFFIX")
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partial).use { out -> input.copyTo(out) }
                true
            } ?: false
            if (!keepFileIfWritten(partial, copied) || !partial.renameTo(target)) {
                partial.delete()
                return null
            }
            FileProvider.getUriForFile(context, "${context.packageName}$FILE_PROVIDER_SUFFIX", target)
        } catch (e: Exception) {
            // File cache đã bị dọn (FileNotFoundException) hoặc provider từ chối → coi như QR đã mất.
            null
        }
    }

    private fun isLegacyCacheQrUri(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" &&
            uri.authority == "${context.packageName}$FILE_PROVIDER_SUFFIX" &&
            uri.path?.startsWith(LEGACY_QR_URI_PATH_PREFIX) == true

    /**
     * BUG-66: hàm thuần (không đụng Bitmap/Android thật) để test nhánh `compress()==false` — xoá [file] rác
     * khi ghi thất bại hoặc file rỗng. @return true nếu [file] hợp lệ, được phép cấp URI.
     */
    internal fun keepFileIfWritten(file: File, compressSucceeded: Boolean): Boolean {
        if (!compressSucceeded || !file.exists() || file.length() == 0L) {
            file.delete()
            return false
        }
        return true
    }
}
