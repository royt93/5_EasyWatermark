package com.mckimquyen.watermark.export.stego

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * IDEA-02: [Dct8x8] là nền cho toàn bộ watermark ẩn — sai ở đây thì mọi thứ phía trên đều vô nghĩa.
 * Test bằng round-trip số học thuần, không cần bitmap/Android.
 */
class Dct8x8Test {

    private fun maxAbsDiff(a: DoubleArray, b: DoubleArray): Double =
        a.indices.maxOf { abs(a[it] - b[it]) }

    @Test
    fun `roi rac deu nhau thi chi con he so DC`() {
        val block = DoubleArray(64) { 100.0 }
        val coeffs = block.copyOf()
        Dct8x8.forward(coeffs)

        assertThat(coeffs[0]).isWithin(1e-6).of(800.0) // DC = tổng theo chuẩn hoá của DCT-II 8 điểm.
        for (i in 1 until 64) {
            assertThat(coeffs[i]).isWithin(1e-6).of(0.0)
        }
    }

    @Test
    fun `forward roi inverse tra lai dung khoi ban dau`() {
        val random = Random(42)
        val block = DoubleArray(64) { random.nextDouble(0.0, 255.0) }
        val coeffs = block.copyOf()

        Dct8x8.forward(coeffs)
        val restored = coeffs.copyOf()
        Dct8x8.inverse(restored)

        assertThat(maxAbsDiff(block, restored)).isLessThan(1e-6)
    }

    @Test
    fun `nang luong bao toan qua bien doi Parseval`() {
        val random = Random(7)
        val block = DoubleArray(64) { random.nextDouble(-50.0, 50.0) }
        val coeffs = block.copyOf()
        Dct8x8.forward(coeffs)

        val energyBefore = block.sumOf { it * it }
        val energyAfter = coeffs.sumOf { it * it }
        assertThat(energyAfter).isWithin(1e-6).of(energyBefore)
    }

    @Test
    fun `sua nhe he so tan so giua roi bien doi nguoc chi lam anh lech nho`() {
        // Đây chính là cơ chế nhúng bit: sửa một hệ số tần số giữa (không phải DC — mắt nhạy với DC),
        // biến đổi ngược phải cho ra khối gần giống hệt, sai khác lan đều trên cả 64 pixel chứ không
        // dồn vào 1 điểm như sửa thẳng pixel (LSB).
        val random = Random(3)
        val block = DoubleArray(64) { random.nextDouble(0.0, 255.0) }
        val coeffs = block.copyOf()
        Dct8x8.forward(coeffs)

        val midFreqIndex = 3 * 8 + 4
        coeffs[midFreqIndex] += 20.0
        Dct8x8.inverse(coeffs)

        val diff = block.indices.map { abs(block[it] - coeffs[it]) }
        assertThat(diff.max()).isLessThan(10.0) // Không có pixel nào lệch nhiều.
        assertThat(diff.average()).isGreaterThan(0.1) // Nhưng lệch có lan ra, không phải bằng 0 hết.
    }

    @Test
    fun `khoi toan so 0 tra ve toan so 0`() {
        val block = DoubleArray(64)
        val coeffs = block.copyOf()
        Dct8x8.forward(coeffs)
        assertThat(coeffs.all { abs(it) < 1e-9 }).isTrue()
    }
}
