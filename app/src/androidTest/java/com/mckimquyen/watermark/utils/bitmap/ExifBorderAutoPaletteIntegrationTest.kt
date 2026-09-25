package com.mckimquyen.watermark.utils.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.palette.graphics.Palette
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-18 integration test (Skia thật trên thiết bị — Robolectric không rasterize pixel): chạy đúng chuỗi
 * export dùng (`Palette.from(bitmap)` → [ExifFramePalette.resolve] → [ExifBorderRenderer.buildExifBorderBitmap])
 * rồi đọc pixel dải nền thật. AC: ảnh tối và ảnh sáng cho khung KHÁC màu, mỗi khung theo tông của ảnh mình.
 */
@RunWith(AndroidJUnit4::class)
class ExifBorderAutoPaletteIntegrationTest {

    private val exif = ExifModel(make = "Canon", model = "EOS R5", iso = "100", fNumber = "f/2.8")
    private val darkNavy = Color.rgb(20, 30, 60)
    private val sunnyYellow = Color.rgb(250, 220, 90)

    private fun solid(color: Int): Bitmap = Bitmap.createBitmap(SOURCE_SIZE, SOURCE_SIZE, Bitmap.Config.ARGB_8888)
        .also { Canvas(it).drawColor(color) }

    private fun renderAutoPalette(source: Bitmap, style: ExifFrameStyle): Bitmap {
        val dominant = Palette.from(source).generate().dominantSwatch?.rgb
        return ExifBorderRenderer.buildExifBorderBitmap(
            source = source,
            eModel = exif,
            style = style,
            frameColors = ExifFramePalette.resolve(dominant, style)
        )
    }

    /** Pixel sát mép trái dải CLASSIC (dưới ảnh gốc) — chữ bắt đầu từ 5% bề ngang nên cột x=1 chỉ có nền. */
    private fun classicBandPixel(framed: Bitmap): Int = framed.getPixel(1, framed.height - 2)

    @Test
    fun darkAndBrightPhotos_getDifferentBandColors_matchingTheirOwnTone() {
        val darkFramed = renderAutoPalette(solid(darkNavy), ExifFrameStyle.CLASSIC)
        val brightFramed = renderAutoPalette(solid(sunnyYellow), ExifFrameStyle.CLASSIC)

        val darkBand = classicBandPixel(darkFramed)
        val brightBand = classicBandPixel(brightFramed)

        assertThat(darkBand).isNotEqualTo(brightBand)
        assertThat(isClose(darkBand, darkNavy)).isTrue()
        assertThat(isClose(brightBand, sunnyYellow)).isTrue()
    }

    @Test
    fun autoPaletteOff_keepsDefaultWhiteClassicBand() {
        val framed = ExifBorderRenderer.buildExifBorderBitmap(solid(darkNavy), exif, ExifFrameStyle.CLASSIC)

        assertThat(classicBandPixel(framed)).isEqualTo(Color.WHITE)
    }

    /** Palette lượng tử hoá màu (5 bit/kênh) nên màu chủ đạo lệch vài đơn vị so với màu tô — chấp nhận sai số nhỏ. */
    private fun isClose(actual: Int, expected: Int): Boolean =
        Math.abs(Color.red(actual) - Color.red(expected)) <= COLOR_TOLERANCE &&
            Math.abs(Color.green(actual) - Color.green(expected)) <= COLOR_TOLERANCE &&
            Math.abs(Color.blue(actual) - Color.blue(expected)) <= COLOR_TOLERANCE

    private companion object {
        const val SOURCE_SIZE = 200
        const val COLOR_TOLERANCE = 12
    }
}
