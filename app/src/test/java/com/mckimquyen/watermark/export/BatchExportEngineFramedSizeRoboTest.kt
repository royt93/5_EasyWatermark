package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ExifFrameStyle
import com.mckimquyen.watermark.data.model.ExifModel
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import com.mckimquyen.watermark.utils.bitmap.CardFrameRenderer
import com.mckimquyen.watermark.utils.bitmap.ExifBorderRenderer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * FEAT-28: ước tính kích thước grid preview phải khớp bitmap export thật. Test so sánh hàm thuần
 * ([ExifBorderRenderer.expandedSize], [BatchExportEngine.resolveFramedSize]) với bitmap do renderer
 * dựng ra — nếu ai đổi công thức khung mà quên cập nhật hàm thuần, test này đỏ.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchExportEngineFramedSizeRoboTest {

    private companion object {
        const val SRC_W = 300
        const val SRC_H = 200
        const val CORNER = 0.06f
        const val SHADOW = 0.03f
        const val CUSTOM_BAND = 0.2f
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // WaterMark không có default cho field bắt buộc — lấy config mặc định thật từ repo rồi copy().
    private val base: WaterMark = runBlocking {
        WaterMarkRepository(context, newTestWaterMarkDataStore(context)).waterMark.first()
    }

    private val exif = ExifModel(make = "Leica", model = "Q3", iso = "100")

    private fun source(): Bitmap = Bitmap.createBitmap(SRC_W, SRC_H, Bitmap.Config.ARGB_8888)

    @Test
    fun expandedSize_matchesRealBitmap_forEveryStyleAndThickness() {
        for (style in ExifFrameStyle.entries) {
            for (thickness in listOf(null, CUSTOM_BAND)) {
                val real = ExifBorderRenderer.buildExifBorderBitmap(
                    source = source(),
                    eModel = exif,
                    style = style,
                    bandThicknessPercent = thickness
                )
                val expected = ExifBorderRenderer.expandedSize(SRC_W, SRC_H, style, thickness)
                assertThat(expected).isEqualTo(real.width to real.height)
            }
        }
    }

    @Test
    fun resolveFramedSize_noFrames_returnsOriginal() {
        val size = BatchExportEngine.resolveFramedSize(SRC_W, SRC_H, base, hasExif = true)
        assertThat(size).isEqualTo(SRC_W to SRC_H)
    }

    @Test
    fun resolveFramedSize_cardOnly_matchesRealCardBitmap() {
        val config = base.copy(cardFrameEnabled = true, cardCornerRadiusPercent = CORNER, cardShadowPercent = SHADOW)
        val real = CardFrameRenderer.buildCardBitmap(source(), CORNER, SHADOW, 0)
        val size = BatchExportEngine.resolveFramedSize(SRC_W, SRC_H, config, hasExif = false)
        assertThat(size).isEqualTo(real.width to real.height)
    }

    @Test
    fun resolveFramedSize_exifThenCard_matchesChainedRealBitmaps() {
        val config = base.copy(
            enableExif = true,
            exifFrameStyle = ExifFrameStyle.POLAROID.ordinal,
            cardFrameEnabled = true,
            cardCornerRadiusPercent = CORNER,
            cardShadowPercent = SHADOW
        )
        val withExif = ExifBorderRenderer.buildExifBorderBitmap(source(), exif, ExifFrameStyle.POLAROID)
        val real = CardFrameRenderer.buildCardBitmap(withExif, CORNER, SHADOW, 0)
        val size = BatchExportEngine.resolveFramedSize(SRC_W, SRC_H, config, hasExif = true)
        assertThat(size).isEqualTo(real.width to real.height)
    }

    @Test
    fun resolveFramedSize_exifEnabledButImageHasNoExif_skipsExifFrame() {
        val config = base.copy(enableExif = true)
        val size = BatchExportEngine.resolveFramedSize(SRC_W, SRC_H, config, hasExif = false)
        assertThat(size).isEqualTo(SRC_W to SRC_H)
    }
}
