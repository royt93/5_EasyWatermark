package com.mckimquyen.watermark.ui.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Shader
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.TextPaintStyle
import com.mckimquyen.watermark.data.model.TextTypeface
import com.mckimquyen.watermark.data.model.WaterMark
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-39: [WaterMarkImageView.resolveAutoContrast] — hàm dùng CHUNG cho preview editor lẫn
 * `BatchExportEngine` (trước đây chỉ preview editor tự làm việc này riêng, export bỏ qua).
 *
 * CHỈ test phần GUARD (bật/tắt, markMode, bitmap recycled) + hiệu ứng KHÔNG phụ thuộc màu Palette
 * thật (sàn alpha) — Palette trên bitmap solid-color trong Robolectric LUÔN rơi vào fallback
 * `Color.GRAY` (đã verify qua probe riêng, không rasterize pixel thật giống lý do đã ghi ở
 * `WaterMarkImageViewAutoContrastRoboTest`), nên phần "đổi màu chữ đúng theo nền sáng/tối" chỉ
 * verify được qua smoke test thật trên device (xem "Kết quả kiểm chứng" BUG-39 trong
 * `doc/task/done/`).
 */
@RunWith(RobolectricTestRunner::class)
class WaterMarkImageViewResolveAutoContrastRoboTest {

    private fun config(
        autoContrastEnabled: Boolean = true,
        markMode: WaterMarkRepository.MarkMode = WaterMarkRepository.MarkMode.Text,
        alpha: Int = 40
    ) = WaterMark(
        text = "Watermark",
        textSize = 40f,
        textColor = Color.RED,
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

    private fun solidBitmap(color: Int = Color.WHITE): Bitmap =
        Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun disabled_returnsConfigUnchanged() = runBlocking {
        val original = config(autoContrastEnabled = false)

        val result = WaterMarkImageView.resolveAutoContrast(
            bitmap = solidBitmap(),
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.1f,
            offsetY = 0.1f,
            textSizeInBitmapPx = 40f,
            config = original
        )

        assertThat(result).isSameInstanceAs(original)
    }

    @Test
    fun imageMode_returnsConfigUnchanged() = runBlocking {
        val original = config(markMode = WaterMarkRepository.MarkMode.Image)

        val result = WaterMarkImageView.resolveAutoContrast(
            bitmap = solidBitmap(),
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.1f,
            offsetY = 0.1f,
            textSizeInBitmapPx = 40f,
            config = original
        )

        assertThat(result).isSameInstanceAs(original)
    }

    @Test
    fun recycledBitmap_returnsConfigUnchanged_khongCrash() = runBlocking {
        val original = config()
        val bitmap = solidBitmap().apply { recycle() }

        val result = WaterMarkImageView.resolveAutoContrast(
            bitmap = bitmap,
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.1f,
            offsetY = 0.1f,
            textSizeInBitmapPx = 40f,
            config = original
        )

        assertThat(result).isSameInstanceAs(original)
    }

    @Test
    fun enabledValidCase_alphaLuonDatSanToiThieu() = runBlocking {
        val original = config(alpha = 10)

        val result = WaterMarkImageView.resolveAutoContrast(
            bitmap = solidBitmap(),
            tileMode = Shader.TileMode.CLAMP,
            offsetX = 0.1f,
            offsetY = 0.1f,
            textSizeInBitmapPx = 40f,
            config = original
        )

        // Không early-return (guard đều pass) — đã thực sự đi qua nhánh áp dụng, sàn alpha không
        // phụ thuộc màu Palette trả về thật hay fallback (xem doc lớp), luôn verify được.
        assertThat(result.alpha).isEqualTo(WaterMarkImageView.readableAlpha(original.alpha))
        assertThat(result.alpha).isAtLeast(WaterMarkImageView.AUTO_CONTRAST_MIN_ALPHA)
    }

    @Test
    fun repeatTileMode_khongCrash_vanTraVeConfigHopLe() = runBlocking {
        val original = config()

        val result = WaterMarkImageView.resolveAutoContrast(
            bitmap = solidBitmap(Color.BLACK),
            tileMode = Shader.TileMode.REPEAT,
            offsetX = 0.5f,
            offsetY = 0.5f,
            textSizeInBitmapPx = 40f,
            config = original
        )

        assertThat(result.alpha).isAtLeast(WaterMarkImageView.AUTO_CONTRAST_MIN_ALPHA)
    }
}
