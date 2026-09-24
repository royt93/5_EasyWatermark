package com.mckimquyen.watermark.utils

import java.io.InputStream
import java.security.MessageDigest

/**
 * IDEA-07: hash SHA-256 nội dung ảnh gốc — dùng để nhúng vào QR động, xác thực nguồn gốc ảnh.
 * Thuần JVM (không phụ thuộc Android) để unit test không cần Robolectric.
 */
object HashUtils {

    private const val BUFFER_SIZE = 8192

    /** Đọc [input] tới hết (không load nguyên file vào RAM) rồi trả hash SHA-256 dạng hex thường. */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
