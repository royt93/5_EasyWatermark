package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-03: test định dạng con dấu chứng thực.
 *
 * Chạy Robolectric vì [AuthenticityStamp] dùng `android.util.Base64` — `java.util.Base64` chỉ có từ
 * API 26 trong khi app đỡ tới minSdk 24.
 */
@RunWith(RobolectricTestRunner::class)
class AuthenticityStampTest {

    private fun stamp(
        hash: String = "a".repeat(64),
        timestampMs: Long = 1_759_000_000_000L,
        owner: String = "Roy",
        publicKey: String = "cHVibGljS2V5",
        signature: String = "c2lnbmF0dXJl"
    ) = AuthenticityStamp.Stamp(hash, timestampMs, owner, publicKey, signature)

    @Test
    fun `format roi parse tra lai dung con dau ban dau`() {
        val goc = stamp()
        val parsed = AuthenticityStamp.parse(AuthenticityStamp.format(goc))
        assertThat(parsed).isEqualTo(goc)
    }

    @Test
    fun `owner chua ky tu phan tach van round trip dung`() {
        // Đây là lý do owner phải encode base64: '|' cũng là ký tự ngăn trường.
        val goc = stamp(owner = "Roy | Studio | 2026")
        val parsed = AuthenticityStamp.parse(AuthenticityStamp.format(goc))
        assertThat(parsed?.owner).isEqualTo("Roy | Studio | 2026")
    }

    @Test
    fun `owner tieng viet co dau van round trip dung`() {
        val goc = stamp(owner = "Nhiếp ảnh gia Đặng Văn Hưng")
        val parsed = AuthenticityStamp.parse(AuthenticityStamp.format(goc))
        assertThat(parsed?.owner).isEqualTo("Nhiếp ảnh gia Đặng Văn Hưng")
    }

    @Test
    fun `owner rong van hop le`() {
        val parsed = AuthenticityStamp.parse(AuthenticityStamp.format(stamp(owner = "")))
        assertThat(parsed).isNotNull()
        assertThat(parsed?.owner).isEmpty()
    }

    @Test
    fun `chu ky rong van parse duoc de con giu phan hash`() {
        val parsed = AuthenticityStamp.parse(AuthenticityStamp.format(stamp(signature = "")))
        assertThat(parsed).isNotNull()
        assertThat(parsed?.signature).isEmpty()
        assertThat(parsed?.hash).isEqualTo("a".repeat(64))
    }

    @Test
    fun `sai phien ban tra ve null`() {
        val raw = AuthenticityStamp.format(stamp()).replaceFirst(AuthenticityStamp.VERSION, "EWM9")
        assertThat(AuthenticityStamp.parse(raw)).isNull()
    }

    @Test
    fun `thieu truong tra ve null`() {
        assertThat(AuthenticityStamp.parse("EWM1|abc|123")).isNull()
    }

    @Test
    fun `timestamp khong phai so tra ve null`() {
        assertThat(AuthenticityStamp.parse("EWM1|abc|khongphaiso|Um95|fp|sig")).isNull()
    }

    @Test
    fun `chuoi rac hoac rong tra ve null`() {
        assertThat(AuthenticityStamp.parse(null)).isNull()
        assertThat(AuthenticityStamp.parse("")).isNull()
        assertThat(AuthenticityStamp.parse("   ")).isNull()
        assertThat(AuthenticityStamp.parse("chu thich binh thuong cua nguoi dung")).isNull()
    }

    @Test
    fun `signedPayload khong chua chu ky`() {
        val goc = stamp(signature = "chukyratdai")
        val payload = String(AuthenticityStamp.signedPayload(goc), Charsets.UTF_8)
        assertThat(payload).doesNotContain("chukyratdai")
        assertThat(payload).contains(goc.hash)
        assertThat(payload).contains(goc.publicKey)
    }

    @Test
    fun `signedPayload giong nhau du chu ky khac nhau`() {
        // Ký rồi mới gắn chữ ký vào: payload lúc ký và lúc verify phải trùng khít.
        val truocKhiKy = stamp(signature = "")
        val sauKhiKy = truocKhiKy.copy(signature = "c2lnbmVk")
        assertThat(AuthenticityStamp.signedPayload(sauKhiKy))
            .isEqualTo(AuthenticityStamp.signedPayload(truocKhiKy))
    }

    @Test
    fun `doi hash lam payload ky doi theo`() {
        val a = AuthenticityStamp.signedPayload(stamp(hash = "a".repeat(64)))
        val b = AuthenticityStamp.signedPayload(stamp(hash = "b".repeat(64)))
        assertThat(a).isNotEqualTo(b)
    }
}
