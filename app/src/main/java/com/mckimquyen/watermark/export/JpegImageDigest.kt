package com.mckimquyen.watermark.export

import java.io.InputStream
import java.security.MessageDigest

/**
 * IDEA-03: hash SHA-256 phần DỮ LIỆU ẢNH của một file JPEG, cố tình BỎ QUA mọi segment metadata.
 *
 * Vì sao không hash cả file: pipeline export ghi EXIF SAU khi file đã ghi xong (xem 3 điểm gọi
 * `ExportNaming.applyCopyrightExif` trong `BatchExportEngine`). Nếu hash toàn bộ file rồi ghi hash
 * đó vào EXIF thì chính hành động ghi làm file đổi — hash tự phủ định mình.
 *
 * Vì sao không hash pixel đã decode: `BitmapFactory` decode qua Skia, phiên bản Skia khác nhau giữa
 * các đời Android cho pixel lệch nhau — ảnh xuất ở máy này verify ở máy kia sẽ báo sai.
 *
 * Cách làm: duyệt marker theo cấu trúc JPEG (`SOI` → các segment `FFxx + length + payload` → `SOS`
 * → entropy data → `EOI`), bỏ qua `APPn` (FFE0–FFEF) và `COM` (FFFE) — đúng những segment mà việc
 * ghi EXIF đụng vào — rồi hash mọi thứ còn lại. Kết quả ổn định ở mức byte, không cần decoder, và
 * hash tính TRƯỚC khi ghi EXIF vẫn khớp hash tính SAU.
 *
 * Thuần JVM để unit test chạy thẳng, không cần Robolectric.
 */
object JpegImageDigest {

    private const val MARKER_PREFIX = 0xFF
    private const val MARKER_SOI = 0xD8
    private const val MARKER_EOI = 0xD9
    private const val MARKER_SOS = 0xDA
    private const val MARKER_COM = 0xFE
    private const val MARKER_APP_FIRST = 0xE0
    private const val MARKER_APP_LAST = 0xEF

    /** Segment độc lập không có payload: RST0..RST7 (D0–D7), SOI (D8), EOI (D9), TEM (01). */
    private const val MARKER_RST_FIRST = 0xD0
    private const val MARKER_RST_LAST = 0xD7
    private const val MARKER_TEM = 0x01

    /** 2 byte độ dài của segment tính CẢ 2 byte đó, nên payload thật là length - 2. */
    private const val SEGMENT_LENGTH_FIELD_SIZE = 2

    private const val BUFFER_SIZE = 8192

    /**
     * Trả hash SHA-256 (hex thường) phần dữ liệu ảnh của [input], hoặc `null` nếu không phải JPEG
     * hợp lệ / file cụt giữa chừng. Đọc theo buffer, không nạp cả file vào RAM.
     *
     * Stream do phía gọi đóng.
     */
    fun hashImageData(input: InputStream): String? {
        val digest = MessageDigest.getInstance("SHA-256")

        // JPEG luôn mở đầu bằng SOI (FF D8).
        if (input.read() != MARKER_PREFIX || input.read() != MARKER_SOI) return null
        digest.update(MARKER_PREFIX.toByte())
        digest.update(MARKER_SOI.toByte())

        while (true) {
            val prefix = input.read()
            if (prefix == -1) return null // Hết file mà chưa gặp SOS/EOI → file cụt.
            if (prefix != MARKER_PREFIX) return null // Lạc khỏi ranh giới marker → không tin được.

            // Chuỗi FF đệm trước marker là hợp lệ theo chuẩn, bỏ qua hết.
            var marker = input.read()
            while (marker == MARKER_PREFIX) {
                marker = input.read()
            }
            if (marker == -1) return null

            when {
                marker == MARKER_EOI -> {
                    digest.update(MARKER_PREFIX.toByte())
                    digest.update(marker.toByte())
                    return digest.toHex()
                }

                // Từ SOS trở đi là entropy data — hash thẳng tới hết file, không parse tiếp.
                marker == MARKER_SOS -> {
                    digest.update(MARKER_PREFIX.toByte())
                    digest.update(marker.toByte())
                    return if (digestRest(input, digest)) digest.toHex() else null
                }

                // Marker độc lập, không mang payload.
                marker == MARKER_TEM || marker in MARKER_RST_FIRST..MARKER_RST_LAST -> {
                    digest.update(MARKER_PREFIX.toByte())
                    digest.update(marker.toByte())
                }

                else -> {
                    val length = readSegmentLength(input) ?: return null
                    val isMetadata = marker == MARKER_COM || marker in MARKER_APP_FIRST..MARKER_APP_LAST
                    if (isMetadata) {
                        // Chính là phần ghi EXIF đụng vào — bỏ qua hoàn toàn khỏi hash.
                        if (!skipFully(input, length)) return null
                    } else {
                        digest.update(MARKER_PREFIX.toByte())
                        digest.update(marker.toByte())
                        digest.update((length shr 8 and 0xFF).toByte())
                        digest.update((length and 0xFF).toByte())
                        if (!digestFully(input, digest, length)) return null
                    }
                }
            }
        }
    }

    /** Đọc 2 byte độ dài segment; `null` nếu cụt hoặc độ dài vô lý. */
    private fun readSegmentLength(input: InputStream): Int? {
        val high = input.read()
        val low = input.read()
        if (high == -1 || low == -1) return null
        val length = (high shl 8) or low
        return if (length < SEGMENT_LENGTH_FIELD_SIZE) null else length
    }

    private fun skipFully(input: InputStream, length: Int): Boolean {
        var remaining = length - SEGMENT_LENGTH_FIELD_SIZE
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size))
            if (read == -1) return false
            remaining -= read
        }
        return true
    }

    private fun digestFully(input: InputStream, digest: MessageDigest, length: Int): Boolean {
        var remaining = length - SEGMENT_LENGTH_FIELD_SIZE
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(remaining, buffer.size))
            if (read == -1) return false
            digest.update(buffer, 0, read)
            remaining -= read
        }
        return true
    }

    /** Hash phần còn lại của stream (entropy data sau SOS). */
    private fun digestRest(input: InputStream, digest: MessageDigest): Boolean {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return true
            digest.update(buffer, 0, read)
        }
    }

    private fun MessageDigest.toHex(): String = digest().joinToString("") { "%02x".format(it) }
}
