package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** FEAT-28: kiểm pixel thật của [CardFrameRenderer.buildCardBitmap] (native graphics, không stub). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardFrameRendererRoboTest {

    private companion object {
        const val SRC_W = 200
        const val SRC_H = 120
        val PHOTO = Color.rgb(200, 30, 30)
        val BG = Color.rgb(250, 250, 250)
    }

    private fun source(): Bitmap = Bitmap.createBitmap(SRC_W, SRC_H, Bitmap.Config.ARGB_8888).apply { eraseColor(PHOTO) }

    @Test
    fun output_sizeMatchesComputedLayout() {
        val src = source()
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0.1f, 0.03f)
        val out = CardFrameRenderer.buildCardBitmap(src, 0.1f, 0.03f, BG)
        assertThat(out.width).isEqualTo(l.canvasWidth)
        assertThat(out.height).isEqualTo(l.canvasHeight)
    }

    @Test
    fun center_keepsOriginalPhotoColor() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.1f, 0.03f, BG)
        assertThat(out.getPixel(out.width / 2, out.height / 2)).isEqualTo(PHOTO)
    }

    @Test
    fun farCorner_isBackgroundColor() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.1f, 0f, BG)
        assertThat(out.getPixel(0, 0)).isEqualTo(BG)
        assertThat(out.getPixel(out.width - 1, out.height - 1)).isEqualTo(BG)
    }

    @Test
    fun roundedCorner_photoCornerPixelBecomesBackground_notPhoto() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.4f, 0f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0.4f, 0f)
        // pixel sát đỉnh góc ảnh gốc phải bị bo mất → không còn là màu ảnh
        assertThat(out.getPixel(l.padding, l.padding)).isNotEqualTo(PHOTO)
    }

    @Test
    fun zeroCorner_photoCornerPixelStaysPhoto() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0f, 0f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0f, 0f)
        assertThat(out.getPixel(l.padding + 1, l.padding + 1)).isEqualTo(PHOTO)
    }

    @Test
    fun shadow_darkensBackgroundBelowPhoto_comparedToNoShadow() {
        val withShadow = CardFrameRenderer.buildCardBitmap(source(), 0.06f, 0.08f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0.06f, 0.08f)
        val belowPhoto = withShadow.getPixel(withShadow.width / 2, l.padding + SRC_H + 2)
        assertThat(Color.red(belowPhoto)).isLessThan(Color.red(BG))
    }

    @Test
    fun source_isNotRecycledByRenderer() {
        val src = source()
        CardFrameRenderer.buildCardBitmap(src, 0.1f, 0.03f, BG)
        assertThat(src.isRecycled).isFalse()
    }

    @Test
    fun customBackgroundColor_isUsed() {
        val blue = Color.rgb(10, 20, 220)
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.06f, 0f, blue)
        assertThat(out.getPixel(0, 0)).isEqualTo(blue)
    }
}
