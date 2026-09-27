package com.mckimquyen.watermark.export.stego

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * IDEA-02: [StegoPayload] là lớp chặn "đọc ra nhiễu rồi tưởng là watermark". Test tập trung vào đúng
 * tính chất đó chứ không chỉ round-trip cho đẹp.
 */
class StegoPayloadTest {

    @Test
    fun `encode roi decode tra lai dung owner id`() {
        val id = StegoPayload.ownerIdOf("Roy Studio")
        val decoded = StegoPayload.decode(StegoPayload.encode(id))

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.ownerId).isEqualTo(id)
    }

    @Test
    fun `payload dung dung 64 bit`() {
        assertThat(StegoPayload.encode(12345).size).isEqualTo(StegoPayload.TOTAL_BITS)
        assertThat(StegoPayload.TOTAL_BITS).isEqualTo(64)
    }

    @Test
    fun `bit ngau nhien gan nhu khong bao gio qua duoc MAGIC va CRC`() {
        // Đây là tính chất quan trọng nhất của file này: ảnh người lạ không được sinh ra một chủ sở hữu.
        val random = Random(2026)
        var falsePositives = 0
        val trials = 200_000
        repeat(trials) {
            val noise = BooleanArray(StegoPayload.TOTAL_BITS) { random.nextBoolean() }
            if (StegoPayload.decode(noise) != null) falsePositives++
        }
        println("[IDEA-02] Nhiễu qua được MAGIC+CRC: $falsePositives/$trials")
        assertThat(falsePositives).isEqualTo(0)
    }

    @Test
    fun `lat mot bit bat ky deu bi phat hien`() {
        val bits = StegoPayload.encode(StegoPayload.ownerIdOf("Roy"))
        for (i in bits.indices) {
            val corrupted = bits.copyOf().also { it[i] = !it[i] }
            assertThat(StegoPayload.decode(corrupted)).isNull()
        }
    }

    @Test
    fun `chu so huu khac nhau cho id khac nhau`() {
        val a = StegoPayload.ownerIdOf("Roy Studio")
        val b = StegoPayload.ownerIdOf("Ai Do Khac")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `cung chu so huu luon cho cung id`() {
        // ID phải ổn định giữa các lần chạy/máy, nếu không thì verify ảnh cũ sẽ trượt.
        assertThat(StegoPayload.ownerIdOf("Roy Studio")).isEqualTo(StegoPayload.ownerIdOf("Roy Studio"))
        // Giá trị cụ thể của thuật toán FNV-1a — khoá lại để đổi thuật toán hash là thấy ngay ở đây,
        // không phải chờ tới lúc ảnh cũ verify sai trên máy khác.
        assertThat(StegoPayload.ownerIdOf("Roy Studio")).isEqualTo(804_905_637)
    }

    @Test
    fun `khoang trang thua khong lam doi id`() {
        assertThat(StegoPayload.ownerIdOf("  Roy Studio  ")).isEqualTo(StegoPayload.ownerIdOf("Roy Studio"))
    }

    @Test
    fun `chu so huu rong van cho payload hop le`() {
        val decoded = StegoPayload.decode(StegoPayload.encode(StegoPayload.ownerIdOf("")))
        assertThat(decoded).isNotNull()
    }

    @Test
    fun `chu so huu tieng Viet co dau hoat dong binh thuong`() {
        val id = StegoPayload.ownerIdOf("Nhiếp ảnh gia Đặng Văn Hưng")
        val decoded = StegoPayload.decode(StegoPayload.encode(id))
        assertThat(decoded?.ownerId).isEqualTo(id)
    }

    @Test
    fun `mang sai do dai tra ve null`() {
        assertThat(StegoPayload.decode(BooleanArray(32))).isNull()
        assertThat(StegoPayload.decode(BooleanArray(128))).isNull()
        assertThat(StegoPayload.decode(BooleanArray(0))).isNull()
    }

    @Test
    fun `toan bit 0 hoac toan bit 1 deu bi tu choi`() {
        assertThat(StegoPayload.decode(BooleanArray(64) { false })).isNull()
        assertThat(StegoPayload.decode(BooleanArray(64) { true })).isNull()
    }
}
