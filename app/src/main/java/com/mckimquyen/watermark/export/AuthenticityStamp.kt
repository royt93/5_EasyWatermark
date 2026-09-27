package com.mckimquyen.watermark.export

import android.util.Base64

/**
 * IDEA-03: định dạng con dấu chứng thực nhúng vào EXIF `UserComment`.
 *
 * Dạng chuỗi: `EWM1|<hash hex>|<timestamp ms>|<owner base64>|<khoá công khai base64>|<chữ ký base64>`
 *
 * `owner` encode base64 vì tên người có thể chứa chính ký tự `|` phân tách. Tiền tố phiên bản
 * [VERSION] để đời sau đổi định dạng mà không hiểu nhầm con dấu cũ.
 *
 * Con dấu mang theo KHOÁ CÔNG KHAI chứ không chỉ vân tay: thiếu nó thì máy khác không xác minh nổi
 * chữ ký, mà so vân tay suông thì vô nghĩa (kẻ giả mạo chép lại vân tay là xong). Vân tay hiển thị
 * cho người dùng được tính lại từ chính khoá này nên không thể mâu thuẫn với nó.
 *
 * Phần ký ([signedPayload]) CỐ TÌNH không gồm chữ ký, và dùng chung một hàm cho cả lúc ký lẫn lúc
 * xác minh — hai bên lệch nhau một ký tự là chữ ký không bao giờ khớp.
 */
object AuthenticityStamp {

    const val VERSION = "EWM1"

    private const val SEPARATOR = '|'
    private const val FIELD_COUNT = 6

    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING

    data class Stamp(
        /** SHA-256 phần dữ liệu ảnh, xem [JpegImageDigest.hashImageData]. */
        val hash: String,
        val timestampMs: Long,
        val owner: String,
        /** Khoá công khai đã ký, base64 X.509 — xem `AuthenticityKeyStore.publicKeyEncoded`. */
        val publicKey: String,
        /** Chữ ký ECDSA của [signedPayload], base64. Rỗng nếu ký thất bại (vẫn giữ được phần hash). */
        val signature: String
    )

    fun format(stamp: Stamp): String = buildString {
        append(VERSION).append(SEPARATOR)
        append(stamp.hash).append(SEPARATOR)
        append(stamp.timestampMs).append(SEPARATOR)
        append(encode(stamp.owner)).append(SEPARATOR)
        append(stamp.publicKey).append(SEPARATOR)
        append(stamp.signature)
    }

    /** `null` khi chuỗi không phải con dấu hợp lệ (sai phiên bản, thiếu trường, timestamp hỏng). */
    fun parse(raw: String?): Stamp? {
        if (raw.isNullOrBlank()) return null
        // limit = FIELD_COUNT: base64 không sinh ký tự '|' nên 5 trường đầu luôn tách đúng; phần dư
        // (nếu có) dồn hết vào trường chữ ký và sẽ tự fail ở bước verify, không nhận nhầm là hợp lệ.
        val parts = raw.trim().split(SEPARATOR, limit = FIELD_COUNT)
        if (parts.size != FIELD_COUNT) return null
        if (parts[0] != VERSION) return null

        val hash = parts[1]
        if (hash.isEmpty()) return null
        val timestamp = parts[2].toLongOrNull() ?: return null
        val owner = decode(parts[3]) ?: return null

        return Stamp(
            hash = hash,
            timestampMs = timestamp,
            owner = owner,
            publicKey = parts[4],
            signature = parts[5]
        )
    }

    /**
     * Đúng phần dữ liệu được ký: mọi trường TRỪ chữ ký. Dùng chung cho cả ký và xác minh.
     */
    fun signedPayload(stamp: Stamp): ByteArray =
        listOf(VERSION, stamp.hash, stamp.timestampMs.toString(), encode(stamp.owner), stamp.publicKey)
            .joinToString(SEPARATOR.toString())
            .toByteArray(Charsets.UTF_8)

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), BASE64_FLAGS)

    private fun decode(value: String): String? {
        if (value.isEmpty()) return ""
        return try {
            String(Base64.decode(value, BASE64_FLAGS), Charsets.UTF_8)
        } catch (iae: IllegalArgumentException) {
            iae.printStackTrace()
            null
        }
    }
}
