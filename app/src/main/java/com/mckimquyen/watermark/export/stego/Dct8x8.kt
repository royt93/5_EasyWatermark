package com.mckimquyen.watermark.export.stego

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * IDEA-02: DCT-II / IDCT-III hai chiều trên khối 8x8 — nền cho watermark ẩn.
 *
 * Vì sao 8x8 và vì sao DCT: JPEG nén bằng đúng phép biến đổi này trên đúng lưới 8x8. Nhúng tin vào
 * miền DCT theo cùng lưới nghĩa là tin nằm sẵn ở "hệ quy chiếu" mà bộ nén JPEG làm việc, nên nó sống
 * sót qua nén thay vì bị lượng tử hoá xoá mất như khi sửa thẳng pixel (LSB).
 *
 * Tự viết thay vì kéo thư viện: repo chưa có JTransforms/OpenCV, mà DCT 8 điểm chỉ cần một bảng
 * cosine 8x8 dựng sẵn. Tách 2D thành 1D theo hàng rồi theo cột ([SIZE] * [SIZE] * 2 phép nhân mỗi
 * khối) — đủ nhanh cho ảnh vài nghìn pixel và không thêm phụ thuộc nào.
 *
 * Thuần JVM, không chạm Android → unit test chạy thẳng.
 */
object Dct8x8 {

    const val SIZE = 8
    private const val BLOCK_LEN = SIZE * SIZE

    /**
     * `cosTable[x][u] = cos((2x+1)·u·π/16)`, nhân sẵn hệ số chuẩn hoá `C(u)·sqrt(2/N)`.
     * Dựng một lần lúc nạp lớp: mỗi khối 8x8 tra bảng thay vì gọi [cos] 1024 lần.
     */
    private val cosTable: Array<DoubleArray> = Array(SIZE) { x ->
        DoubleArray(SIZE) { u ->
            val normalize = if (u == 0) sqrt(1.0 / SIZE) else sqrt(2.0 / SIZE)
            normalize * cos((2 * x + 1) * u * PI / (2 * SIZE))
        }
    }

    /**
     * DCT thuận: [block] (64 mẫu, hàng-trước) → hệ số, ghi vào [out].
     * [block] và [out] được phép là cùng một mảng.
     */
    fun forward(block: DoubleArray, out: DoubleArray = block) {
        require(block.size >= BLOCK_LEN) { "block phải có ít nhất $BLOCK_LEN phần tử" }
        require(out.size >= BLOCK_LEN) { "out phải có ít nhất $BLOCK_LEN phần tử" }

        val temp = DoubleArray(BLOCK_LEN)
        // Theo hàng.
        for (row in 0 until SIZE) {
            val base = row * SIZE
            for (u in 0 until SIZE) {
                var sum = 0.0
                for (x in 0 until SIZE) {
                    sum += block[base + x] * cosTable[x][u]
                }
                temp[base + u] = sum
            }
        }
        // Theo cột.
        for (col in 0 until SIZE) {
            for (v in 0 until SIZE) {
                var sum = 0.0
                for (y in 0 until SIZE) {
                    sum += temp[y * SIZE + col] * cosTable[y][v]
                }
                out[v * SIZE + col] = sum
            }
        }
    }

    /** DCT nghịch: [coeffs] → mẫu, ghi vào [out]. Cho phép [coeffs] === [out]. */
    fun inverse(coeffs: DoubleArray, out: DoubleArray = coeffs) {
        require(coeffs.size >= BLOCK_LEN) { "coeffs phải có ít nhất $BLOCK_LEN phần tử" }
        require(out.size >= BLOCK_LEN) { "out phải có ít nhất $BLOCK_LEN phần tử" }

        val temp = DoubleArray(BLOCK_LEN)
        // Theo cột.
        for (col in 0 until SIZE) {
            for (y in 0 until SIZE) {
                var sum = 0.0
                for (v in 0 until SIZE) {
                    sum += coeffs[v * SIZE + col] * cosTable[y][v]
                }
                temp[y * SIZE + col] = sum
            }
        }
        // Theo hàng.
        for (row in 0 until SIZE) {
            val base = row * SIZE
            for (x in 0 until SIZE) {
                var sum = 0.0
                for (u in 0 until SIZE) {
                    sum += temp[base + u] * cosTable[x][u]
                }
                out[base + x] = sum
            }
        }
    }
}
