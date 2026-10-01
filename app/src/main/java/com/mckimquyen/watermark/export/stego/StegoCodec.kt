package com.mckimquyen.watermark.export.stego

import kotlin.math.roundToInt

/**
 * IDEA-02: nhúng/đọc chuỗi bit vào kênh sáng của ảnh qua miền DCT 8x8 — lớp watermark VÔ HÌNH.
 *
 * ## Vì sao không dùng LSB
 * Sửa bit thấp nhất của pixel là cách dễ nhất và cũng vô dụng nhất: JPEG lượng tử hoá hệ số tần số
 * cao, đúng chỗ chứa nhiễu LSB, nên chỉ cần lưu lại ảnh một lần là tin bay sạch. Ticket nêu rõ điều
 * này ở phần "Rủi ro / cân nhắc".
 *
 * ## Cơ chế đang dùng: so sánh CẶP hệ số (Zhao–Koch)
 * Mỗi khối 8x8 mang 1 bit, mã hoá bằng QUAN HỆ LỚN-BÉ giữa hai hệ số tần số giữa ([COEF_A], [COEF_B])
 * thay vì bằng giá trị tuyệt đối của một hệ số:
 * - bit 1 → ép `|A| - |B| >= STRENGTH`
 * - bit 0 → ép `|B| - |A| >= STRENGTH`
 *
 * Điểm mấu chốt: JPEG chia hai hệ số này cho hai số lượng tử GẦN BẰNG NHAU (chúng cùng vùng tần số),
 * nên phép nén kéo cả hai xuống cùng tỉ lệ và KHÔNG đảo được dấu của hiệu. Một giá trị tuyệt đối thì
 * bị lượng tử hoá làm tròn mất, còn một quan hệ so sánh thì sống.
 *
 * Chọn tần số giữa cũng là một đánh đổi có chủ đích: tần số thấp (gần DC) mắt người nhìn ra ngay,
 * tần số cao bị bộ nén xoá trước tiên. Giữa là vùng duy nhất vừa kín vừa bền.
 *
 * ## Chống mất khối
 * Payload được lặp lại nhiều vòng trên toàn ảnh rồi đọc bằng bỏ phiếu đa số ([decode]), nên mất một
 * phần ảnh (crop, vá, che) không làm mất tin miễn còn đủ số khối.
 *
 * ## Giới hạn thành thật
 * Cách này bền với NÉN JPEG lặp lại, nhưng KHÔNG tự chống RESIZE: đổi kích thước làm lệch lưới 8x8,
 * khối đọc ra không còn trùng khối lúc ghi. Muốn chống resize phải đồng bộ lại lưới (miền log-polar
 * hoặc template pattern) — ngoài phạm vi. Độ bền thật được đo bằng số liệu trong test, không hứa suông.
 *
 * Thuần JVM (nhận `IntArray` pixel ARGB) để unit test không cần Robolectric.
 */
object StegoCodec {

    /**
     * Hai hệ số tần số giữa trong khối 8x8 (chỉ số hàng-trước), chọn đối xứng qua đường chéo để có
     * mức lượng tử gần bằng nhau — điều kiện để phép so sánh công bằng sau khi nén.
     *
     * Chọn (2,3) và (3,2) chứ không phải (3,4)/(4,3): trong bảng lượng tử luma chuẩn, (2,3)=24 và
     * (3,2)=22, trong khi (3,4)=51 và (4,3)=56. Số lượng tử nhỏ hơn hơn hai lần nghĩa là hệ số bị làm
     * tròn thô hơn ÍT hơn hai lần, nên quan hệ lớn-bé giữa chúng sống sót được mức nén mạnh hơn hẳn.
     *
     * Đây là sửa từ số đo thật: với cặp (3,4)/(4,3), codec Skia ở q=75 làm mất payload dù simulator
     * JVM vẫn báo đọc được 100% — bảng lượng tử libjpeg-turbo mà Skia dùng khác bảng Annex K đủ để
     * lật kết quả. Hạ tần số bền hơn mà không phải tăng cường độ nhúng (tăng cường độ sẽ kéo PSNR
     * xuống, hỏng đúng tiêu chí "mắt thường không phân biệt được").
     */
    private const val COEF_A = 2 * Dct8x8.SIZE + 3
    private const val COEF_B = 3 * Dct8x8.SIZE + 2

    /**
     * Khoảng cách tối thiểu ép giữa hai hệ số. Lớn thì bền hơn nhưng dễ lộ vết trên ảnh phẳng; nhỏ
     * thì kín hơn nhưng nén mạnh là mất. Giá trị này chọn theo số đo trong `StegoRobustnessTest`.
     */
    private const val STRENGTH = 26.0

    private const val BLOCK_LEN = Dct8x8.SIZE * Dct8x8.SIZE

    /** Hệ số luma theo ITU-R BT.601 — cùng công thức JPEG dùng khi tách kênh Y. */
    private const val LUMA_R = 0.299
    private const val LUMA_G = 0.587
    private const val LUMA_B = 0.114

    private const val MIN_VALUE = 0
    private const val MAX_VALUE = 255

    /** Số khối tối thiểu để một lần đọc có nghĩa (payload ít nhất phải xuất hiện trọn 1 vòng). */
    const val MIN_BLOCKS_PER_ROUND = 1

    /** Số khối 8x8 mà ảnh [width]x[height] chứa được — cũng là số bit nhúng được tối đa. */
    fun capacityBits(width: Int, height: Int): Int =
        (width / Dct8x8.SIZE) * (height / Dct8x8.SIZE)

    /**
     * Nhúng [bits] vào [pixels] (ARGB, kích thước [width]x[height]), sửa TẠI CHỖ.
     *
     * Payload lặp lại tới khi hết khối. Trả `false` nếu ảnh nhỏ hơn một vòng payload — khi đó không
     * sửa gì cả, thà không nhúng còn hơn nhúng một nửa rồi đọc ra rác.
     */
    fun encode(pixels: IntArray, width: Int, height: Int, bits: BooleanArray): Boolean {
        if (bits.isEmpty()) return false
        val blocksX = width / Dct8x8.SIZE
        val blocksY = height / Dct8x8.SIZE
        if (blocksX * blocksY < bits.size) return false

        val block = DoubleArray(BLOCK_LEN)
        var bitIndex = 0
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                readLumaBlock(pixels, width, bx, by, block)
                Dct8x8.forward(block)
                applyBit(block, bits[bitIndex % bits.size])
                Dct8x8.inverse(block)
                writeLumaBlock(pixels, width, bx, by, block)
                bitIndex++
            }
        }
        return true
    }

    /**
     * Đọc lại [bitCount] bit từ [pixels] bằng bỏ phiếu đa số trên mọi vòng lặp có trong ảnh.
     *
     * Trả về `null` nếu ảnh không đủ một vòng. Kèm theo mỗi bit là độ tin cậy ([Decoded.confidence]):
     * tỉ lệ phiếu thuận trung bình — gần 0.5 nghĩa là đọc được toàn nhiễu, tin không còn ở đó.
     *
     * ENH-38: công thức này CHỈ có ý nghĩa phân biệt nhiễu/tín hiệu khi [Decoded.rounds] >= 2. Ở
     * ảnh vừa đúng 1 vòng payload (vd 64x64px với `TOTAL_BITS=64`), mỗi bit chỉ có 1 phiếu nên phe
     * "thắng" luôn thắng tuyệt đối — `confidence` LUÔN = 1.0 dù ảnh sạch hoàn toàn không có
     * watermark. Tuyến phòng thủ thật ở `rounds=1` là MAGIC+CRC trong [StegoPayload], không phải
     * ngưỡng [StegoPayload.MIN_CONFIDENCE].
     */
    fun decode(pixels: IntArray, width: Int, height: Int, bitCount: Int): Decoded? {
        if (bitCount <= 0) return null
        val blocksX = width / Dct8x8.SIZE
        val blocksY = height / Dct8x8.SIZE
        if (blocksX * blocksY < bitCount) return null

        val votesOne = IntArray(bitCount)
        val votesTotal = IntArray(bitCount)
        val block = DoubleArray(BLOCK_LEN)

        var bitIndex = 0
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                readLumaBlock(pixels, width, bx, by, block)
                Dct8x8.forward(block)
                val slot = bitIndex % bitCount
                if (readBit(block)) votesOne[slot]++
                votesTotal[slot]++
                bitIndex++
            }
        }

        val bits = BooleanArray(bitCount) { votesOne[it] * 2 > votesTotal[it] }
        // Tỉ lệ phiếu theo phe THẮNG, trung bình trên mọi bit: 1.0 = mọi vòng nhất trí, 0.5 = nhiễu.
        val agreement = bits.indices.map { i ->
            val win = if (bits[i]) votesOne[i] else votesTotal[i] - votesOne[i]
            win.toDouble() / votesTotal[i]
        }.average()

        return Decoded(bits = bits, confidence = agreement, rounds = votesTotal.min())
    }

    data class Decoded(
        val bits: BooleanArray,
        /** Trung bình tỉ lệ phiếu của phe thắng: 1.0 nhất trí tuyệt đối, ~0.5 là nhiễu thuần. */
        val confidence: Double,
        /** Số vòng payload đọc được — càng nhiều thì bỏ phiếu càng đáng tin. */
        val rounds: Int
    ) {
        // BooleanArray dùng so sánh tham chiếu, phải tự viết equals/hashCode cho đúng nghĩa giá trị.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return bits.contentEquals(other.bits) && confidence == other.confidence && rounds == other.rounds
        }

        override fun hashCode(): Int =
            31 * (31 * bits.contentHashCode() + confidence.hashCode()) + rounds
    }

    /** Ép quan hệ lớn-bé giữa hai hệ số sao cho nó mã hoá đúng [bit]. */
    private fun applyBit(coeffs: DoubleArray, bit: Boolean) {
        val a = coeffs[COEF_A]
        val b = coeffs[COEF_B]
        // Làm việc trên độ lớn rồi trả lại dấu cũ: dấu hệ số DCT dễ bị lật khi làm tròn, độ lớn thì không.
        val magA = kotlin.math.abs(a)
        val magB = kotlin.math.abs(b)
        val signA = if (a < 0) -1.0 else 1.0
        val signB = if (b < 0) -1.0 else 1.0

        val mid = (magA + magB) / 2
        val half = STRENGTH / 2
        val (newMagA, newMagB) = if (bit) {
            (mid + half) to (mid - half).coerceAtLeast(0.0)
        } else {
            (mid - half).coerceAtLeast(0.0) to (mid + half)
        }

        coeffs[COEF_A] = signA * newMagA
        coeffs[COEF_B] = signB * newMagB
    }

    private fun readBit(coeffs: DoubleArray): Boolean =
        kotlin.math.abs(coeffs[COEF_A]) > kotlin.math.abs(coeffs[COEF_B])

    /** Lấy kênh sáng của khối (bx,by) ra [out], dịch về khoảng [-128,127] như JPEG làm. */
    private fun readLumaBlock(pixels: IntArray, width: Int, bx: Int, by: Int, out: DoubleArray) {
        val originX = bx * Dct8x8.SIZE
        val originY = by * Dct8x8.SIZE
        for (y in 0 until Dct8x8.SIZE) {
            val rowOffset = (originY + y) * width + originX
            for (x in 0 until Dct8x8.SIZE) {
                val pixel = pixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                out[y * Dct8x8.SIZE + x] = (LUMA_R * r + LUMA_G * g + LUMA_B * b) - 128.0
            }
        }
    }

    /**
     * Ghi khối sáng đã sửa trở lại [pixels], GIỮ NGUYÊN màu: chỉ cộng phần chênh lệch độ sáng vào cả
     * 3 kênh. Cách này tránh phải chuyển qua lại RGB↔YCbCr (mỗi lần chuyển là một lần làm tròn, lặp
     * nhiều khối sẽ trôi màu thấy rõ).
     */
    private fun writeLumaBlock(pixels: IntArray, width: Int, bx: Int, by: Int, block: DoubleArray) {
        val originX = bx * Dct8x8.SIZE
        val originY = by * Dct8x8.SIZE
        for (y in 0 until Dct8x8.SIZE) {
            val rowOffset = (originY + y) * width + originX
            for (x in 0 until Dct8x8.SIZE) {
                val index = rowOffset + x
                val pixel = pixels[index]
                val alpha = pixel ushr 24
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val oldLuma = LUMA_R * r + LUMA_G * g + LUMA_B * b
                val newLuma = block[y * Dct8x8.SIZE + x] + 128.0
                val delta = newLuma - oldLuma

                pixels[index] = (alpha shl 24) or
                    (clamp(r + delta) shl 16) or
                    (clamp(g + delta) shl 8) or
                    clamp(b + delta)
            }
        }
    }

    private fun clamp(value: Double): Int =
        value.roundToInt().coerceIn(MIN_VALUE, MAX_VALUE)
}
