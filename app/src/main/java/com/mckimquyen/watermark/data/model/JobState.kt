package com.mckimquyen.watermark.data.model

sealed class JobState {
    object Ready : JobState()

    object Ing : JobState()

    // P1 review pass 8: data class - equals() so nội dung `result`, không phải reference. Trước đây
    // plain class khiến DiffUtil.areContentsTheSame (SaveImageListAdapter) luôn `false` dù nội
    // dung giống hệt -> rebind/flicker thừa mỗi lần jobState emit lại cùng giá trị.
    data class Success(val result: Result<*>) : JobState()

    data class Failure(val result: Result<*>) : JobState()
}
