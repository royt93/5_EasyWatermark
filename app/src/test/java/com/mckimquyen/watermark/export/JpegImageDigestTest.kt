package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * IDEA-03: unit test THUẦN (không Robolectric) — [JpegImageDigest] chỉ đọc byte nên chạy thẳng JVM.
 *
 * Test quan trọng nhất là [hashGiuNguyenKhiApp1DoiDoDai]: đó chính là lý do tồn tại của cả file
 * `JpegImageDigest` — nếu hash đổi theo APP1 thì việc ghi EXIF sẽ tự phủ định con dấu vừa ghi.
 */
class JpegImageDigestTest {

    private fun hashOf(bytes: ByteArray): String? =
        ByteArrayInputStream(bytes).use { JpegImageDigest.hashImageData(it) }

    /** Dựng JPEG tối thiểu: SOI + (APP1 tuỳ chọn) + SOF0 + SOS + entropy + EOI. */
    private fun buildJpeg(app1Payload: ByteArray? = null, entropy: ByteArray = byteArrayOf(1, 2, 3, 4)): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF); out.write(0xD8) // SOI

        if (app1Payload != null) {
            out.write(0xFF); out.write(0xE1) // APP1
            val length = app1Payload.size + 2
            out.write(length shr 8 and 0xFF); out.write(length and 0xFF)
            out.write(app1Payload)
        }

        // SOF0 với payload giả — segment KHÔNG phải metadata nên phải nằm trong hash.
        val sof = byteArrayOf(8, 0, 16, 0, 16, 1)
        out.write(0xFF); out.write(0xC0)
        out.write((sof.size + 2) shr 8 and 0xFF); out.write((sof.size + 2) and 0xFF)
        out.write(sof)

        // SOS + entropy data.
        val sos = byteArrayOf(1, 1, 0, 0, 63, 0)
        out.write(0xFF); out.write(0xDA)
        out.write((sos.size + 2) shr 8 and 0xFF); out.write((sos.size + 2) and 0xFF)
        out.write(sos)
        out.write(entropy)

        out.write(0xFF); out.write(0xD9) // EOI
        return out.toByteArray()
    }

    @Test
    fun `jpeg hop le tra ve hash 64 ky tu hex`() {
        val hash = hashOf(buildJpeg())
        assertThat(hash).isNotNull()
        assertThat(hash!!).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun hashGiuNguyenKhiApp1DoiDoDai() {
        // Đây là mục tiêu thiết kế: ghi EXIF (= thay APP1) KHÔNG được làm đổi hash.
        val khongCoApp1 = hashOf(buildJpeg(app1Payload = null))
        val app1Ngan = hashOf(buildJpeg(app1Payload = ByteArray(16) { 0x41 }))
        val app1Dai = hashOf(buildJpeg(app1Payload = ByteArray(5000) { 0x42 }))

        assertThat(khongCoApp1).isNotNull()
        assertThat(app1Ngan).isEqualTo(khongCoApp1)
        assertThat(app1Dai).isEqualTo(khongCoApp1)
    }

    @Test
    fun `hash doi khi du lieu anh that doi`() {
        val goc = hashOf(buildJpeg(entropy = byteArrayOf(1, 2, 3, 4)))
        val daSua = hashOf(buildJpeg(entropy = byteArrayOf(1, 2, 3, 5)))

        assertThat(goc).isNotNull()
        assertThat(daSua).isNotEqualTo(goc)
    }

    @Test
    fun `segment COM cung bi bo qua khoi hash`() {
        val coCom = ByteArrayOutputStream().apply {
            write(0xFF); write(0xD8)
            val comment = "ghi chu bat ky".toByteArray()
            write(0xFF); write(0xFE)
            write((comment.size + 2) shr 8 and 0xFF); write((comment.size + 2) and 0xFF)
            write(comment)
            write(buildJpeg().copyOfRange(2, buildJpeg().size))
        }.toByteArray()

        assertThat(hashOf(coCom)).isEqualTo(hashOf(buildJpeg()))
    }

    @Test
    fun `khong phai jpeg tra ve null`() {
        assertThat(hashOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))).isNull() // PNG
        assertThat(hashOf(byteArrayOf())).isNull()
        assertThat(hashOf("khong phai anh".toByteArray())).isNull()
    }

    @Test
    fun `file cut giua chung tra ve null chu khong treo`() {
        val full = buildJpeg(app1Payload = ByteArray(200) { 0x43 })

        // Cụt ngay sau SOI.
        assertThat(hashOf(full.copyOfRange(0, 2))).isNull()
        // Cụt giữa phần khai báo độ dài APP1.
        assertThat(hashOf(full.copyOfRange(0, 5))).isNull()
        // Cụt giữa payload APP1 (độ dài khai 202 nhưng không đủ byte).
        assertThat(hashOf(full.copyOfRange(0, 50))).isNull()
    }

    @Test
    fun `do dai segment vo ly tra ve null`() {
        // Khai length = 1 (nhỏ hơn cả 2 byte của chính trường length).
        val hong = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xC0.toByte(),
            0x00,
            0x01
        )
        assertThat(hashOf(hong)).isNull()
    }

    @Test
    fun `cung mot anh hash hai lan cho ket qua giong nhau`() {
        val jpeg = buildJpeg(app1Payload = ByteArray(64) { 0x44 })
        assertThat(hashOf(jpeg)).isEqualTo(hashOf(jpeg))
    }
}
