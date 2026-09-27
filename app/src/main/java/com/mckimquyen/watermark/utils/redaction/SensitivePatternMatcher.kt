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
     */
    private val PHONE_CANDIDATE_REGEX = Regex("""(?:\+\d|0\d)(?:[\s.-]?\d){7,10}""")

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
