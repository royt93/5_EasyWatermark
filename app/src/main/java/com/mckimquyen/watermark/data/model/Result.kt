package com.mckimquyen.watermark.data.model

// Generic error code for unspecified failures (used when no specific error type applies)
const val ERROR_CODE_GENERIC = "-1"

// P1 review pass 8: data class để equals()/hashCode() so nội dung (không phải reference) -
// DiffUtil.areContentsTheSame qua JobState.Success/Failure phụ thuộc việc này.
data class Result<T>(
    var type: Type,
    var data: T? = null,
    var code: String? = null,
    var message: String? = null
) {

    fun isFailure() = type == Type.Failure

    sealed class Type {
        object Success : Type()
        object Failure : Type()
    }

    companion object {
        fun <T> success(data: T?, code: String? = null, message: String? = null): Result<T> {
            return Result(Type.Success, data, code, message)
        }

        fun <T> failure(data: T? = null, code: String? = null, message: String? = null): Result<T> {
            return Result(Type.Failure, data, code, message)
        }

        fun <T> extendMsg(result: Result<*>, data: T? = null): Result<T> {
            return Result(result.type, data, result.code, result.message)
        }
    }
}
