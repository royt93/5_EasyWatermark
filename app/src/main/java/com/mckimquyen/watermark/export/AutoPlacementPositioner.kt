package com.mckimquyen.watermark.export

import com.mckimquyen.watermark.data.model.Anchor

/**
 * Hình chữ nhật chuẩn hoá 0..1 THUẦN Kotlin (không dùng `android.graphics.RectF`) — cố tình tách
 * khỏi RectF vì AGP unit test config của module này bật `isReturnDefaultValues = true`
 * (`app/build.gradle.kts`), khiến constructor `RectF(l,t,r,b)` bị stub rỗng (field luôn 0) khi
 * chạy plain JUnit KHÔNG qua Robolectric — verify thực nghiệm lúc viết [AutoPlacementPositionerTest].
 * Chuyển đổi RectF <-> [NormalizedRect] ở lớp gọi ngoài ([AutoPlacementEngine]), nơi Android runtime
 * thật (app/Robolectric) hoạt động bình thường.
 */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * IDEA-01: chọn 1 trong 9 preset [Anchor] có tổng diện tích giao với khuôn mặt nhỏ nhất — hàm
 * THUẦN (không phụ thuộc Android runtime, không đọc/ghi `ImageInfo`), tái dùng nguyên
 * [Anchor.toOffset] đã có sẵn thay vì phát minh công thức vị trí mới.
 */
object AutoPlacementPositioner {

    /** Góc/cạnh trước, CENTER cuối cùng — quy ước đặt watermark thông thường, dùng làm tie-break khi 2+ anchor cùng mức giao. */
    private val TIE_BREAK_PRIORITY = listOf(
        Anchor.BOTTOM_RIGHT, Anchor.BOTTOM_LEFT, Anchor.TOP_RIGHT, Anchor.TOP_LEFT,
        Anchor.BOTTOM_CENTER, Anchor.TOP_CENTER, Anchor.CENTER_RIGHT, Anchor.CENTER_LEFT, Anchor.CENTER
    )

    /**
     * @param faceRects toạ độ mặt chuẩn hoá 0..1 (rỗng = không có mặt nào -> trả `null`, giữ nguyên
     * vị trí hiện tại của ảnh, không có gì để né).
     * @param wmFracW/wmFracH kích thước watermark tính theo tỉ lệ 0..1 so với ảnh (giống tham số
     * [Anchor.toOffset] đang nhận).
     * @return anchor giao ít nhất với mọi mặt; luôn có kết quả nếu [faceRects] không rỗng (kể cả
     * khi MỌI anchor đều giao > 0 — vẫn chọn cái ít tệ nhất, không bao giờ bỏ cuộc).
     */
    fun pickBestAnchor(
        faceRects: List<NormalizedRect>,
        marginPercent: Float,
        wmFracW: Float,
        wmFracH: Float
    ): Anchor? {
        if (faceRects.isEmpty()) return null
        return TIE_BREAK_PRIORITY.minByOrNull { anchor ->
            val (offsetX, offsetY) = anchor.toOffset(marginPercent, wmFracW, wmFracH)
            val watermarkRect = NormalizedRect(offsetX, offsetY, offsetX + wmFracW, offsetY + wmFracH)
            faceRects.sumOf { face -> intersectionArea(watermarkRect, face).toDouble() }
        }
    }

    private fun intersectionArea(a: NormalizedRect, b: NormalizedRect): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        return (right - left) * (bottom - top)
    }
}
