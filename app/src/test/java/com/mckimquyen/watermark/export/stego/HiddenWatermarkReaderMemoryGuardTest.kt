package com.mckimquyen.watermark.export.stego

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** BUG-85: quyết định có decode full-res hay không phải thuần hàm của kích thước ảnh và heap. */
class HiddenWatermarkReaderMemoryGuardTest {

    private fun mp(megapixels: Int): Pair<Int, Int> = (megapixels * PIXELS_PER_MEGAPIXEL / SIDE_DIVISOR) to SIDE_DIVISOR

    @Test
    fun smallPhoto_fitsOnTypicalHeap() {
        val (w, h) = mp(12)

        assertThat(HiddenWatermarkReader.fitsInMemory(w, h, HEAP_256_MB)).isTrue()
    }

    @Test
    fun fortyEightMegapixel_doesNotFitOn256MbHeap() {
        val (w, h) = mp(48)

        assertThat(HiddenWatermarkReader.fitsInMemory(w, h, HEAP_256_MB)).isFalse()
    }

    @Test
    fun hundredEightMegapixel_doesNotFitOn512MbHeap() {
        val (w, h) = mp(108)

        assertThat(HiddenWatermarkReader.fitsInMemory(w, h, HEAP_512_MB)).isFalse()
    }

    @Test
    fun sameImage_fitsOnlyWhenHeapIsLargeEnough() {
        val (w, h) = mp(48)

        assertThat(HiddenWatermarkReader.fitsInMemory(w, h, HEAP_256_MB)).isFalse()
        assertThat(HiddenWatermarkReader.fitsInMemory(w, h, HEAP_2_GB)).isTrue()
    }

    @Test
    fun nonPositiveDimensions_neverFit() {
        assertThat(HiddenWatermarkReader.fitsInMemory(0, 100, HEAP_512_MB)).isFalse()
        assertThat(HiddenWatermarkReader.fitsInMemory(100, -1, HEAP_512_MB)).isFalse()
    }

    @Test
    fun pixelCountOverflowingInt_isStillRejectedNotWrapped() {
        // 65536 x 65536 = 2^32 pixel: phép nhân Int sẽ tràn về 0 và lọt qua guard nếu không dùng Long.
        assertThat(HiddenWatermarkReader.fitsInMemory(OVERFLOW_SIDE, OVERFLOW_SIDE, HEAP_512_MB)).isFalse()
    }

    private companion object {
        const val PIXELS_PER_MEGAPIXEL = 1_000_000
        const val SIDE_DIVISOR = 4_000
        const val HEAP_256_MB = 256L * 1024 * 1024
        const val HEAP_512_MB = 512L * 1024 * 1024
        const val HEAP_2_GB = 2048L * 1024 * 1024
        const val OVERFLOW_SIDE = 65_536
    }
}
