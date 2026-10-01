package com.mckimquyen.watermark.utils.redaction

/**
 * IDEA-14: nhận diện một dòng text (đọc từ ML Kit Text Recognition) có phải thông tin nhạy cảm
 * (email hoặc số điện thoại) hay không. Thuần Kotlin, không phụ thuộc Android — tách khỏi
 * [com.mckimquyen.watermark.utils.textdetection.MlKitSensitiveTextSource] để unit test phủ dày mọi
 * case định dạng mà không cần Robolectric.
 */
object SensitivePatternMatcher {

    /** RFC-lite: đủ chặt để không bắt nhầm câu thường, đủ lỏng để bắt được domain thật. */
    private val EMAIL_REGEX = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    /**
     * Số điện thoại VN + quốc tế: BẮT BUỘC bắt đầu bằng `0` (VN nội địa) hoặc `+` (mã quốc gia) —
     * nếu không neo vào 1 trong 2 dạng khởi đầu thật của số điện thoại, mọi chuỗi 9-11 chữ số bất kỳ
     * (mã đơn hàng, ngày giờ dán liền...) đều khớp giả, không dùng được để đối chiếu tin cậy.
     * Cho phép khoảng trắng/gạch ngang/chấm xen giữa các cụm số (cách hiển thị phổ biến).
     *
     * BUG-51: quantifier trên PHẢI neo đúng [PHONE_DIGITS_MAX] (11) — cũ là `{7,10}` (2 chữ số neo
     * + tối đa 10 lặp = tối đa 12 chữ số), vượt trần. Khi SĐT 10 số dính liền số khác (OCR không có
     * khoảng cách), quantifier tham lam nuốt luôn digit thừa thành 1 candidate 12 số, bị loại bỏ
     * hoàn toàn — `findAll` không khớp lại vị trí con bên trong nên SĐT thật không bao giờ được thử
     * lại. Giới hạn `{7,9}` (tối đa 11 chữ số) đảm bảo match luôn nằm trong trần hợp lệ ngay từ
     * regex, không cần đợi bước strip-và-đếm digit phía sau loại bỏ.
     */
    private val PHONE_CANDIDATE_REGEX = Regex("""(?:\+\d|0\d)(?:[\s.-]?\d){7,9}""")

    private const val PHONE_DIGITS_MIN = 9
    private const val PHONE_DIGITS_MAX = 11

    /** @return true nếu [text] chứa email hoặc số điện thoại nhạy cảm. */
    fun isSensitive(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return EMAIL_REGEX.containsMatchIn(trimmed) || containsPhoneNumber(trimmed)
    }

    private fun containsPhoneNumber(text: String): Boolean =
        PHONE_CANDIDATE_REGEX.findAll(text).any { match ->
            val digitsOnly = match.value.filter { it.isDigit() }
            digitsOnly.length in PHONE_DIGITS_MIN..PHONE_DIGITS_MAX
        }
}
