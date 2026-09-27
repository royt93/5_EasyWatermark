package com.mckimquyen.watermark.ui.widget

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.ui.widget.RedactionOverlayView.Companion.findSuggestionAt
import com.mckimquyen.watermark.ui.widget.RedactionOverlayView.HitBox
import org.junit.Test

/**
 * IDEA-14: hit-test tap-để-toggle-vùng — tách thuần Kotlin (không dùng [android.graphics.RectF])
 * để test được trong plain JUnit, tránh landmine RectF constructor bị stub rỗng khi không chạy
 * Robolectric (xem [[feedback_returnDefaultValues_android_graphics_stub]]).
 */
class RedactionOverlayViewHitTestTest {

    @Test
    fun `tap trung 1 vung thi tra dung index`() {
        val boxes = listOf(HitBox(0.1f, 0.1f, 0.3f, 0.3f))
        assertThat(findSuggestionAt(boxes, 0.2f, 0.2f)).isEqualTo(0)
    }

    @Test
    fun `tap ngoai moi vung thi tra -1`() {
        val boxes = listOf(HitBox(0.1f, 0.1f, 0.3f, 0.3f))
        assertThat(findSuggestionAt(boxes, 0.9f, 0.9f)).isEqualTo(-1)
    }

    @Test
    fun `danh sach rong luon tra -1`() {
        assertThat(findSuggestionAt(emptyList(), 0.5f, 0.5f)).isEqualTo(-1)
    }

    @Test
    fun `tap dung bien trai-tren tinh la trung`() {
        val boxes = listOf(HitBox(0.1f, 0.1f, 0.3f, 0.3f))
        assertThat(findSuggestionAt(boxes, 0.1f, 0.1f)).isEqualTo(0)
    }

    @Test
    fun `tap dung bien phai-duoi tinh la trung`() {
        val boxes = listOf(HitBox(0.1f, 0.1f, 0.3f, 0.3f))
        assertThat(findSuggestionAt(boxes, 0.3f, 0.3f)).isEqualTo(0)
    }

    @Test
    fun `nhieu vung khong chong lan chon dung tung vung`() {
        val boxes = listOf(
            HitBox(0.0f, 0.0f, 0.2f, 0.2f),
            HitBox(0.5f, 0.5f, 0.7f, 0.7f)
        )
        assertThat(findSuggestionAt(boxes, 0.1f, 0.1f)).isEqualTo(0)
        assertThat(findSuggestionAt(boxes, 0.6f, 0.6f)).isEqualTo(1)
    }

    @Test
    fun `2 vung chong lan thi uu tien vung ve sau cung (index lon hon)`() {
        val boxes = listOf(
            HitBox(0.0f, 0.0f, 0.5f, 0.5f),
            HitBox(0.2f, 0.2f, 0.4f, 0.4f)
        )
        // Điểm (0.3, 0.3) nằm trong CẢ 2 vùng — phải chọn vùng vẽ sau cùng (index 1).
        assertThat(findSuggestionAt(boxes, 0.3f, 0.3f)).isEqualTo(1)
    }
}
