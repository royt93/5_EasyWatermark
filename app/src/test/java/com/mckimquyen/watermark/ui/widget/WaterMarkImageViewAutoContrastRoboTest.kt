package com.mckimquyen.watermark.ui.widget

import android.graphics.Color
import android.graphics.Shader
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-06: các hàm thuần quyết định VÙNG lấy mẫu độ sáng + sàn alpha + điều kiện áp dụng
 * auto-contrast — tách riêng khỏi phần build [androidx.palette.graphics.Palette] thật (chỉ verify
 * qua smoke test thật trên device, Robolectric không mô phỏng đúng phân tích pixel thật, xem
 * `TextEffectRendererTest` cho lý do tương tự với `Color`/`ColorUtils`).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewAutoContrastRoboTest {

    private fun config(
        autoContrastEnabled: Boolean = true,
        markMode: WaterMarkRepository.MarkMode = WaterMarkRepository.MarkMode.Text,
        alpha: Int = 255
    ) = WaterMark(
        text = "Watermark",
        textSize = 40f,
        textColor = Color.WHITE,
        textStyle = TextPaintStyle.Fill,
        textTypeface = TextTypeface.Normal,
        alpha = alpha,
        degree = 0f,
        hGap = 0,
        vGap = 0,
        iconUri = Uri.EMPTY,
        markMode = markMode,
        enableBounds = false,
        autoContrastEnabled = autoContrastEnabled
    )

    // ---- shouldApplyAutoContrast ----

    @Test
    fun shouldApply_enabledAndTextMode_true() {
        assertThat(WaterMarkImageView.shouldApplyAutoContrast(config())).isTrue()
    }

    @Test
    fun shouldApply_disabled_false() {
        assertThat(WaterMarkImageView.shouldApplyAutoContrast(config(autoContrastEnabled = false))).isFalse()
    }

    @Test
    fun shouldApply_enabledButImageMode_false() {
        assertThat(
            WaterMarkImageView.shouldApplyAutoContrast(config(markMode = WaterMarkRepository.MarkMode.Image))
        ).isFalse()
    }

    // ---- readableAlpha ----

    @Test
    fun readableAlpha_belowFloor_raisedToFloor() {
        assertThat(WaterMarkImageView.readableAlpha(40)).isEqualTo(WaterMarkImageView.AUTO_CONTRAST_MIN_ALPHA)
    }

    @Test
    fun readableAlpha_atOrAboveFloor_unchanged() {
        assertThat(WaterMarkImageView.readableAlpha(255)).isEqualTo(255)
        assertThat(WaterMarkImageView.readableAlpha(WaterMarkImageView.AUTO_CONTRAST_MIN_ALPHA)).isEqualTo(WaterMarkImageView.AUTO_CONTRAST_MIN_ALPHA)
    }

    // ---- computeAutoContrastSampleRegion ----

    @Test
    fun sampleRegion_repeatTileMode_ignoresOffset_usesWholeBitmap() {
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.REPEAT,
            offsetX = 0.9f,
            offsetY = 0.1f,
            sampleSizePx = 50,
            bitmapWidth = 1000,
            bitmapHeight = 800
        )

        assertThat(region.left).isEqualTo(0)
        assertThat(region.top).isEqualTo(0)
        assertThat(region.right).isEqualTo(1000)
        assertThat(region.bottom).isEqualTo(800)
    }

    @Test
    fun sampleRegion_mirrorTileMode_alsoUsesWholeBitmap() {
        // Codebase chỉ phân biệt CLAMP vs "khác CLAMP" cho mọi logic vị trí (xem applyAnchor,
        // updateWaterMarkOffset) — MIRROR/DECAL đi theo cùng nhánh với REPEAT.
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.MIRROR,
            offsetX = 0.5f,
            offsetY = 0.5f,
            sampleSizePx = 50,
            bitmapWidth = 640,
            bitmapHeight = 480
        )

        assertThat(region.left).isEqualTo(0)
        assertThat(region.top).isEqualTo(0)
        assertThat(region.right).isEqualTo(640)
        assertThat(region.bottom).isEqualTo(480)
    }

    @Test
    fun sampleRegion_clampTileMode_topLeftOffset_boxAtOrigin() {
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0f,
            offsetY = 0f,
            sampleSizePx = 50,
            bitmapWidth = 1000,
            bitmapHeight = 800
        )

        assertThat(region.left).isEqualTo(0)
        assertThat(region.top).isEqualTo(0)
        assertThat(region.right).isEqualTo(50)
        assertThat(region.bottom).isEqualTo(50)
        assertThat(region.isValid).isTrue()
    }

    @Test
    fun sampleRegion_clampTileMode_centerOffset_boxCenteredAtFraction() {
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.5f,
            offsetY = 0.25f,
            sampleSizePx = 100,
            bitmapWidth = 1000,
            bitmapHeight = 800
        )

        assertThat(region.left).isEqualTo(500)
        assertThat(region.top).isEqualTo(200)
        assertThat(region.right).isEqualTo(600)
        assertThat(region.bottom).isEqualTo(300)
    }

    @Test
    fun sampleRegion_clampTileMode_nearEdgeOffset_clampedInsideBitmap_stillValid() {
        // offset gần mép (0.99) + sampleSize lớn không được vượt biên bitmap, và vùng vẫn phải
        // hợp lệ (right>left, bottom>top) — không được co về 0 khiến auto-contrast âm thầm bị bỏ qua.
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.99f,
            offsetY = 0.99f,
            sampleSizePx = 500,
            bitmapWidth = 1000,
            bitmapHeight = 800
        )

        assertThat(region.right).isAtMost(1000)
        assertThat(region.bottom).isAtMost(800)
        assertThat(region.isValid).isTrue()
    }

    @Test
    fun sampleRegion_invalidBitmapSize_returnsInvalidRegion() {
        val region = WaterMarkImageView.computeAutoContrastSampleRegion(
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.5f,
            offsetY = 0.5f,
            sampleSizePx = 50,
            bitmapWidth = 0,
            bitmapHeight = 800
        )

        assertThat(region.isValid).isFalse()
    }
}
