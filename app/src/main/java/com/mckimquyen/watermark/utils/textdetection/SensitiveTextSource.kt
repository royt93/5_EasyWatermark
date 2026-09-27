package com.mckimquyen.watermark.utils.textdetection

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * IDEA-14: nguồn phát hiện vùng TEXT NHẠY CẢM (email/SĐT) trong 1 bitmap. Tách interface theo đúng
 * khuôn [com.mckimquyen.watermark.utils.facedetection.FaceDetectionSource] — implementation thật
 * (ML Kit) cần native lib, không mô phỏng tin cậy trong Robolectric; logic điều phối test bằng fake
 * implementation của interface này, ML Kit thật verify qua androidTest/smoke test riêng.
 */
interface SensitiveTextSource {
    /**
     * @return toạ độ chuẩn hoá 0..1 theo kích thước [bitmap] của các DÒNG text bị coi là nhạy cảm
     * (đã lọc qua [com.mckimquyen.watermark.utils.redaction.SensitivePatternMatcher]) — KHÔNG phải
     * mọi text đọc được. Rỗng nếu không có dòng nào nhạy cảm.
     */
    suspend fun detectSensitiveRegions(bitmap: Bitmap): List<RectF>
}
