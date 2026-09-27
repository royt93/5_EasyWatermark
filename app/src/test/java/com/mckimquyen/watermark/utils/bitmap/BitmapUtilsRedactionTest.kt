package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * IDEA-14: verify hàm [applyRedaction] (mosaic hoá từng vùng nhạy cảm) — chạy Robolectric vì cần
 * tạo bitmap thật và đọc pixel.
 */
@RunWith(RobolectricTestRunner::class)
class BitmapUtilsRedactionTest {

    /**
     * Gradient ngang dựng bằng [Bitmap.setPixel] trực tiếp (không qua `Canvas.drawLine`) — tránh
     * phụ thuộc rasterize/anti-alias của Skia, đảm bảo mỗi cột `x` có đúng 1 màu xác định trước.
     */
    private fun sampleBitmap(width: Int = 100, height: Int = 100): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bmp.setPixel(x, y, Color.rgb(x * 255 / width, 0, 0))
            }
        }
        return bmp
    }

    @Test
    fun `danh sach rects null tra ve CHINH bitmap nguon khong tao ban sao thua`() {
        val src = sampleBitmap()
        val result = applyRedaction(src, null)
        assertThat(result).isSameInstanceAs(src)
    }

    @Test
    fun `danh sach rects rong tra ve CHINH bitmap nguon`() {
        val src = sampleBitmap()
        val result = applyRedaction(src, emptyList())
        assertThat(result).isSameInstanceAs(src)
    }

    @Test
    fun `co vung che thi tra ve bitmap MOI khac instance nguon`() {
        val src = sampleBitmap()
        val result = applyRedaction(src, listOf(RectF(0.2f, 0.2f, 0.6f, 0.6f)))
        assertThat(result).isNotSameInstanceAs(src)
        assertThat(result.width).isEqualTo(src.width)
        assertThat(result.height).isEqualTo(src.height)
    }

    @Test
    fun `vung ngoai rects duoc giu nguyen pixel goc`() {
        val src = sampleBitmap()
        // Chỉ che góc dưới-phải (0.5..1.0).
        val result = applyRedaction(src, listOf(RectF(0.5f, 0.5f, 1.0f, 1.0f)))

        // Điểm (10, 10) nằm ở góc trên-trái (ngoài vùng che) → pixel phải giống hệt.
        assertThat(result.getPixel(10, 10)).isEqualTo(src.getPixel(10, 10))
    }

    @Test
    fun `vung trong rects bi mosaic hoa - cac pixel canh nhau tro nen giong nhau theo khoi`() {
        val src = sampleBitmap(width = 120, height = 120)
        // src ban đầu: mỗi cột x có màu khác nhau (x=60 khác x=61).
        val originalPixelA = src.getPixel(60, 60)
        val originalPixelB = src.getPixel(61, 60)
        assertThat(originalPixelA).isNotEqualTo(originalPixelB)

        // Che cả vùng 50..80.
        val result = applyRedaction(src, listOf(RectF(50f / 120f, 50f / 120f, 80f / 120f, 80f / 120f)))

        // Sau khi mosaic: 2 pixel nằm cùng 1 khối [MOSAIC_BLOCK] phải CÙNG màu (bị gom cụm).
        val mosaicPixelA = result.getPixel(60, 60)
        val mosaicPixelB = result.getPixel(61, 60)
        assertThat(mosaicPixelA).isEqualTo(mosaicPixelB)
    }

    @Test
    fun `rects vuot bien anh khong nem loi out-of-bounds`() {
        val src = sampleBitmap()
        // Rect vượt ra ngoài [0..1] — hàm phải tự clamp, không crash.
        val result = applyRedaction(src, listOf(RectF(-0.5f, -0.5f, 1.5f, 1.5f)))
        assertThat(result).isNotNull()
    }
}
