package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-03: test phần QUYẾT ĐỊNH của [AuthenticityVerifier.evaluate] — so hash và xét thứ tự ưu tiên
 * giữa chữ ký và hash.
 *
 * Robolectric KHÔNG mô phỏng `AndroidKeyStore` nên `AuthenticityKeyStore.verify` ở đây luôn trả
 * `false` (khoá giả không decode được). Nhờ vậy test này khoá đúng một tính chất quan trọng: **chữ ký
 * không hợp lệ thì kết quả LUÔN là "không nguyên vẹn", kể cả khi hash khớp** — nếu ai đó sau này đảo
 * điều kiện thành `intact = hashMatches` là test đỏ ngay. Phần ký/verify bằng khoá thật được phủ bởi
 * `AuthenticityStampIntegrationTest` (androidTest, chạy Keystore thật trên máy).
 */
@RunWith(RobolectricTestRunner::class)
class AuthenticityVerifierEvaluateRoboTest {

    private val hash = "a".repeat(64)

    private fun stamp(hash: String = this.hash, signature: String = "c2ln") = AuthenticityStamp.Stamp(
        hash = hash,
        timestampMs = 1_759_000_000_000L,
        owner = "Roy",
        publicKey = "cHVibGljS2V5",
        signature = signature
    )

    @Test
    fun `hash lech thi bao khong nguyen ven`() {
        val result = AuthenticityVerifier.evaluate(stamp(), currentHash = "b".repeat(64))

        assertThat(result.hashMatches).isFalse()
        assertThat(result.intact).isFalse()
    }

    @Test
    fun `hash khop nhung chu ky khong hop le van khong nguyen ven`() {
        // Chữ ký hỏng = chính con dấu bị can thiệp, hash trong đó do kẻ sửa tự điền nên vô nghĩa.
        val result = AuthenticityVerifier.evaluate(stamp(), currentHash = hash)

        assertThat(result.hashMatches).isTrue()
        assertThat(result.signatureValid).isFalse()
        assertThat(result.intact).isFalse()
    }

    @Test
    fun `chu ky rong khong bao gio hop le`() {
        val result = AuthenticityVerifier.evaluate(stamp(signature = ""), currentHash = hash)

        assertThat(result.signatureValid).isFalse()
        assertThat(result.intact).isFalse()
    }

    @Test
    fun `giu lai thong tin hien thi tu con dau`() {
        val result = AuthenticityVerifier.evaluate(stamp(), currentHash = hash)

        assertThat(result.timestampMs).isEqualTo(1_759_000_000_000L)
        assertThat(result.owner).isEqualTo("Roy")
    }

    @Test
    fun `khoa hong thi van tay rong va khong phai khoa may nay`() {
        val result = AuthenticityVerifier.evaluate(stamp(), currentHash = hash)

        assertThat(result.keyFingerprint).isEmpty()
        assertThat(result.signedByThisDevice).isFalse()
    }
}
