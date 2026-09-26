package com.mckimquyen.watermark.export

/**
 * IDEA-15: chấm điểm thương hiệu (Brand Compliance Scoring) cho watermark trên ảnh trước khi export.
 * Hàm THUẦN (pure Kotlin, không phụ thuộc Android runtime) để chạy unit test độc lập không cần Robolectric.
 */
object BrandComplianceScorer {

    data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

    enum class Level {
        PASS,
        WARN,
        FAIL
    }

    enum class Issue {
        EDGE,
        LOW_OPACITY,
        LOW_CONTRAST,
        COVERS_FACE,
        SIZE_OUT_OF_RANGE
    }

    enum class LayoutMode {
        SINGLE,
        TILED
    }

    data class ComplianceResult(
        val level: Level,
        val issues: List<Issue>
    ) {
        val isPass: Boolean get() = level == Level.PASS
        val isWarn: Boolean get() = level == Level.WARN
        val isFail: Boolean get() = level == Level.FAIL
    }

    // Các ngưỡng chuẩn theo quy tắc thương hiệu
    const val EDGE_MIN_MARGIN_WARN = 0.03f
    const val EDGE_MIN_MARGIN_FAIL = 0.01f

    const val MIN_ALPHA_WARN = 100 // ~39%
    const val MIN_ALPHA_FAIL = 60 // ~23%

    const val CONTRAST_WARN = 3.0
    const val CONTRAST_FAIL = 1.5

    const val SIZE_MIN_WARN = 0.01f // diện tích watermark < 1% khung ảnh
    const val SIZE_MAX_WARN = 0.5f // diện tích watermark > 50% khung ảnh
    const val SIZE_MIN_FAIL = 0.0025f // diện tích watermark < 0.25% khung ảnh
    const val SIZE_MAX_FAIL = 0.85f // diện tích watermark > 85% khung ảnh

    const val FACE_OVERLAP_WARN = 0.05f // che > 5% khuôn mặt
    const val FACE_OVERLAP_FAIL = 0.15f // che > 15% khuôn mặt

    /**
     * Đánh giá độ tuân thủ thương hiệu cho watermark.
     *
     * @param watermarkRect Khung chuẩn hoá 0..1 của watermark (chỉ có khi CLAMP mode)
     * @param alpha Độ mờ đục 0..255 của watermark
     * @param contrastRatio Tỉ lệ tương phản WCAG giữa màu chữ và màu nền (null nếu là ảnh/không đo được)
     * @param faces Danh sách khuôn mặt đã chuẩn hoá 0..1
     * @param layoutMode Watermark đơn hoặc lặp toàn khung
     */
    fun evaluate(
        watermarkRect: NormalizedBox?,
        alpha: Int,
        contrastRatio: Double?,
        faces: List<NormalizedBox>,
        layoutMode: LayoutMode
    ): ComplianceResult {
        val issues = mutableListOf<Issue>()
        var hasFail = false
        var hasWarn = false

        // 1. Độ mờ (áp dụng cho mọi tile mode)
        if (alpha < MIN_ALPHA_FAIL) {
            issues.add(Issue.LOW_OPACITY)
            hasFail = true
        } else if (alpha < MIN_ALPHA_WARN) {
            issues.add(Issue.LOW_OPACITY)
            hasWarn = true
        }

        // 2. Độ tương phản (áp dụng cho mọi tile mode khi đo được)
        if (contrastRatio != null) {
            if (contrastRatio < CONTRAST_FAIL) {
                issues.add(Issue.LOW_CONTRAST)
                hasFail = true
            } else if (contrastRatio < CONTRAST_WARN) {
                issues.add(Issue.LOW_CONTRAST)
                hasWarn = true
            }
        }

        // Các quy tắc vị trí/kích thước/che mặt CHỈ áp dụng cho CLAMP mode
        // (REPEAT / MIRROR là dạng lặp toàn khung theo chủ đích, bỏ qua mép/mặt/kích thước)
        if (layoutMode == LayoutMode.SINGLE && watermarkRect != null) {
            // 3. Quá sát mép
            val left = watermarkRect.left
            val top = watermarkRect.top
            val right = watermarkRect.right
            val bottom = watermarkRect.bottom

            if (left < EDGE_MIN_MARGIN_FAIL || top < EDGE_MIN_MARGIN_FAIL ||
                right > (1f - EDGE_MIN_MARGIN_FAIL) || bottom > (1f - EDGE_MIN_MARGIN_FAIL)
            ) {
                issues.add(Issue.EDGE)
                hasFail = true
            } else if (left < EDGE_MIN_MARGIN_WARN || top < EDGE_MIN_MARGIN_WARN ||
                right > (1f - EDGE_MIN_MARGIN_WARN) || bottom > (1f - EDGE_MIN_MARGIN_WARN)
            ) {
                issues.add(Issue.EDGE)
                hasWarn = true
            }

            // 4. Kích thước bất thường
            val width = (right - left).coerceAtLeast(0f)
            val height = (bottom - top).coerceAtLeast(0f)
            val area = width * height
            if (area <= SIZE_MIN_FAIL || area >= SIZE_MAX_FAIL) {
                issues.add(Issue.SIZE_OUT_OF_RANGE)
                hasFail = true
            } else if (area < SIZE_MIN_WARN || area > SIZE_MAX_WARN) {
                issues.add(Issue.SIZE_OUT_OF_RANGE)
                hasWarn = true
            }

            // 5. Che khuôn mặt
            if (faces.isNotEmpty()) {
                var maxFaceOverlap = 0f
                for (face in faces) {
                    val faceArea = (face.right - face.left).coerceAtLeast(0f) * (face.bottom - face.top).coerceAtLeast(0f)
                    if (faceArea > 0f) {
                        val overlap = intersectionArea(watermarkRect, face)
                        val ratio = overlap / faceArea
                        if (ratio > maxFaceOverlap) {
                            maxFaceOverlap = ratio
                        }
                    }
                }
                if (maxFaceOverlap >= FACE_OVERLAP_FAIL) {
                    issues.add(Issue.COVERS_FACE)
                    hasFail = true
                } else if (maxFaceOverlap >= FACE_OVERLAP_WARN) {
                    issues.add(Issue.COVERS_FACE)
                    hasWarn = true
                }
            }
        }

        val level = when {
            hasFail -> Level.FAIL
            hasWarn -> Level.WARN
            else -> Level.PASS
        }

        return ComplianceResult(level, issues)
    }

    private fun intersectionArea(a: NormalizedBox, b: NormalizedBox): Float {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        return if (right <= left || bottom <= top) 0f else (right - left) * (bottom - top)
    }
}
