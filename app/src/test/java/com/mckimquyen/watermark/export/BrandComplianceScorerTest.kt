package com.mckimquyen.watermark.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * IDEA-15: Unit test thuần (JUnit) cho [BrandComplianceScorer] — kiểm tra đầy đủ các quy tắc
 * thương hiệu (mép, độ mờ, độ tương phản, che mặt, kích thước, tile mode).
 */
class BrandComplianceScorerTest {

    private val safeRect = BrandComplianceScorer.NormalizedBox(left = 0.1f, top = 0.1f, right = 0.4f, bottom = 0.2f)
    private val safeAlpha = 200
    private val safeContrast = 4.5

    @Test
    fun safeWatermark_returnsPass() {
        val result = BrandComplianceScorer.evaluate(
            watermarkRect = safeRect,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            faces = emptyList(),
            layoutMode = BrandComplianceScorer.LayoutMode.SINGLE
        )

        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.PASS)
        assertThat(result.issues).isEmpty()
        assertThat(result.isPass).isTrue()
    }

    @Test
    fun edgeTooClose_triggersEdgeWarnAndFail() {
        // Mép sát < 1% -> FAIL
        val failRect = BrandComplianceScorer.NormalizedBox(left = 0.005f, top = 0.1f, right = 0.3f, bottom = 0.2f)
        val failResult = BrandComplianceScorer.evaluate(failRect, safeAlpha, safeContrast, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(failResult.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(failResult.issues).contains(BrandComplianceScorer.Issue.EDGE)

        // Mép sát 1%..3% -> WARN
        val warnRect = BrandComplianceScorer.NormalizedBox(left = 0.02f, top = 0.1f, right = 0.3f, bottom = 0.2f)
        val warnResult = BrandComplianceScorer.evaluate(warnRect, safeAlpha, safeContrast, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(warnResult.level).isEqualTo(BrandComplianceScorer.Level.WARN)
        assertThat(warnResult.issues).contains(BrandComplianceScorer.Issue.EDGE)
    }

    @Test
    fun lowOpacity_triggersLowOpacityWarnAndFail() {
        // Alpha < 60 -> FAIL
        val failResult = BrandComplianceScorer.evaluate(safeRect, alpha = 50, safeContrast, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(failResult.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(failResult.issues).contains(BrandComplianceScorer.Issue.LOW_OPACITY)

        // Alpha 60..100 -> WARN
        val warnResult = BrandComplianceScorer.evaluate(safeRect, alpha = 80, safeContrast, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(warnResult.level).isEqualTo(BrandComplianceScorer.Level.WARN)
        assertThat(warnResult.issues).contains(BrandComplianceScorer.Issue.LOW_OPACITY)
    }

    @Test
    fun lowContrast_triggersLowContrastWarnAndFail() {
        // Tương phản < 1.5 -> FAIL
        val failResult = BrandComplianceScorer.evaluate(safeRect, safeAlpha, contrastRatio = 1.2, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(failResult.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(failResult.issues).contains(BrandComplianceScorer.Issue.LOW_CONTRAST)

        // Tương phản 1.5..3.0 -> WARN
        val warnResult = BrandComplianceScorer.evaluate(safeRect, safeAlpha, contrastRatio = 2.4, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(warnResult.level).isEqualTo(BrandComplianceScorer.Level.WARN)
        assertThat(warnResult.issues).contains(BrandComplianceScorer.Issue.LOW_CONTRAST)
    }

    @Test
    fun faceOverlap_triggersCoversFaceWarnAndFail() {
        val face = BrandComplianceScorer.NormalizedBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.4f) // diện tích 0.04
        val faces = listOf(face)

        // Watermark đè > 15% mặt -> FAIL
        val failOverlapWm = BrandComplianceScorer.NormalizedBox(left = 0.2f, top = 0.2f, right = 0.35f, bottom = 0.35f) // giao 0.0225 > 50%
        val failResult = BrandComplianceScorer.evaluate(failOverlapWm, safeAlpha, safeContrast, faces, BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(failResult.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(failResult.issues).contains(BrandComplianceScorer.Issue.COVERS_FACE)

        // Watermark đè 7.5% mặt -> WARN
        val warnOverlapWm = BrandComplianceScorer.NormalizedBox(left = 0.15f, top = 0.15f, right = 0.26f, bottom = 0.25f)
        val warnResult = BrandComplianceScorer.evaluate(warnOverlapWm, safeAlpha, safeContrast, faces, BrandComplianceScorer.LayoutMode.SINGLE)
        assertThat(warnResult.level).isEqualTo(BrandComplianceScorer.Level.WARN)
        assertThat(warnResult.issues).contains(BrandComplianceScorer.Issue.COVERS_FACE)
    }

    @Test
    fun sizeOutsideRange_triggersWarnAndFail() {
        val tinyWarn = BrandComplianceScorer.NormalizedBox(left = 0.1f, top = 0.1f, right = 0.2f, bottom = 0.15f)
        val tinyFail = BrandComplianceScorer.NormalizedBox(left = 0.1f, top = 0.1f, right = 0.12f, bottom = 0.12f)

        val warnResult = BrandComplianceScorer.evaluate(
            tinyWarn,
            safeAlpha,
            safeContrast,
            emptyList(),
            BrandComplianceScorer.LayoutMode.SINGLE
        )
        val failResult = BrandComplianceScorer.evaluate(
            tinyFail,
            safeAlpha,
            safeContrast,
            emptyList(),
            BrandComplianceScorer.LayoutMode.SINGLE
        )

        assertThat(warnResult.level).isEqualTo(BrandComplianceScorer.Level.WARN)
        assertThat(warnResult.issues).contains(BrandComplianceScorer.Issue.SIZE_OUT_OF_RANGE)
        assertThat(failResult.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(failResult.issues).contains(BrandComplianceScorer.Issue.SIZE_OUT_OF_RANGE)
    }

    @Test
    fun repeatTileMode_ignoresEdgeAndFaceRules_checksOnlyOpacityAndContrast() {
        // Watermark sát mép và đè mặt nhưng là REPEAT mode -> không bị coi là lỗi mép/mặt
        val face = BrandComplianceScorer.NormalizedBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.4f)
        val edgeWm = BrandComplianceScorer.NormalizedBox(left = 0.001f, top = 0.001f, right = 0.3f, bottom = 0.3f)

        val result = BrandComplianceScorer.evaluate(
            watermarkRect = edgeWm,
            alpha = safeAlpha,
            contrastRatio = safeContrast,
            faces = listOf(face),
            layoutMode = BrandComplianceScorer.LayoutMode.TILED
        )

        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.PASS)
        assertThat(result.issues).doesNotContain(BrandComplianceScorer.Issue.EDGE)
        assertThat(result.issues).doesNotContain(BrandComplianceScorer.Issue.COVERS_FACE)
    }

    @Test
    fun multipleIssues_picksWorstSeverity() {
        // Có 1 WARN (alpha=80) và 1 FAIL (edge sát mép <1%) -> tổng thể là FAIL
        val badEdge = BrandComplianceScorer.NormalizedBox(left = 0.005f, top = 0.1f, right = 0.3f, bottom = 0.2f)
        val result = BrandComplianceScorer.evaluate(badEdge, alpha = 80, safeContrast, emptyList(), BrandComplianceScorer.LayoutMode.SINGLE)

        assertThat(result.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(result.issues).containsExactly(BrandComplianceScorer.Issue.LOW_OPACITY, BrandComplianceScorer.Issue.EDGE)
    }
}
