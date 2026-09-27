package com.mckimquyen.watermark.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.bitmap.OutputImageUtils
import com.mckimquyen.watermark.utils.bitmap.applyCropAndRotate
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * IDEA-09 integration test trên thiết bị thật — Robolectric KHÔNG rasterize pixel nên chuỗi biến đổi
 * crop → downscale → nén JPEG → decode lại chỉ kiểm chứng được ở đây (Skia thật).
 *
 * Test đi đúng chuỗi hàm mà `BatchExportEngine.generateSurvivabilityPreview()` dùng:
 * [SurvivabilityProfile.centerCropBox] → [applyCropAndRotate] → [OutputImageUtils.resizeIfNeeded]
 * → `Bitmap.compress(JPEG)` + `decodeByteArray`.
 */
@RunWith(AndroidJUnit4::class)
class SurvivabilityTransformIntegrationTest {

    private val instagram = SurvivabilityProfile.profiles.first {
        it.platform == SurvivabilityProfile.Platform.INSTAGRAM
    }

    /** Ảnh ngang có vạch đỏ ở mép phải — đóng vai watermark đặt sát góc. */
    private fun wideBitmapWithRightEdgeMark(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(
            width * 0.9f,
            height * 0.4f,
            width.toFloat(),
            height * 0.6f,
            Paint().apply { color = Color.RED }
        )
        return bitmap
    }

    private fun recompressJpeg(bitmap: Bitmap, quality: Int): Bitmap {
        val stream = ByteArrayOutputStream()
        assertThat(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)).isTrue()
        val bytes = stream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Test
    fun cropTheoTiLeInstagram_choRaAnhVuongVaCatMatVungSatMep() {
        val source = wideBitmapWithRightEdgeMark(800, 400)
        val box = SurvivabilityProfile.centerCropBox(source.width, source.height, instagram.cropAspect!!)
        val cropped = applyCropAndRotate(source, 0f, RectF(box.left, box.top, box.right, box.bottom))

        // 800x400 crop về 1:1 → vuông 400x400, chỉ giữ khoảng giữa theo trục ngang.
        assertThat(cropped.width).isEqualTo(cropped.height)
        assertThat(cropped.width).isEqualTo(400)

        // Vạch đỏ nằm ở 90..100% chiều ngang ảnh gốc → nằm ngoài vùng crop 25..75%, phải mất hẳn.
        val midY = cropped.height / 2
        val rightMostPixel = cropped.getPixel(cropped.width - 1, midY)
        assertThat(rightMostPixel).isEqualTo(Color.WHITE)

        source.recycle()
        cropped.recycle()
    }

    @Test
    fun downscaleVaNenJpeg_traVeAnhDungKichThuocVaDecodeLaiDuoc() {
        val source = wideBitmapWithRightEdgeMark(4000, 3000)
        val resized = OutputImageUtils.resizeIfNeeded(source, instagram.maxLongEdge)
        assertThat(maxOf(resized.width, resized.height)).isEqualTo(instagram.maxLongEdge)
        // Giữ đúng tỉ lệ 4:3 của ảnh gốc.
        assertThat(resized.height).isEqualTo(instagram.maxLongEdge * 3 / 4)

        val recompressed = recompressJpeg(resized, instagram.jpegQuality)
        assertThat(recompressed).isNotNull()
        assertThat(recompressed.isRecycled).isFalse()
        assertThat(recompressed.width).isEqualTo(resized.width)
        assertThat(recompressed.height).isEqualTo(resized.height)

        source.recycle()
        if (resized !== source) resized.recycle()
        recompressed.recycle()
    }

    @Test
    fun watermarkSatMepPhai_biChamDiemFailTrenInstagram() {
        val source = wideBitmapWithRightEdgeMark(4000, 2000)
        // Đúng vị trí vạch đỏ đã vẽ: 90..100% ngang, 40..60% dọc.
        val watermarkRect = BrandComplianceScorer.NormalizedBox(0.9f, 0.4f, 1f, 0.6f)

        val score = SurvivabilityProfile.evaluate(
            watermarkRect = watermarkRect,
            alpha = 255,
            contrastRatio = 10.0,
            profile = instagram,
            originalWidth = source.width,
            originalHeight = source.height
        )

        assertThat(score.level).isEqualTo(BrandComplianceScorer.Level.FAIL)
        assertThat(score.issues).contains(SurvivabilityProfile.Issue.CROPPED_OUT)
        assertThat(score.suggestions).contains(SurvivabilityProfile.Suggestion.MOVE_TOWARD_CENTER)

        source.recycle()
    }
}
