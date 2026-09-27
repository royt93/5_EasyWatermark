package com.mckimquyen.watermark.export

/**
 * IDEA-09: mô phỏng biến đổi của nền tảng mạng xã hội (crop tỉ lệ + downscale cạnh dài + recompress
 * JPEG) rồi chấm điểm xem watermark còn "sống sót" (nhìn thấy/đọc được) hay không.
 *
 * Hàm THUẦN (không phụ thuộc Android runtime) để unit test trực tiếp trên JVM — cùng quy ước với
 * [BrandComplianceScorer], và tái dùng luôn [BrandComplianceScorer.NormalizedBox] /
 * [BrandComplianceScorer.Level] / [BrandComplianceScorer.evaluate] thay vì định nghĩa lại rule
 * opacity/contrast/edge/size.
 */
object SurvivabilityProfile {

    enum class Platform {
        FACEBOOK,
        INSTAGRAM,
        ZALO
    }

    /**
     * 1 hồ sơ biến đổi giả lập của nền tảng.
     *
     * @param cropAspect tỉ lệ rộng/cao mà nền tảng ép ảnh về (crop giữa khung); `null` = giữ tỉ lệ gốc
     * @param maxLongEdge giới hạn cạnh dài (px) sau khi nền tảng downscale
     * @param jpegQuality mức nén JPEG nền tảng áp lại — chỉ ảnh hưởng phần HÌNH minh hoạ, không ảnh
     *   hưởng điểm số (điểm tính theo kích thước/vị trí, xem [evaluate])
     */
    data class Profile(
        val platform: Platform,
        val cropAspect: Float?,
        val maxLongEdge: Int,
        val jpegQuality: Int
    )

    /**
     * ponytail: số liệu ƯỚC LƯỢNG theo hành vi công khai của từng nền tảng, KHÔNG phải đo thật —
     * đây là calibration knob, chỉnh thẳng ở đây khi nền tảng đổi hành vi. Cạnh dài giữ trùng với
     * preset resize cùng tên ở `OutputImageUtils.resizePresets` để 2 chỗ không mâu thuẫn nhau.
     */
    val profiles: List<Profile> = listOf(
        Profile(Platform.FACEBOOK, cropAspect = null, maxLongEdge = 2048, jpegQuality = 85),
        Profile(Platform.INSTAGRAM, cropAspect = 1f, maxLongEdge = 1080, jpegQuality = 75),
        Profile(Platform.ZALO, cropAspect = null, maxLongEdge = 1600, jpegQuality = 70)
    )

    enum class Issue {
        /** Watermark bị khung crop của nền tảng cắt mất (một phần hoặc toàn bộ). */
        CROPPED_OUT,

        /** Sau downscale, watermark nhỏ tới mức khó đọc. */
        TOO_SMALL_AFTER_DOWNSCALE,

        /** Kế thừa từ [BrandComplianceScorer]: sát mép sau khi crop. */
        EDGE,
        LOW_OPACITY,
        LOW_CONTRAST,
        SIZE_OUT_OF_RANGE
    }

    enum class Suggestion {
        MOVE_TOWARD_CENTER,
        INCREASE_SIZE,
        INCREASE_OPACITY,
        INCREASE_CONTRAST
    }

    data class Result(
        val level: BrandComplianceScorer.Level,
        val issues: List<Issue>,
        val suggestions: List<Suggestion>,
        /** Chiều cao watermark (px) ước tính trên ảnh output cuối của nền tảng — 0 nếu không đo được. */
        val watermarkHeightPx: Int
    )

    /** Phần diện tích watermark còn nằm trong khung crop — dưới ngưỡng này coi là cảnh báo. */
    const val VISIBLE_RATIO_WARN = 0.9f

    /** Dưới ngưỡng này coi như watermark đã mất hẳn theo nghĩa thực dụng. */
    const val VISIBLE_RATIO_FAIL = 0.5f

    /** Chiều cao watermark (px) trên output cuối: dưới ngưỡng này gần như không đọc được. */
    const val MIN_READABLE_PX_FAIL = 12

    /** Dưới ngưỡng này thì đọc được nhưng đã rất khó. */
    const val MIN_READABLE_PX_WARN = 24

    /**
     * Khung crop giữa ảnh (chuẩn hoá 0..1) khi nền tảng ép ảnh về [targetAspect] (rộng/cao).
     * Ảnh đã đúng tỉ lệ → trả về nguyên khung đầy đủ.
     */
    fun centerCropBox(srcWidth: Int, srcHeight: Int, targetAspect: Float): BrandComplianceScorer.NormalizedBox {
        val full = BrandComplianceScorer.NormalizedBox(0f, 0f, 1f, 1f)
        if (srcWidth <= 0 || srcHeight <= 0 || targetAspect <= 0f) return full

        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
        return when {
            // Ảnh rộng hơn mức cho phép → cắt bớt 2 bên.
            srcAspect > targetAspect -> {
                val keepWidth = targetAspect / srcAspect
                val margin = (1f - keepWidth) / 2f
                BrandComplianceScorer.NormalizedBox(margin, 0f, 1f - margin, 1f)
            }
            // Ảnh cao hơn mức cho phép → cắt bớt trên/dưới.
            srcAspect < targetAspect -> {
                val keepHeight = srcAspect / targetAspect
                val margin = (1f - keepHeight) / 2f
                BrandComplianceScorer.NormalizedBox(0f, margin, 1f, 1f - margin)
            }

            else -> full
        }
    }

    /**
     * Chiếu [rect] (chuẩn hoá theo khung GỐC) sang hệ toạ độ chuẩn hoá của khung [crop].
     * Trả `null` khi watermark nằm hoàn toàn ngoài vùng crop (bị cắt mất sạch).
     */
    fun mapThroughCrop(
        rect: BrandComplianceScorer.NormalizedBox,
        crop: BrandComplianceScorer.NormalizedBox
    ): BrandComplianceScorer.NormalizedBox? {
        val cropWidth = crop.right - crop.left
        val cropHeight = crop.bottom - crop.top
        if (cropWidth <= 0f || cropHeight <= 0f) return null

        val left = maxOf(rect.left, crop.left)
        val top = maxOf(rect.top, crop.top)
        val right = minOf(rect.right, crop.right)
        val bottom = minOf(rect.bottom, crop.bottom)
        if (right <= left || bottom <= top) return null

        return BrandComplianceScorer.NormalizedBox(
            left = (left - crop.left) / cropWidth,
            top = (top - crop.top) / cropHeight,
            right = (right - crop.left) / cropWidth,
            bottom = (bottom - crop.top) / cropHeight
        )
    }

    /**
     * Chấm điểm khả năng sống sót của watermark sau biến đổi của [profile].
     *
     * Kích thước đọc được tính ANALYTICALLY từ [originalWidth]/[originalHeight] thật, KHÔNG đo trên
     * bitmap preview (preview decode ở kích thước nhỏ nên px đo được không phản ánh output thật).
     *
     * @param watermarkRect khung watermark chuẩn hoá trên ảnh gốc; `null` = watermark lặp toàn khung
     *   (REPEAT/MIRROR) — dạng này crop/downscale không thể xoá sạch nên bỏ qua rule vị trí/kích thước
     * @param contrastRatio tỉ lệ tương phản WCAG đo được ở vùng watermark (`null` nếu không đo được)
     */
    fun evaluate(
        watermarkRect: BrandComplianceScorer.NormalizedBox?,
        alpha: Int,
        contrastRatio: Double?,
        profile: Profile,
        originalWidth: Int,
        originalHeight: Int
    ): Result {
        val isTiled = watermarkRect == null
        val issues = mutableListOf<Issue>()
        val suggestions = mutableListOf<Suggestion>()
        var level = BrandComplianceScorer.Level.PASS

        fun raise(to: BrandComplianceScorer.Level) {
            if (to.ordinal > level.ordinal) level = to
        }

        val crop = if (!isTiled && profile.cropAspect != null) {
            centerCropBox(originalWidth, originalHeight, profile.cropAspect)
        } else {
            null
        }

        // 1. Watermark có còn nằm trong khung sau khi nền tảng crop không?
        val mappedRect = if (watermarkRect == null) {
            null
        } else if (crop == null) {
            watermarkRect
        } else {
            val visible = mapThroughCrop(watermarkRect, crop)
            val rectArea = areaOf(watermarkRect)
            val visibleRatio = if (rectArea <= 0f || visible == null) {
                0f
            } else {
                // `visible` đã ở hệ toạ độ khung crop — quy về tỉ lệ so với watermark gốc.
                areaOf(visible) * areaOf(crop) / rectArea
            }
            when {
                visibleRatio < VISIBLE_RATIO_FAIL -> {
                    issues.add(Issue.CROPPED_OUT)
                    suggestions.add(Suggestion.MOVE_TOWARD_CENTER)
                    raise(BrandComplianceScorer.Level.FAIL)
                }

                visibleRatio < VISIBLE_RATIO_WARN -> {
                    issues.add(Issue.CROPPED_OUT)
                    suggestions.add(Suggestion.MOVE_TOWARD_CENTER)
                    raise(BrandComplianceScorer.Level.WARN)
                }
            }
            visible
        }

        // 2. Sau downscale, watermark còn cao bao nhiêu px thật?
        val watermarkHeightPx = if (mappedRect == null) {
            0
        } else {
            val croppedWidth = originalWidth * (crop?.let { it.right - it.left } ?: 1f)
            val croppedHeight = originalHeight * (crop?.let { it.bottom - it.top } ?: 1f)
            val longEdge = maxOf(croppedWidth, croppedHeight)
            val scale = if (profile.maxLongEdge > 0 && longEdge > profile.maxLongEdge) {
                profile.maxLongEdge / longEdge
            } else {
                1f
            }
            ((mappedRect.bottom - mappedRect.top) * croppedHeight * scale).toInt()
        }
        if (mappedRect != null && watermarkHeightPx > 0) {
            when {
                watermarkHeightPx < MIN_READABLE_PX_FAIL -> {
                    issues.add(Issue.TOO_SMALL_AFTER_DOWNSCALE)
                    suggestions.add(Suggestion.INCREASE_SIZE)
                    raise(BrandComplianceScorer.Level.FAIL)
                }

                watermarkHeightPx < MIN_READABLE_PX_WARN -> {
                    issues.add(Issue.TOO_SMALL_AFTER_DOWNSCALE)
                    suggestions.add(Suggestion.INCREASE_SIZE)
                    raise(BrandComplianceScorer.Level.WARN)
                }
            }
        }

        // 3. Rule opacity/contrast/edge/size — dùng lại nguyên BrandComplianceScorer trên khung SAU
        // crop (không truyền faces: badge che-mặt đã hiển thị sẵn ở grid preview, IDEA-15/IDEA-01).
        val base = BrandComplianceScorer.evaluate(
            watermarkRect = mappedRect,
            alpha = alpha,
            contrastRatio = contrastRatio,
            faces = emptyList(),
            layoutMode = if (isTiled) {
                BrandComplianceScorer.LayoutMode.TILED
            } else {
                BrandComplianceScorer.LayoutMode.SINGLE
            }
        )
        raise(base.level)
        for (baseIssue in base.issues) {
            when (baseIssue) {
                BrandComplianceScorer.Issue.EDGE -> {
                    issues.add(Issue.EDGE)
                    suggestions.add(Suggestion.MOVE_TOWARD_CENTER)
                }

                BrandComplianceScorer.Issue.LOW_OPACITY -> {
                    issues.add(Issue.LOW_OPACITY)
                    suggestions.add(Suggestion.INCREASE_OPACITY)
                }

                BrandComplianceScorer.Issue.LOW_CONTRAST -> {
                    issues.add(Issue.LOW_CONTRAST)
                    suggestions.add(Suggestion.INCREASE_CONTRAST)
                }

                BrandComplianceScorer.Issue.SIZE_OUT_OF_RANGE -> {
                    issues.add(Issue.SIZE_OUT_OF_RANGE)
                    // Chỉ gợi ý phóng to khi watermark đang QUÁ NHỎ — quá lớn thì gợi ý ngược lại là sai.
                    if (mappedRect != null && areaOf(mappedRect) < BrandComplianceScorer.SIZE_MIN_WARN) {
                        suggestions.add(Suggestion.INCREASE_SIZE)
                    }
                }

                BrandComplianceScorer.Issue.COVERS_FACE -> Unit // không truyền faces nên không xảy ra
            }
        }

        return Result(
            level = level,
            issues = issues.distinct(),
            suggestions = suggestions.distinct(),
            watermarkHeightPx = watermarkHeightPx
        )
    }

    private fun areaOf(box: BrandComplianceScorer.NormalizedBox): Float {
        val width = (box.right - box.left).coerceAtLeast(0f)
        val height = (box.bottom - box.top).coerceAtLeast(0f)
        return width * height
    }
}
