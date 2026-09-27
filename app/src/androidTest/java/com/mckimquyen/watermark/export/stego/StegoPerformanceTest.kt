package com.mckimquyen.watermark.export.stego

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * IDEA-02: ĐO thời gian nhúng/đọc trên thiết bị thật trước khi quyết có cần giới hạn kích thước ảnh.
 *
 * DCT thuần Kotlin không có JNI/SIMD đỡ, ảnh 4000x3000 là ~187k khối 8x8 — phải biết con số thật chứ
 * không đoán. Test này in ra ms cho từng kích thước; ngưỡng assert đặt rộng, mục đích là ĐO và chặn
 * hồi quy thảm hoạ, không phải chấm điểm máy.
 */
@RunWith(AndroidJUnit4::class)
class StegoPerformanceTest {

    private fun pixels(width: Int, height: Int): IntArray {
        val random = Random(1)
        return IntArray(width * height) {
            val v = random.nextInt(40, 220)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun measure(width: Int, height: Int): Pair<Long, Long> {
        val data = pixels(width, height)
        val bits = BooleanArray(64) { it % 3 == 0 }

        val encodeMs = measureTimeMillis {
            StegoCodec.encode(data, width, height, bits)
        }
        val decodeMs = measureTimeMillis {
            StegoCodec.decode(data, width, height, bits.size)
        }
        println("[IDEA-02 perf] ${width}x$height — nhúng ${encodeMs}ms, đọc ${decodeMs}ms")
        return encodeMs to decodeMs
    }

    @Test
    fun anh1MP() {
        val (encode, _) = measure(1000, 1000)
        assertThat(encode).isLessThan(10_000)
    }

    @Test
    fun anh4MP() {
        val (encode, _) = measure(2000, 2000)
        assertThat(encode).isLessThan(20_000)
    }

    @Test
    fun anh12MP_kichThuocAnhDienThoaiThucTe() {
        // Ảnh 4000x3000 là cỡ ảnh camera điện thoại đời mới xuất "Original" — ca xấu nhất thực tế.
        val (encode, decode) = measure(4000, 3000)
        println("[IDEA-02 perf] TỔNG 12MP: ${encode + decode}ms")
        assertThat(encode).isLessThan(60_000)
    }
}
