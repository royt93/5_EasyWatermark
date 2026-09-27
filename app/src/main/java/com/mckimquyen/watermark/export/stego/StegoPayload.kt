package com.mckimquyen.watermark.export.stego

/**
 * IDEA-02: khuôn payload nhúng vào lớp watermark ẩn.
 *
 * Bố cục 64 bit cố định:
 * ```
 * [ 16 bit MAGIC ][ 32 bit ID chủ sở hữu ][ 16 bit CRC ]
 * ```
 *
 * Vì sao cần MAGIC + CRC chứ không nhúng thẳng ID: đọc một ảnh KHÔNG có watermark vẫn luôn ra 64 bit
 * nào đó (nhiễu ảnh tự nhiên cũng cho ra quan hệ lớn-bé ngẫu nhiên giữa 2 hệ số). Không có cách tự
 * kiểm, hệ thống sẽ bịa ra một ID chủ sở hữu từ ảnh của người lạ — sai nguy hiểm hơn là không đọc
 * được. MAGIC lọc thô, CRC bắt phần còn lại; xác suất nhiễu qua được cả hai là ~1/2^32.
 *
 * ID 32 bit là hash rút gọn của chuỗi chủ sở hữu, KHÔNG phải chính chuỗi đó: 64 bit không đủ chứa
 * tên, và mục đích ở đây là *đối chiếu* ("ảnh này có phải của tôi không") chứ không phải *hiển thị*.
 * Tên đầy đủ đã nằm ở con dấu EXIF của IDEA-03; lớp ẩn là phao cứu sinh khi EXIF bị nền tảng xoá.
 *
 * Thuần JVM → unit test chạy thẳng.
 */
object StegoPayload {

    const val TOTAL_BITS = 64

    private const val MAGIC_BITS = 16
    private const val ID_BITS = 32
    private const val CRC_BITS = 16

    /** Nhận dạng "đây là payload của app này" — chọn giá trị không đối xứng để nhiễu khó trùng. */
    private const val MAGIC = 0xEA02

    private const val CRC16_POLYNOMIAL = 0x1021
    private const val CRC16_INIT = 0xFFFF

    /** Ngưỡng tin cậy tối thiểu để dám khẳng định đã đọc được watermark (xem [StegoCodec.Decoded]). */
    const val MIN_CONFIDENCE = 0.90

    data class Payload(val ownerId: Int)

    /** ID 32 bit từ chuỗi chủ sở hữu. Chuỗi rỗng vẫn cho ID hợp lệ (ổn định, khác 0). */
    fun ownerIdOf(owner: String): Int {
        var hash = 0x811C9DC5.toInt() // FNV-1a 32 bit: ngắn, phân bố đều, đủ cho việc đối chiếu.
        for (char in owner.trim()) {
            hash = hash xor char.code
            hash *= 0x01000193
        }
        return hash
    }

    /** Dựng 64 bit để nhúng. */
    fun encode(ownerId: Int): BooleanArray {
        val bits = BooleanArray(TOTAL_BITS)
        writeInt(bits, 0, MAGIC_BITS, MAGIC)
        writeInt(bits, MAGIC_BITS, ID_BITS, ownerId)
        val crc = crc16(bits, 0, MAGIC_BITS + ID_BITS)
        writeInt(bits, MAGIC_BITS + ID_BITS, CRC_BITS, crc)
        return bits
    }

    /**
     * Giải 64 bit đọc được. Trả `null` khi MAGIC sai hoặc CRC không khớp — tức là không có watermark,
     * hoặc có nhưng đã hỏng quá mức tin được. Cả hai trường hợp đều phải im lặng chứ không đoán bừa.
     */
    fun decode(bits: BooleanArray): Payload? {
        if (bits.size != TOTAL_BITS) return null
        if (readInt(bits, 0, MAGIC_BITS) != MAGIC) return null

        val expectedCrc = crc16(bits, 0, MAGIC_BITS + ID_BITS)
        if (readInt(bits, MAGIC_BITS + ID_BITS, CRC_BITS) != expectedCrc) return null

        return Payload(ownerId = readInt(bits, MAGIC_BITS, ID_BITS))
    }

    /** CRC-16/CCITT-FALSE trên [length] bit đầu tính từ [offset]. */
    private fun crc16(bits: BooleanArray, offset: Int, length: Int): Int {
        var crc = CRC16_INIT
        for (i in 0 until length) {
            val bit = bits[offset + i]
            val msb = (crc and 0x8000) != 0
            crc = (crc shl 1) and 0xFFFF
            if (msb != bit) crc = crc xor CRC16_POLYNOMIAL
        }
        return crc and 0xFFFF
    }

    private fun writeInt(bits: BooleanArray, offset: Int, length: Int, value: Int) {
        for (i in 0 until length) {
            bits[offset + i] = (value ushr (length - 1 - i)) and 1 == 1
        }
    }

    private fun readInt(bits: BooleanArray, offset: Int, length: Int): Int {
        var value = 0
        for (i in 0 until length) {
            value = (value shl 1) or if (bits[offset + i]) 1 else 0
        }
        return value
    }
}
