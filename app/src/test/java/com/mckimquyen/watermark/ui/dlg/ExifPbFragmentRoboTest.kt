package com.mckimquyen.watermark.ui.dlg

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BUG-18: `styleButtons` KHÔNG được cache theo `by lazy` cấp Fragment — `BottomSheetDialogFragment`
 * có thể tái tạo View (onCreateView gọi lại) nhiều lần trong cùng 1 Fragment instance (xoay màn
 * hình / dialog bị hệ thống tái tạo), và `by lazy` sẽ giữ tham chiếu MẠNH tới các `MaterialButton`
 * của View CŨ đã gỡ khỏi hierarchy vĩnh viễn cho tới khi Fragment bị huỷ hẳn.
 *
 * Launch thật [ExifPbFragment] (BottomSheetDialogFragment + Hilt ViewModel qua `activityViewModels`)
 * không khả thi trong unit test JVM/Robolectric của repo này (không có Hilt test harness) — cách
 * kiểm chứng khác: xác nhận bằng reflection rằng field KHÔNG còn được sinh bởi delegate `Lazy`
 * (`by lazy` sinh field `<name>$delegate` kiểu `kotlin.Lazy`); property getter thuần không sinh
 * field nào cho `styleButtons`, luôn đọc `binding` (đã trỏ đúng View hiện tại) mỗi lần gọi.
 */
@RunWith(RobolectricTestRunner::class)
class ExifPbFragmentRoboTest {

    @Test
    fun styleButtons_hasNoLazyDelegateField() {
        val fields = ExifPbFragment::class.java.declaredFields.map { it.name }

        assertThat(fields).doesNotContain("styleButtons\$delegate")
    }

    @Test
    fun styleButtons_hasNoBackingFieldAtAll() {
        // Property getter thuần (get() = ...) không sinh field lưu trữ nào cho "styleButtons" —
        // khác với `by lazy` (sinh field delegate) hoặc `val` thường (sinh backing field).
        val fields = ExifPbFragment::class.java.declaredFields.map { it.name }

        assertThat(fields.none { it.contains("styleButtons") }).isTrue()
    }
}
