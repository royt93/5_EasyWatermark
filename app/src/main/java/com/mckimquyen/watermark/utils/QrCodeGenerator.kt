package com.mckimquyen.watermark.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

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
}
