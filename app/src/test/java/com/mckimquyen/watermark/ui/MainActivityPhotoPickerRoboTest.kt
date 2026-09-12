package com.mckimquyen.watermark.ui

import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-10: chọn icon watermark phải ưu tiên Android Photo Picker
 * (`ActivityResultContracts.PickVisualMedia`) thay vì `ACTION_PICK` legacy khi khả dụng — Photo
 * Picker không cần quyền `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE`.
 *
 * `isPhotoPickerAvailable()` trả `true` trong Robolectric ở đây (SDK giả lập >= 33, native Photo
 * Picker luôn sẵn có từ Android 13+, không cần PackageManager resolve) — ĐÚNG môi trường thật cần
 * verify (Samsung Galaxy S24 Ultra dùng smoke test cũng luôn `true`). Nhánh fallback `ACTION_PICK`
 * (thiết bị Android < 11 hoặc thiếu Play Services module) không tái tạo được ở đây vì không hạ
 * được `Build.VERSION.SDK_INT`/`isPhotoPickerAvailable` xuống `false` trong Robolectric mà không
 * làm sai lệch toàn bộ môi trường test khác — code fallback (`pickIconLauncher.launch(mime)`) tái
 * dùng nguyên `PickImageContract` gốc chưa từng bị sửa, nên rủi ro hồi quy ở nhánh đó thấp.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityPhotoPickerRoboTest {

    private fun MainActivity.performFileSearch(requestCode: Int) {
        MainActivity::class.java.getDeclaredMethod("performFileSearch", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(this, requestCode)
    }

    @Test
    fun `pickIcon uses Android Photo Picker for single image, no multi-select extra`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(activity)).isTrue()

        activity.performFileSearch(MainActivity.REQ_PICK_ICON)
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
            ?: shadowOf(activity).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(MediaStore.ACTION_PICK_IMAGES)
        assertThat(started.type).isEqualTo("image/*")
        // Pick đơn (icon watermark chỉ cần 1 ảnh) — không có extra giới hạn multi-select.
        assertThat(started.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX)).isFalse()
    }
}
