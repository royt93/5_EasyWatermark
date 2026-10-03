package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-28: pixel thật của [CardFrameRenderer] trên Skia thật (Robolectric không mô phỏng chính xác
 * xfermode/maskFilter). Chứng minh: bo góc cắt thật góc ảnh, bóng làm tối nền phía dưới, tâm giữ
 * nguyên màu ảnh, góc xa cùng màu nền.
 */
@RunWith(AndroidJUnit4::class)
class CardFramePixelIntegrationTest {

    private companion object {
        const val SRC_W = 400
        const val SRC_H = 240
        val PHOTO = Color.rgb(200, 30, 30)
        val BG = Color.rgb(250, 250, 250)
        const val MAX_CHANNEL_DELTA = 3
    }

    private fun source(): Bitmap = Bitmap.createBitmap(SRC_W, SRC_H, Bitmap.Config.ARGB_8888).apply { eraseColor(PHOTO) }

    private fun assertClose(actual: Int, expected: Int) {
        assertThat(Math.abs(Color.red(actual) - Color.red(expected))).isAtMost(MAX_CHANNEL_DELTA)
        assertThat(Math.abs(Color.green(actual) - Color.green(expected))).isAtMost(MAX_CHANNEL_DELTA)
        assertThat(Math.abs(Color.blue(actual) - Color.blue(expected))).isAtMost(MAX_CHANNEL_DELTA)
    }

    @Test
    fun roundedCorner_cutsPhotoCorner_toBackground() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.4f, 0f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0.4f, 0f)
        assertClose(out.getPixel(l.padding, l.padding), BG)
    }

    @Test
    fun noCorner_keepsPhotoCornerSharp() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0f, 0f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0f, 0f)
        assertClose(out.getPixel(l.padding + 1, l.padding + 1), PHOTO)
    }

    @Test
    fun center_isUntouchedPhotoColor() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.2f, 0.05f, BG)
        assertClose(out.getPixel(out.width / 2, out.height / 2), PHOTO)
    }

    @Test
    fun shadow_darkensBackgroundJustBelowPhoto() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.06f, 0.08f, BG)
        val l = CardFrameRenderer.computeLayout(SRC_W, SRC_H, 0.06f, 0.08f)
        val below = out.getPixel(out.width / 2, l.padding + SRC_H + 2)
        assertThat(Color.red(below)).isLessThan(Color.red(BG) - 5)
    }

    @Test
    fun farCorner_isPureBackground() {
        val out = CardFrameRenderer.buildCardBitmap(source(), 0.2f, 0.05f, BG)
        assertClose(out.getPixel(0, 0), BG)
    }
}
