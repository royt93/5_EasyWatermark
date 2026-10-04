package com.mckimquyen.watermark.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * BUG-55: giữ ĐÚNG 1 job cập nhật preview watermark. Trước đây mỗi lần `waterMark` emit lại launch
 * coroutine mới mà không huỷ coroutine trước; `resolveText` có thể chạy `Dispatchers.IO` (text có
 * token `{...}`) nên coroutine cũ hoàn tất SAU coroutine mới sẽ ghi đè preview bằng config cũ →
 * preview khác kết quả export. Mỗi [update] huỷ job trước, nên chỉ lần gọi MỚI NHẤT được [apply].
 *
 * Tách khỏi `MainActivity` để unit test thuần bằng `runTest`, không cần dựng cả Activity.
 *
 * @param T kiểu ảnh đang chọn (thực tế `ImageInfo`) — generic để unit test không phụ thuộc `android.net.Uri`.
 * @param resolveText resolve token trong text theo ảnh đang chọn (suspend, có thể chuyển dispatcher).
 * @param apply nhận text đã resolve — caller tự ghép vào config rồi gán cho view.
 */
class PreviewConfigUpdater<T : Any>(
    private val scope: CoroutineScope,
    private val resolveText: suspend (text: String, image: T) -> String,
    private val apply: (resolvedText: String) -> Unit
) {
    private var job: Job? = null

    /** [rawText] là text gốc của config; [image] null (chưa chọn ảnh) thì áp dụng nguyên văn, không resolve. */
    fun update(rawText: String, image: T?) {
        job?.cancel()
        job = scope.launch {
            val resolved = if (image != null) resolveText(rawText, image) else rawText
            apply(resolved)
        }
    }

    /** Huỷ job đang chờ — gọi ở `onDestroy` để không áp dụng vào view đã huỷ. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
