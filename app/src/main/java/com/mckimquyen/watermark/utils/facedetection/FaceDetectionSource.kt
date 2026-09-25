package com.mckimquyen.watermark.utils.facedetection

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IDEA-01: nguồn phát hiện khuôn mặt trong 1 bitmap. Tách interface vì implementation thật (ML
 * Kit) cần native lib thật, không mô phỏng tin cậy được trong Robolectric — logic điều phối
 * ([com.mckimquyen.watermark.export.AutoPlacementEngine]) test bằng fake implementation của
 * interface này, bản thân ML Kit thật verify qua androidTest/smoke test riêng.
 */
interface FaceDetectionSource {
    /** @return toạ độ mặt chuẩn hoá 0..1 theo kích thước [bitmap]; rỗng nếu không tìm thấy mặt nào. */
    suspend fun detectFaces(bitmap: Bitmap): List<RectF>
}

/** Cầu nối [Task] (ML Kit/Play Services) sang coroutine — tránh thêm nguyên dependency `kotlinx-coroutines-play-services` chỉ để có `.await()`. */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
