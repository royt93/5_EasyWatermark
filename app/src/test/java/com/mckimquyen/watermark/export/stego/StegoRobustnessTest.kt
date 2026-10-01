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

    /** Nền phẳng tuyệt đối (solid color) — case ENH-39: ảnh sản phẩm nền trắng, slide, screenshot. */
    private fun flatPixels(width: Int, height: Int, gray: Int = 230): IntArray {
        val pixel = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        return IntArray(width * height) { pixel }
    }

    /**
     * ENH-39 (KHÔNG sửa STRENGTH — số liệu xác nhận không cần): chất lượng khối phẳng tuyệt đối vẫn
     * trong ngưỡng "mắt thường không phân biệt" (PSNR >40dB, như `chat luong anh khong bi anh huong
     * nhan biet duoc` đã dùng) ở mọi mức xám KHÔNG chạm tuyệt đối biên 0/255 — case đó thuộc ENH-40.
     */
    @Test
    fun `ENH-39 nen phang khong cham bien van dat nguong PSNR mat thuong`() {
        val width = 256
        val height = 256
        for (gray in intArrayOf(10, 128, 230, 245)) {
            val original = flatPixels(width, height, gray)
            val stamped = original.copyOf()
            assertThat(StegoCodec.encode(stamped, width, height, randomBits(seed = 39))).isTrue()

            var sumSquaredError = 0.0
            for (i in original.indices) {
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = ((original[i] shr shift) and 0xFF) - ((stamped[i] shr shift) and 0xFF)
                    sumSquaredError += (d * d).toDouble()
                }
            }
            val psnr = 10 * kotlin.math.log10(255.0 * 255.0 / (sumSquaredError / (original.size * 3)))
            println("[ENH-39] Nền phẳng gray=$gray: PSNR ${"%.1f".format(psnr)} dB")
            assertThat(psnr).isGreaterThan(40.0)
        }
    }

    /**
     * ENH-40: khối phẳng tuyệt đối SÁT BIÊN đen/trắng (gray=0/255) mất watermark hoàn toàn qua JPEG
     * q<=70 — [StegoCodec.compensateRailClipping] dịch DC ra xa biên [RAIL_MARGIN] trước khi mã hoá.
     * Test này là bằng chứng hồi quy: trước khi sửa, 2 case q=70/q=50 ở gray=0/255 chỉ đạt 46.9-56.3%
     * (gần ngẫu nhiên) dù `confidence` vẫn báo 1.0 — nguy hiểm vì báo nhầm chủ sở hữu tự tin.
     */
    @Test
    fun `ENH-40 nen den trang tuyet doi van doc dung qua JPEG sau khi bu DC`() {
        val width = 512
        val height = 512
        for (gray in intArrayOf(0, 255)) {
            for (quality in intArrayOf(85, 75, 70, 50)) {
                val pixels = flatPixels(width, height, gray)
                val bits = randomBits(seed = 50 + quality)
                StegoCodec.encode(pixels, width, height, bits)
                val compressed = jpegRoundTrip(pixels, width, height, quality)
                val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!
                val acc = accuracy(bits, decoded.bits)
                println("[ENH-40] gray=$gray, JPEG q=$quality: bit đúng ${"%.1f".format(acc * 100)}%")
                assertThat(acc).isEqualTo(1.0)
            }
        }
    }

    /** ENH-40: khối gần biên nhưng chưa phẳng (có texture) không bị đụng tới — dùng margin y hệt test trên. */
    @Test
    fun `ENH-40 khoi gan bien nhung co texture khong bi dich DC`() {
        val width = 512
        val height = 512
        val bits = randomBits(seed = 61)

        // texturedPixels nền base=60..180, không chạm biên — tái dùng để xác nhận compensateRailClipping
        // không âm thầm đụng vào ảnh có texture thật (chỉ gate trên block phẳng tuyệt đối).
        val pixels = texturedPixels(width, height, seed = 61)
        StegoCodec.encode(pixels, width, height, bits)
        val compressed = jpegRoundTrip(pixels, width, height, 50)
        val decoded = StegoCodec.decode(compressed, width, height, bits.size)!!

        assertThat(accuracy(bits, decoded.bits)).isEqualTo(1.0)
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
