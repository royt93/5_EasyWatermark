package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.Anchor
import org.junit.Test

/**
 * IDEA-01: [AutoPlacementPositioner.pickBestAnchor] — hàm thuần, không phụ thuộc ML Kit/Android
 * runtime thật. `wmFracW=0.2f, wmFracH=0.1f, marginPercent=0.05f` dùng xuyên suốt để dễ tính tay
 * toạ độ từng anchor: BOTTOM_RIGHT = (0.75,0.85)-(0.95,0.95), BOTTOM_LEFT = (0.05,0.85)-(0.25,0.95),
 * TOP_LEFT = (0.05,0.05)-(0.25,0.15), CENTER = (0.4,0.45)-(0.6,0.55).
 */
class AutoPlacementPositionerTest {

    private val wmFracW = 0.2f
    private val wmFracH = 0.1f
    private val margin = 0.05f

    private fun pick(faceRects: List<NormalizedRect>) =
        AutoPlacementPositioner.pickBestAnchor(faceRects, margin, wmFracW, wmFracH)

    @Test
    fun noFaces_returnsNull() {
        assertThat(pick(emptyList())).isNull()
    }

    @Test
    fun faceCoversCenter_allCornersZeroOverlap_picksTopPriorityCorner() {
        val faceOverCenter = NormalizedRect(0.3f, 0.3f, 0.7f, 0.7f)
        // Mọi anchor góc/cạnh đều giao=0 với mặt giữa ảnh -> chọn theo thứ tự ưu tiên, đầu tiên là BOTTOM_RIGHT.
        assertThat(pick(listOf(faceOverCenter))).isEqualTo(Anchor.BOTTOM_RIGHT)
    }

    @Test
    fun faceCoversRightHalf_skipsBottomRight_picksNextPriorityWithZeroOverlap() {
        val faceRightHalf = NormalizedRect(0.5f, 0f, 1f, 1f)
        // BOTTOM_RIGHT (ưu tiên 1) giao với mặt (x 0.75-0.95 nằm trong 0.5-1) -> bị loại.
        // BOTTOM_LEFT (ưu tiên 2, x 0.05-0.25) không giao -> được chọn.
        assertThat(pick(listOf(faceRightHalf))).isEqualTo(Anchor.BOTTOM_LEFT)
    }

    @Test
    fun multipleFacesScattered_picksAnchorWithLeastTotalOverlap() {
        // Mặt phủ toàn bộ cạnh phải + góc trên-trái -> chỉ còn góc dưới-trái sạch.
        val faceRight = NormalizedRect(0.7f, 0f, 1f, 1f)
        val faceTopLeft = NormalizedRect(0f, 0f, 0.3f, 0.3f)
        assertThat(pick(listOf(faceRight, faceTopLeft))).isEqualTo(Anchor.BOTTOM_LEFT)
    }

    @Test
    fun faceCoversEntireImage_everyAnchorOverlaps_stillReturnsNonNullBestEffort() {
        val fullImageFace = NormalizedRect(0f, 0f, 1f, 1f)
        // Không anchor nào tránh được, nhưng vẫn phải trả 1 lựa chọn (đỡ tệ nhất) chứ không null/crash.
        assertThat(pick(listOf(fullImageFace))).isNotNull()
    }

    @Test
    fun twoFacesLeavingOnlyTopRightClean_picksTopRight() {
        val faceLeft = NormalizedRect(0f, 0f, 0.5f, 1f)
        val faceBottom = NormalizedRect(0f, 0.6f, 1f, 1f)
        // Loại hết BOTTOM_RIGHT/BOTTOM_LEFT (dính faceBottom) và TOP_LEFT (dính faceLeft) -> còn TOP_RIGHT.
        assertThat(pick(listOf(faceLeft, faceBottom))).isEqualTo(Anchor.TOP_RIGHT)
    }
}
