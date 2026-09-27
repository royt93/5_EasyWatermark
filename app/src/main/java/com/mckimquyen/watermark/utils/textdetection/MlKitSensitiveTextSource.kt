package com.mckimquyen.watermark.utils.textdetection

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mckimquyen.watermark.utils.facedetection.awaitTask
import com.mckimquyen.watermark.utils.redaction.SensitivePatternMatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IDEA-14: implementation thật dùng ML Kit Text Recognition (model BUNDLED trong APK — hoạt động
 * offline hoàn toàn từ lần đầu, cùng lựa chọn với `MlKitFaceDetectionSource` của IDEA-01, xem
 * `com.google.mlkit:text-recognition` trong `settings.gradle.kts`).
 *
 * Duyệt theo `TextBlock.lines` (dòng) TRƯỚC — email/SĐT thường gọn trong 1 dòng, khoanh theo cả
 * block (nhiều dòng) dễ che lố sang nội dung xung quanh không nhạy cảm.
 *
 * **Fallback theo block khi không dòng nào khớp riêng lẻ**: xác nhận qua smoke test thật trên màn
 * hình hẹp (720px) — email dài bị UI TỰ NGẮT DÒNG giữa chừng (vd "gokusaiyan6" / "996@gmail" /
 * ".com" thành 3 dòng riêng), không dòng nào tự nó là email hợp lệ nên bị bỏ sót hoàn toàn dù mắt
 * người đọc ra ngay. Khi không có dòng nào khớp, nối toàn bộ text trong CÙNG BLOCK (bỏ dấu xuống
 * dòng) rồi thử lại — ML Kit gom các dòng liền kề cùng đoạn vào 1 block, nên ghép lại tái tạo đúng
 * chuỗi gốc trước khi bị UI ngắt.
 *
 * Giữ 1 [com.google.mlkit.vision.text.TextRecognizer] duy nhất cho vòng đời `@Singleton` — cùng lý
 * do với `MlKitFaceDetectionSource` (tránh overhead tạo lại native resource mỗi lần gọi).
 */
@Singleton
class MlKitSensitiveTextSource @Inject constructor() : SensitiveTextSource {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun detectSensitiveRegions(bitmap: Bitmap): List<RectF> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(inputImage).awaitTask()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (width <= 0f || height <= 0f) return emptyList()

        return result.textBlocks.flatMap { block -> sensitiveBoxesInBlock(block) }
            .mapNotNull { box -> box.toNormalizedRectF(width, height) }
    }

    private fun sensitiveBoxesInBlock(block: Text.TextBlock): List<Rect> {
        val lineMatches = block.lines.mapNotNull { line ->
            line.boundingBox.takeIf { SensitivePatternMatcher.isSensitive(line.text) }
        }
        if (lineMatches.isNotEmpty()) return lineMatches

        // Không dòng nào tự nó khớp — thử ghép cả block (xem doc-comment lớp trên).
        val joinedText = block.lines.joinToString(separator = "") { it.text }
        return if (SensitivePatternMatcher.isSensitive(joinedText)) {
            listOfNotNull(block.boundingBox)
        } else {
            emptyList()
        }
    }

    private fun Rect.toNormalizedRectF(width: Float, height: Float): RectF = RectF(
        (left / width).coerceIn(0f, 1f),
        (top / height).coerceIn(0f, 1f),
        (right / width).coerceIn(0f, 1f),
        (bottom / height).coerceIn(0f, 1f)
    )
}
