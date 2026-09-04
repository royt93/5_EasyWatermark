package com.mckimquyen.watermark.data.model

/**
 * Quyết định [JobState] từ [Result] của 1 thao tác export ảnh. Hàm thuần (không phụ thuộc
 * Android) để dễ unit test; tách riêng vì trước đây (BUG-03) nơi gọi luôn gán
 * `JobState.Success` bất kể `result` là success hay failure.
 */
object JobStateResolver {

    fun resolve(result: Result<*>?): JobState {
        requireNotNull(result) { "result không được null khi resolve JobState" }
        return if (result.isFailure()) {
            JobState.Failure(result)
        } else {
            JobState.Success(result)
        }
    }
}
