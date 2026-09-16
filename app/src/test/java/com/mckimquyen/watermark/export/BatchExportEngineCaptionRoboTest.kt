package com.mckimquyen.watermark.export

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.model.ImageInfo
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.testutil.newTestWaterMarkDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression cho bug tìm được ở code review (2026-09-16): xoá caption 1 ảnh (để trống, ý định
 * "không watermark ảnh này") KHÔNG skip vẽ ở [BatchExportEngine.generateImage] (khác
 * [BatchExportEngine.generatePreviewBitmap]) — `layoutPaint` (Paint() mặc định màu đen, alpha
 * 255) vẫn bị `canvas.drawRect()` tô kín đè lên ảnh vì shader null. Không assert được bằng ảnh
 * xuất ra thật (Robolectric ở máy build này không rasterize pixel — `getPixel` luôn trả 0 dù đã
 * vẽ, xem `BatchExportEngineCaptionRoboTest` git history) nên test trực tiếp điều kiện quyết định
 * có vẽ hay không, `internal` để test truy cập (xem [BatchExportEngine.shouldSkipTextWatermark]).
 */
@RunWith(RobolectricTestRunner::class)
class BatchExportEngineCaptionRoboTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val engine = BatchExportEngine(context, ExportNaming())

    private fun sharedTextConfig(text: String) = runBlocking {
        val repo = WaterMarkRepository(context, newTestWaterMarkDataStore(context))
        repo.updateText(text)
        repo.waterMark.first()
    }

    @Test
    fun resolveBaseText_captionEmptyString_overridesSharedTextWithEmpty() {
        val config = sharedTextConfig("shared text")
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"), caption = "")

        assertThat(engine.resolveBaseText(imageInfo, config)).isEmpty()
    }

    @Test
    fun resolveBaseText_captionNull_fallsBackToSharedText() {
        val config = sharedTextConfig("shared text")
        val imageInfo = ImageInfo(Uri.parse("content://media/1.jpg"), caption = null)

        assertThat(engine.resolveBaseText(imageInfo, config)).isEqualTo("shared text")
    }

    @Test
    fun shouldSkipTextWatermark_textModeAndBlankCaption_returnsTrue_mustNotDrawOpaqueBlackRect() {
        // Đây chính là case gây bug: caption = "" ghi đè "shared text" (không rỗng) thành baseText
        // rỗng — generateImage() PHẢI skip vẽ (không được canvas.drawRect() với Paint() mặc định
        // màu đen đè lên ảnh gốc), đúng như generatePreviewBitmap() đã làm.
        val baseText = engine.resolveBaseText(ImageInfo(Uri.parse("content://media/1.jpg"), caption = ""), sharedTextConfig("shared text"))

        assertThat(engine.shouldSkipTextWatermark(WaterMarkRepository.MarkMode.Text, baseText)).isTrue()
    }

    @Test
    fun shouldSkipTextWatermark_textModeAndNonBlankText_returnsFalse() {
        assertThat(engine.shouldSkipTextWatermark(WaterMarkRepository.MarkMode.Text, "hello")).isFalse()
    }

    @Test
    fun shouldSkipTextWatermark_imageMode_alwaysReturnsFalse_regardlessOfBlankText() {
        // MarkMode.Image không dùng baseText để quyết định vẽ hay không (build icon shader riêng) —
        // guard chỉ áp dụng cho MarkMode.Text.
        assertThat(engine.shouldSkipTextWatermark(WaterMarkRepository.MarkMode.Image, "")).isFalse()
    }
}
