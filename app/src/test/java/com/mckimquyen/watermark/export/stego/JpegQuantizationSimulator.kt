package com.mckimquyen.watermark.export.stego

import kotlin.math.roundToInt

/**
 * IDEA-02 (test-only): mô phỏng đúng bước LOSSY của nén JPEG để đo độ bền watermark ẩn trong unit
 * test thuần JVM.
 *
 * Vì sao không dùng codec thật ở đây: `javax.imageio` không có trong classpath unit test của Android
 * (android.jar là stub), còn `Bitmap.compress` thì cần thiết bị. Nhưng toàn bộ mất mát của JPEG nằm
 * ở đúng một bước: chia hệ số DCT cho bảng lượng tử rồi LÀM TRÒN — mọi thứ còn lại (Huffman, chuỗi
 * zigzag) đều không mất dữ liệu. Mô phỏng đúng bước đó cho kết quả phản ánh trung thực bản chất, và
 * số đo được xác nhận lại bằng codec Android thật trong `StegoCodecIntegrationTest` (androidTest).
 *
 * Bảng lượng tử là bảng luma chuẩn trong Annex K của đặc tả JPEG — chính bảng mà libjpeg/Skia dùng
 * làm gốc rồi co giãn theo mức chất lượng.
 */
object JpegQuantizationSimulator {

    private val LUMA_QUANT_TABLE = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    )

    private const val MIN_QUALITY = 1
    private const val MAX_QUALITY = 99
    private const val QUALITY_PIVOT = 50

    /** Co giãn bảng lượng tử theo [quality], đúng công thức libjpeg dùng. */
    private fun scaledTable(quality: Int): IntArray {
        val q = quality.coerceIn(MIN_QUALITY, MAX_QUALITY)
        val scale = if (q < QUALITY_PIVOT) 5000 / q else 200 - q * 2
        return IntArray(LUMA_QUANT_TABLE.size) {
            ((LUMA_QUANT_TABLE[it] * scale + 50) / 100).coerceIn(1, 255)
        }
    }

    /**
     * Áp một vòng nén-giải nén JPEG ở mức [quality] lên [pixels] (ARGB, [width]x[height]), trả mảng
     * mới. Ảnh được xử lý theo đúng lưới 8x8 mà JPEG dùng.
     */
    fun recompress(pixels: IntArray, width: Int, height: Int, quality: Int): IntArray {
        val table = scaledTable(quality)
        val out = pixels.copyOf()
        val block = DoubleArray(64)

        val blocksX = width / 8
        val blocksY = height / 8
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                readLuma(out, width, bx, by, block)
                Dct8x8.forward(block)
                // Đây chính là chỗ JPEG mất dữ liệu: chia, làm tròn, nhân lại.
                for (i in block.indices) {
                    val q = table[i].toDouble()
                    block[i] = (block[i] / q).roundToInt() * q
                }
                Dct8x8.inverse(block)
                writeLuma(out, width, bx, by, block)
            }
        }
        return out
    }

    private fun readLuma(pixels: IntArray, width: Int, bx: Int, by: Int, out: DoubleArray) {
        for (y in 0 until 8) {
            val row = (by * 8 + y) * width + bx * 8
            for (x in 0 until 8) {
                val p = pixels[row + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[y * 8 + x] = (0.299 * r + 0.587 * g + 0.114 * b) - 128.0
            }
        }
    }

    private fun writeLuma(pixels: IntArray, width: Int, bx: Int, by: Int, block: DoubleArray) {
        for (y in 0 until 8) {
            val row = (by * 8 + y) * width + bx * 8
            for (x in 0 until 8) {
                val index = row + x
                val p = pixels[index]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val oldLuma = 0.299 * r + 0.587 * g + 0.114 * b
                val delta = (block[y * 8 + x] + 128.0) - oldLuma
                pixels[index] = (0xFF shl 24) or
                    (clamp(r + delta) shl 16) or
                    (clamp(g + delta) shl 8) or
                    clamp(b + delta)
            }
        }
    }

    private fun clamp(v: Double): Int = v.roundToInt().coerceIn(0, 255)
}
