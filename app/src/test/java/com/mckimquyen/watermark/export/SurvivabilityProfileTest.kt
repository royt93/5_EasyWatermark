package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IDEA-09: unit test THUẦN (không Robolectric) cho toán hình học + rule chấm điểm sống sót.
 * [SurvivabilityProfile] cố tình không dùng `RectF` nên chạy được trực tiếp trên JVM.
 */
class SurvivabilityProfileTest {

    private val instagram = SurvivabilityProfile.profiles.first { it.platform == SurvivabilityProfile.Platform.INSTAGRAM }
    private val facebook = SurvivabilityProfile.profiles.first { it.platform == SurvivabilityProfile.Platform.FACEBOOK }

    private val safeAlpha = 200
    private val safeContrast = 7.0

    /** Watermark an toàn: nằm giữa ảnh, chiếm ~9% khung. */
    private val centerRect = BrandComplianceScorer.NormalizedBox(0.35f, 0.35f, 0.65f, 0.65f)

    @Test
    fun `moi profile co tham so hop le`() {
        assertThat(SurvivabilityProfile.profiles).hasSize(3)
        for (profile in SurvivabilityProfile.profiles) {
            assertThat(profile.maxLongEdge).isGreaterThan(0)
            assertThat(profile.jpegQuality).isIn(1..100)
        }
    }

    @Test
    fun `centerCropBox cat 2 ben khi anh ngang hon ti le dich`() {
        val box = SurvivabilityProfile.centerCropBox(2000, 1000, 1f)
        assertThat(box.left).isWithin(TOL).of(0.25f)
        assertThat(box.right).isWithin(TOL).of(0.75f)
        assertThat(box.top).isWithin(TOL).of(0f)
        assertThat(box.bottom).isWithin(TOL).of(1f)
    }

    @Test
    fun `centerCropBox cat tren duoi khi anh cao hon ti le dich`() {
        val box = SurvivabilityProfile.centerCropBox(1000, 2000, 1f)
        assertThat(box.top).isWithin(TOL).of(0.25f)
        assertThat(box.bottom).isWithin(TOL).of(0.75f)
        assertThat(box.left).isWithin(TOL).of(0f)
        assertThat(box.right).isWithin(TOL).of(1f)
    }

    @Test
    fun `centerCropBox giu nguyen khung khi da dung ti le hoac kich thuoc khong hop le`() {
        val square = SurvivabilityProfile.centerCropBox(1000, 1000, 1f)
        assertThat(square).isEqualTo(BrandComplianceScorer.NormalizedBox(0f, 0f, 1f, 1f))

        val invalid = SurvivabilityProfile.centerCropBox(0, 1000, 1f)
        assertThat(invalid).isEqualTo(BrandComplianceScorer.NormalizedBox(0f, 0f, 1f, 1f))
    }

    @Test
    fun `mapThroughCrop tra null khi watermark nam ngoai vung crop`() {
        // Crop giữa ảnh ngang 2000x1000 về vuông → giữ x trong [0.25, 0.75].
        val crop = SurvivabilityProfile.centerCropBox(2000, 1000, 1f)
        val farRight = BrandComplianceScorer.NormalizedBox(0.8f, 0.8f, 0.95f, 0.95f)
        assertThat(SurvivabilityProfile.mapThroughCrop(farRight, crop)).isNull()
    }

    @Test
    fun `mapThroughCrop doi toa do sang he khung da crop`() {
        val crop = BrandComplianceScorer.NormalizedBox(0.25f, 0f, 0.75f, 1f)
        val rect = BrandComplianceScorer.NormalizedBox(0.5f, 0.2f, 0.625f, 0.4f)
        val mapped = SurvivabilityProfile.mapThroughCrop(rect, crop)
        assertThat(mapped).isNotNull()
        assertThat(mapped!!.left).isWithin(TOL).of(0.5f)
        assertThat(mapped.right).isWithin(TOL).of(0.75f)
        // Trục dọc không bị crop nên giữ nguyên.
        assertThat(mapped.top).isWithin(TOL).of(0.2f)
        assertThat(mapped.bottom).isWithin(TOL).of(0.4f)
    }

    @Test
    fun `watermark giua anh thi pass`() {
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = centerRect,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = facebook,
            originalWidth = 4000,
            originalHeight = 3000
        )
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.PASS)
        assertThat(result.issues).isEmpty()
        assertThat(result.suggestions).isEmpty()
        assertThat(result.watermarkHeightPx).isGreaterThan(SurvivabilityProfile.MIN_READABLE_PX_WARN)
    }

    @Test
    fun `watermark sat goc bi instagram crop mat thi fail va goi y doi vao giua`() {
        val corner = BrandComplianceScorer.NormalizedBox(0.85f, 0.4f, 0.97f, 0.5f)
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = corner,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = instagram,
            originalWidth = 4000,
            originalHeight = 2000
        )
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(result.issues).contains(SurvivabilityProfile.Issue.CROPPED_OUT)
        assertThat(result.suggestions).contains(SurvivabilityProfile.Suggestion.MOVE_TOWARD_CENTER)
    }

    @Test
    fun `cung watermark do nhung facebook khong crop thi khong bao cropped out`() {
        val corner = BrandComplianceScorer.NormalizedBox(0.85f, 0.4f, 0.97f, 0.5f)
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = corner,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = facebook,
            originalWidth = 4000,
            originalHeight = 2000
        )
        assertThat(result.issues).doesNotContain(SurvivabilityProfile.Issue.CROPPED_OUT)
    }

    @Test
    fun `watermark qua nho sau downscale thi canh bao va goi y phong to`() {
        // Cao 0.4% của 3000px = 12px trên ảnh gốc, sau khi Facebook hạ cạnh dài 4000→2048 còn ~6px.
        val tiny = BrandComplianceScorer.NormalizedBox(0.45f, 0.5f, 0.55f, 0.504f)
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = tiny,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = facebook,
            originalWidth = 4000,
            originalHeight = 3000
        )
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(result.issues).contains(SurvivabilityProfile.Issue.TOO_SMALL_AFTER_DOWNSCALE)
        assertThat(result.suggestions).contains(SurvivabilityProfile.Suggestion.INCREASE_SIZE)
        assertThat(result.watermarkHeightPx).isLessThan(SurvivabilityProfile.MIN_READABLE_PX_FAIL)
    }

    @Test
    fun `alpha thap va tuong phan thap sinh dung goi y tuong ung`() {
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = centerRect,
            alpha = 40,
            contrastRatio = 1.2,
            profile = facebook,
            originalWidth = 4000,
            originalHeight = 3000
        )
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(result.issues).containsAtLeast(
            SurvivabilityProfile.Issue.LOW_OPACITY,
            SurvivabilityProfile.Issue.LOW_CONTRAST
        )
        assertThat(result.suggestions).containsAtLeast(
            SurvivabilityProfile.Suggestion.INCREASE_OPACITY,
            SurvivabilityProfile.Suggestion.INCREASE_CONTRAST
        )
    }

    @Test
    fun `che do lap toan khung bo qua rule vi tri kich thuoc`() {
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = null,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = instagram,
            originalWidth = 4000,
            originalHeight = 2000
        )
        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.PASS)
        assertThat(result.issues).isEmpty()
        assertThat(result.watermarkHeightPx).isEqualTo(0)
    }

    @Test
    fun `watermark qua lon khong goi y phong to them`() {
        val huge = BrandComplianceScorer.NormalizedBox(0.05f, 0.05f, 0.95f, 0.95f)
        val result = SurvivabilityProfile.evaluate(
            watermarkRect = huge,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            profile = facebook,
            originalWidth = 4000,
            originalHeight = 3000
        )
        assertThat(result.issues).contains(SurvivabilityProfile.Issue.SIZE_OUT_OF_RANGE)
        assertThat(result.suggestions).doesNotContain(SurvivabilityProfile.Suggestion.INCREASE_SIZE)
    }

    private companion object {
        const val TOL = 0.0001f
    }
}
