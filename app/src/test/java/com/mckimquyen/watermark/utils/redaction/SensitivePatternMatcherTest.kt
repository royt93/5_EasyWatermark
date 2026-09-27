package com.mckimquyen.watermark.utils.redaction

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IDEA-14: đây là bộ lọc quyết định vùng nào bị coi là "nhạy cảm" — sai theo cả 2 hướng đều tệ:
 * bỏ sót thì lộ thông tin thật, dương tính giả thì che nhầm nội dung vô hại (làm phiền user).
 */
class SensitivePatternMatcherTest {

    // ── Email ──────────────────────────────────────────────────────────────

    @Test
    fun `email don gian duoc nhan dien`() {
        assertThat(SensitivePatternMatcher.isSensitive("roy@gmail.com")).isTrue()
    }

    @Test
    fun `email nam giua cau van van duoc nhan dien`() {
        assertThat(SensitivePatternMatcher.isSensitive("Liên hệ: roy.studio+contact@my-domain.co.vn nhé")).isTrue()
    }

    @Test
    fun `nhieu domain khac nhau deu duoc nhan dien`() {
        assertThat(SensitivePatternMatcher.isSensitive("a@b.io")).isTrue()
        assertThat(SensitivePatternMatcher.isSensitive("test_user@sub.example.org")).isTrue()
    }

    // ── Số điện thoại ──────────────────────────────────────────────────────

    @Test
    fun `sdt VN 10 so bat dau bang 0`() {
        assertThat(SensitivePatternMatcher.isSensitive("0912345678")).isTrue()
    }

    @Test
    fun `sdt VN co khoang trang giua cac cum`() {
        assertThat(SensitivePatternMatcher.isSensitive("091 234 5678")).isTrue()
    }

    @Test
    fun `sdt VN co gach ngang`() {
        assertThat(SensitivePatternMatcher.isSensitive("091-234-5678")).isTrue()
    }

    @Test
    fun `sdt quoc te dang +84`() {
        assertThat(SensitivePatternMatcher.isSensitive("+84912345678")).isTrue()
    }

    @Test
    fun `sdt nam trong cau`() {
        assertThat(SensitivePatternMatcher.isSensitive("Gọi ngay 0912345678 để đặt lịch")).isTrue()
    }

    // ── Không nhạy cảm — KHÔNG được báo dương tính giả ────────────────────

    @Test
    fun `chuoi thuong khong bi bao nham`() {
        assertThat(SensitivePatternMatcher.isSensitive("Watermark Creator")).isFalse()
    }

    @Test
    fun `rong hoac khoang trang thi khong nhay cam`() {
        assertThat(SensitivePatternMatcher.isSensitive("")).isFalse()
        assertThat(SensitivePatternMatcher.isSensitive("   ")).isFalse()
    }

    @Test
    fun `ngay thang khong bi nham thanh sdt`() {
        // Định dạng ngày dùng "/" — không nằm trong separator hợp lệ của regex SĐT, và không neo 0/+.
        assertThat(SensitivePatternMatcher.isSensitive("27/09/2026")).isFalse()
    }

    @Test
    fun `so khong bat dau bang 0 hoac + thi khong tinh la sdt`() {
        // Số ngẫu nhiên 10 chữ số nhưng không neo đúng dạng khởi đầu số điện thoại thật.
        assertThat(SensitivePatternMatcher.isSensitive("1234567890")).isFalse()
    }

    @Test
    fun `chuoi so qua ngan khong phai sdt`() {
        assertThat(SensitivePatternMatcher.isSensitive("012345")).isFalse()
    }

    @Test
    fun `chuoi so qua dai vuot nguong khong tinh la sdt`() {
        // 15 chữ số liền — không giống định dạng SĐT thật nào, nếu match được thì digit count vượt trần.
        assertThat(SensitivePatternMatcher.isSensitive("012345678901234")).isFalse()
    }

    @Test
    fun `khong co ky tu at thi khong phai email`() {
        assertThat(SensitivePatternMatcher.isSensitive("roy.studio.com")).isFalse()
    }
}
