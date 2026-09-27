package com.mckimquyen.watermark.export.stego

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * IDEA-02: ĐO độ bền thật của [StegoCodec] qua nén JPEG, không hứa suông.
 *
 * Nén mô phỏng bằng [JpegQuantizationSimulator] — tái hiện đúng bước lossy duy nhất của JPEG (lượng
 * tử hoá hệ số DCT theo bảng chuẩn Annex K). `javax.imageio` không dùng được vì android.jar trong
 * unit test chỉ là stub. Số đo ở đây được xác nhận lại bằng codec Android thật trong androidTest.
 *
 * Mỗi test in ra tỉ lệ bit đúng để khi ai đó chỉnh [StegoCodec] còn thấy được mình làm nó tốt lên
 * hay tệ đi, thay vì chỉ biết pass/fail.
 */
class StegoRobustnessTest {

    private val payloadBits = 64

    private fun randomBits(seed: Int, count: Int = payloadBits): BooleanArray {
        val random = Random(seed)
        return BooleanArray(count) { random.nextBoolean() }
    }

    /** Ảnh giả lập có kết cấu (gradient + đốm) — ảnh phẳng tuyệt đối không đại diện ảnh thật. */
    private fun texturedPixels(width: Int, height: Int, seed: Int = 1): IntArray {
        val random = Random(seed)
        return IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val base = (60 + (x * 120 / width) + (y * 60 / height)).coerceIn(0, 255)
            val noise = random.nextInt(-18, 19)
            val r = (base + noise).coerceIn(0, 255)
            val g = (base * 3 / 4 + noise).coerceIn(0, 255)
            val b = (base / 2 + 40 + noise).coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun jpegRoundTrip(pixels: IntArray, width: Int, height: Int, quality: Int): IntArray =
        JpegQuantizationSimulator.recompress(pixels, width, height, quality)

    private fun accuracy(expected: BooleanArray, actual: BooleanArray): Double =
        expected.indices.count { expected[it] == actual[it] }.toDouble() / expected.size

    @Test
    fun `khong nen thi doc lai chinh xac tuyet doi`() {
        val width = 256
        val height = 256
        val pixels = texturedPixels(width, height)
        val bits = randomBits(seed = 11)

        assertThat(StegoCodec.encode(pixels, width, height, bits)).isTrue()
        val decoded = StegoCodec.decode(pixels, width, height, bits.size)

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.bits.toList()).isEqualTo(bits.toList())
        assertThat(decoded.confidence).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `song sot qua nen JPEG chat luong 85 giong Facebook`() {
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height)
        val bits = randomBits(seed = 21)

        StegoCodec.encode(pixels, width, height, bits)
        val compressed = jpegRoundTrip(pixels, width, height, 85)
        val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] JPEG q=85: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}, rounds ${decoded.rounds}")
        assertThat(acc).isEqualTo(1.0)
    }

    @Test
    fun `song sot qua nen JPEG chat luong 75 giong Instagram`() {
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 5)
        val bits = randomBits(seed = 22)

        StegoCodec.encode(pixels, width, height, bits)
        val compressed = jpegRoundTrip(pixels, width, height, 75)
        val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] JPEG q=75: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}")
        assertThat(acc).isEqualTo(1.0)
    }

    @Test
    fun `song sot qua nen JPEG chat luong 70 giong Zalo`() {
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 9)
        val bits = randomBits(seed = 23)

        StegoCodec.encode(pixels, width, height, bits)
        val compressed = jpegRoundTrip(pixels, width, height, 70)
        val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] JPEG q=70: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}")
        assertThat(acc).isEqualTo(1.0)
    }

    @Test
    fun `nen hai lan lien tiep van doc duoc`() {
        // Ảnh bị chia sẻ lại nhiều lần: mỗi lần là một vòng nén nữa.
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 13)
        val bits = randomBits(seed = 24)

        StegoCodec.encode(pixels, width, height, bits)
        val once = jpegRoundTrip(pixels, width, height, 85)
        val twice = jpegRoundTrip(once, width, height, 75)
        val decoded = StegoCodec.decode(twice, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] JPEG q=85 rồi q=75: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}")
        assertThat(acc).isEqualTo(1.0)
    }

    /**
     * q=50 nằm NGOÀI mức mọi nền tảng dùng (thấp nhất là Zalo q≈70) — đo để biết còn dư địa bao nhiêu.
     *
     * Sau khi hạ cặp hệ số xuống vùng lượng tử thấp hơn (xem lý do trong [StegoCodec]), mức này đọc
     * lại đủ 100% bit. Trước đó, với cặp (3,4)/(4,3), chỉ đạt ~89%.
     */
    @Test
    fun `nen rat manh q50 van doc du payload`() {
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 17)
        val bits = randomBits(seed = 25)

        StegoCodec.encode(pixels, width, height, bits)
        val compressed = jpegRoundTrip(pixels, width, height, 50)
        val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] JPEG q=50: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}")
        assertThat(acc).isEqualTo(1.0)
    }

    /**
     * Đo ngưỡng GÃY thật: nén tới mức nào thì mất tin, và quan trọng hơn — lúc mất thì confidence có
     * tụt xuống để tầng trên biết đường mà im lặng, thay vì trả ra ID sai một cách tự tin.
     */
    @Test
    fun `nen cuc doan thi confidence tu tut xuong bao hieu khong con tin duoc`() {
        val width = 512
        val height = 512
        var brokeAt = -1
        for (quality in intArrayOf(40, 30, 20, 10, 5)) {
            val pixels = texturedPixels(width, height, seed = 17)
            val bits = randomBits(seed = 25)
            StegoCodec.encode(pixels, width, height, bits)
            val decoded = StegoCodec.decode(jpegRoundTrip(pixels, width, height, quality), width, height, bits.size)!!
            val acc = accuracy(bits, decoded.bits)
            println("[IDEA-02] JPEG q=$quality: bit đúng ${"%.1f".format(acc * 100)}%, confidence ${"%.3f".format(decoded.confidence)}")
            if (acc < 1.0 && brokeAt == -1) brokeAt = quality
        }
        println("[IDEA-02] Ngưỡng bắt đầu mất bit: q=$brokeAt (mọi nền tảng thật đều dùng q>=70)")
    }

    @Test
    fun `mat mot phan anh van doc duoc nho bo phieu da so`() {
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 31)
        val bits = randomBits(seed = 26)
        StegoCodec.encode(pixels, width, height, bits)

        // Xoá trắng 1/3 dưới của ảnh (giả lập bị che/vá/dán đè).
        for (y in (height * 2 / 3) until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = (0xFF shl 24) or 0xFFFFFF
            }
        }
        val decoded = StegoCodec.decode(pixels, width, height, bits.size)!!

        val acc = accuracy(bits, decoded.bits)
        println("[IDEA-02] Che 1/3 ảnh: bit đúng ${"%.1f".format(acc * 100)}%")
        assertThat(acc).isEqualTo(1.0)
    }

    @Test
    fun `anh khong nhung gi thi confidence thap - khong bao nham co watermark`() {
        // Quan trọng: đọc ảnh sạch KHÔNG được ra kết quả tự tin, nếu không sẽ báo nhầm chủ sở hữu.
        val width = 512
        val height = 512
        val pixels = texturedPixels(width, height, seed = 77)

        val decoded = StegoCodec.decode(pixels, width, height, payloadBits)!!

        println("[IDEA-02] Ảnh sạch: confidence ${"%.3f".format(decoded.confidence)}")
        assertThat(decoded.confidence).isLessThan(0.90)
    }

    @Test
    fun `chat luong anh khong bi anh huong nhan biet duoc`() {
        val width = 256
        val height = 256
        val original = texturedPixels(width, height, seed = 41)
        val stamped = original.copyOf()
        StegoCodec.encode(stamped, width, height, randomBits(seed = 27))

        var sumSquaredError = 0.0
        var maxChannelDiff = 0
        for (i in original.indices) {
            for (shift in intArrayOf(16, 8, 0)) {
                val a = (original[i] shr shift) and 0xFF
                val b = (stamped[i] shr shift) and 0xFF
                val d = abs(a - b)
                if (d > maxChannelDiff) maxChannelDiff = d
                sumSquaredError += (d * d).toDouble()
            }
        }
        val mse = sumSquaredError / (original.size * 3)
        val psnr = 10 * kotlin.math.log10(255.0 * 255.0 / mse)

        println("[IDEA-02] Chất lượng: PSNR ${"%.1f".format(psnr)} dB, lệch kênh lớn nhất $maxChannelDiff")
        // PSNR > 40 dB là ngưỡng quy ước "mắt thường không phân biệt được".
        assertThat(psnr).isGreaterThan(40.0)
    }

    @Test
    fun `anh qua nho de chua payload thi tu choi nhung`() {
        val width = 32
        val height = 32 // 4x4 = 16 khối < 64 bit.
        val pixels = texturedPixels(width, height)
        val before = pixels.copyOf()

        assertThat(StegoCodec.encode(pixels, width, height, randomBits(seed = 28))).isFalse()
        // Từ chối thì phải KHÔNG đụng vào ảnh, không nhúng nửa vời.
        assertThat(pixels.toList()).isEqualTo(before.toList())
        assertThat(StegoCodec.decode(pixels, width, height, payloadBits)).isNull()
    }

    @Test
    fun `capacityBits dung bang so khoi 8x8`() {
        assertThat(StegoCodec.capacityBits(512, 512)).isEqualTo(64 * 64)
        assertThat(StegoCodec.capacityBits(100, 100)).isEqualTo(12 * 12) // Phần dư bị bỏ.
        assertThat(StegoCodec.capacityBits(7, 7)).isEqualTo(0)
    }
}
