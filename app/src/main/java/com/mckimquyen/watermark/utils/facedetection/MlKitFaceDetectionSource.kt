package com.mckimquyen.watermark.utils.facedetection

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import javax.inject.Inject
import javax.inject.Singleton

/**
 * IDEA-01: implementation thật dùng ML Kit Face Detection (model BUNDLED trong APK — hoạt động
 * offline hoàn toàn từ lần đầu, khớp tagline "Offline Protection" của app, xem
 * `com.google.mlkit:face-detection` trong `settings.gradle.kts`).
 *
 * `PERFORMANCE_MODE_FAST` — chỉ cần bounding box để tính vùng né, không cần contour/landmark/
 * classification (mở mắt, cười...) nên bỏ hết để giảm thời gian xử lý batch.
 *
 * Giữ 1 [com.google.mlkit.vision.face.FaceDetector] duy nhất cho vòng đời `@Singleton` (đúng
 * khuyến nghị ML Kit — tạo lại mỗi lần gọi tốn overhead load native resource không cần thiết cho
 * batch nhiều ảnh).
 */
@Singleton
class MlKitFaceDetectionSource @Inject constructor() : FaceDetectionSource {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    override suspend fun detectFaces(bitmap: Bitmap): List<RectF> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(inputImage).awaitTask()
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (width <= 0f || height <= 0f) return emptyList()
        return faces.map { face ->
            val box = face.boundingBox
            RectF(
                (box.left / width).coerceIn(0f, 1f),
                (box.top / height).coerceIn(0f, 1f),
                (box.right / width).coerceIn(0f, 1f),
                (box.bottom / height).coerceIn(0f, 1f)
            )
        }
    }
}
