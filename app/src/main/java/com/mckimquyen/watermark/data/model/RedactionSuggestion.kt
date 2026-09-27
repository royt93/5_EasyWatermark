package com.mckimquyen.watermark.data.model

import android.graphics.RectF

/**
 * IDEA-14: 1 vùng nhạy cảm được đề xuất che trong `SmartRedactionActivity`. [source] chỉ phục vụ
 * hiển thị (tô màu khác nhau theo nguồn phát hiện), không ảnh hưởng logic áp mosaic.
 *
 * Mặc định [confirmed] = true — mọi vùng đề xuất được coi là "sẽ che" ngay khi hiện ra, user tap để
 * BỎ vùng không muốn che (không phải tap để THÊM). An toàn hơn cho mục đích riêng tư: quên tap vẫn
 * che, không phải quên tap thì lộ.
 */
data class RedactionSuggestion(
    val rect: RectF,
    val source: Source,
    val confirmed: Boolean = true
) {
    enum class Source { TEXT, FACE }
}
