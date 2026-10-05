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
     * [prefix] phân biệt file QR tĩnh (`qr_temp_`, QrCodeBottomSheetFragment) vs động
     * (`qr_dyn_`, IDEA-07 BatchExportEngine) — dọn dẹp định kỳ qua [FileUtils.cleanOldTempFiles].
     */
    fun saveToCache(context: Context, bitmap: Bitmap, prefix: String): Uri? {
        if (bitmap.isRecycled) return null
        return try {
            val cachePath = File(context.cacheDir, "qrcodes")
            cachePath.mkdirs()
            val file = File(cachePath, "$prefix${System.currentTimeMillis()}_${(0..9999).random()}.png")
            val compressed = FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            // BUG-66: `compress()` trả false (encoder/ghi thất bại) mà không ném — trước đây vẫn cấp URI
            // cho file rỗng/hỏng và báo thành công. Cùng pattern BUG-34 (SignatureRepository).
            if (!keepFileIfWritten(file, compressed)) return null
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

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
