package com.mckimquyen.watermark.utils.redaction

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.utils.bitmap.applyRedaction
import com.mckimquyen.watermark.utils.textdetection.MlKitSensitiveTextSource
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IDEA-14 — test đầu-cuối chứng minh việc che THẬT SỰ hiệu quả, không chỉ vẽ mờ hình thức: phát
 * hiện vùng email bằng ML Kit thật → mosaic hoá → chạy LẠI chính ML Kit trên ảnh đã che → phải
 * KHÔNG còn đọc ra được email nữa (ngược lại thì tính năng vô nghĩa về mặt bảo vệ riêng tư).
 */
@RunWith(AndroidJUnit4::class)
class RedactionPipelineIntegrationTest {

    private val textSource = MlKitSensitiveTextSource()

    private fun bitmapWithEmail(text: String = "roy.studio@gmail.com"): Bitmap {
        // Cùng chuỗi/kích thước đã xác nhận ổn định trong MlKitSensitiveTextSourceIntegrationTest
        // — tránh chuỗi dài tự nghĩ ra có thể vượt biên canvas hoặc bị OCR đọc nhầm ký tự lạ.
        val bitmap = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText(text, 20f, 100f, paint)
        return bitmap
    }

    @Test
    fun sauKhiChe_docLaiKhongConThayEmailNua() = runBlocking {
        val original = bitmapWithEmail()
        val detected = textSource.detectSensitiveRegions(original)
        assertThat(detected).isNotEmpty()

        val redacted = applyRedaction(original, detected)
        try {
            val detectedAfterRedaction = textSource.detectSensitiveRegions(redacted)
            assertThat(detectedAfterRedaction).isEmpty()
        } finally {
            if (redacted !== original) redacted.recycle()
            original.recycle()
        }
    }

    @Test
    fun vungNgoaiEmail_giuNguyenPixel_khongBiMosaicLay() = runBlocking {
        // Vẽ thêm 1 dòng chữ thường KHÔNG nhạy cảm phía dưới, xác nhận vùng đó giữ NGUYÊN PIXEL —
        // bằng chứng mạnh hơn "đọc lại không ra gì" (vốn đã đúng cả trước khi che).
        val original = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(original)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        canvas.drawText("roy.studio@gmail.com", 20f, 100f, paint)
        canvas.drawText("Watermark Creator", 20f, 300f, paint)

        val detected = textSource.detectSensitiveRegions(original)
        assertThat(detected).hasSize(1)

        // Chụp lại pixel vùng dòng dưới ("Watermark Creator") TRƯỚC khi che.
        val untouchedRegionPixelsBefore = IntArray(1000 * 60)
        original.getPixels(untouchedRegionPixelsBefore, 0, 1000, 0, 270, 1000, 60)

        val redacted = applyRedaction(original, detected)
        try {
            val untouchedRegionPixelsAfter = IntArray(1000 * 60)
            redacted.getPixels(untouchedRegionPixelsAfter, 0, 1000, 0, 270, 1000, 60)
            assertThat(untouchedRegionPixelsAfter).isEqualTo(untouchedRegionPixelsBefore)
        } finally {
            if (redacted !== original) redacted.recycle()
            original.recycle()
        }
    }
}
