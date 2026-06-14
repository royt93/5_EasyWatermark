package com.mckimquyen.watermark.utils

/**
 * Thay các token động dạng {ten} trong nội dung text watermark bằng giá trị tương ứng.
 * Hàm thuần (không phụ thuộc Android) để dễ unit test; phần lấy dữ liệu (EXIF, tên file...)
 * do nơi gọi chuẩn bị sẵn thành [tokens].
 */
object TextTokenResolver {

    /** Các token được hỗ trợ (không bao gồm dấu ngoặc). */
    val SUPPORTED_TOKENS = listOf(
        "filename", "seq", "date", "model", "make",
        "iso", "fnumber", "exposure", "focal", "exif"
    )

    /**
     * @param text nội dung gốc; nếu không chứa '{' thì trả về nguyên văn (fast path).
     * @param tokens map tên-token (không ngoặc) → giá trị thay thế. Giá trị null coi như rỗng.
     */
    fun resolve(text: String, tokens: Map<String, String?>): String {
        if (!text.contains('{')) return text
        var result = text
        for ((key, value) in tokens) {
            result = result.replace("{$key}", value.orEmpty())
        }
        return result
    }
}
