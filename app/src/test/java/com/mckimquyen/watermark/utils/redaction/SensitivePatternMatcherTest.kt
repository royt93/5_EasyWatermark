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

    /**
     * BUG-51: quantifier cũ `{7,10}` cho phép match tham lam tới 12 chữ số (2 chữ số neo + 10 lặp)
     * — vượt trần `PHONE_DIGITS_MAX=11` — khi SĐT 10 số dính liền số khác (OCR không có khoảng
     * cách). Candidate 12 số bị loại bỏ hoàn toàn, `findAll` không overlap nên SĐT thật bên trong
     * không bao giờ được thử lại. Quantifier đúng phải neo tới đúng trần 11 số.
     */
    @Test
    fun `sdt dinh lien voi chuoi so khac van duoc phat hien`() {
        // Mô phỏng OCR dính: "0912345678" (SĐT thật) + "1234567" (mã đơn hàng) không khoảng cách.
        assertThat(SensitivePatternMatcher.isSensitive("09123456781234567")).isTrue()
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
        // BUG-51: đổi mẫu từ "012345678901234" — chuỗi đó TÌNH CỜ bắt đầu bằng "0" + digit hợp lệ,
        // dưới quantifier đã sửa (neo đúng trần 11 số) nó tạo ra đúng 1 cửa sổ 11-số hợp lệ ở đầu —
        // về bản chất giống hệt case SĐT dính liền số khác mà chính ticket này yêu cầu PHẢI phát
        // hiện (xem test "sdt dinh lien..."), nên không còn là phản ví dụ hợp lệ. Mẫu mới không có
        // "0"/"+" nào đứng đầu 1 dãy ≥7 chữ số tiếp theo → không tạo được candidate nào, giữ đúng ý
        // định gốc của test (chuỗi số dài vô nghĩa không được báo nhầm).
        assertThat(SensitivePatternMatcher.isSensitive("123456789012345")).isFalse()
    }

    @Test
    fun `khong co ky tu at thi khong phai email`() {
        assertThat(SensitivePatternMatcher.isSensitive("roy.studio.com")).isFalse()
    }
}
