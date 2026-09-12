package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.provider.MediaStore
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.R
import com.mckimquyen.watermark.data.repo.MemorySettingRepo
import com.mckimquyen.watermark.data.repo.TemplateRepository
import com.mckimquyen.watermark.data.repo.UserConfigRepository
import com.mckimquyen.watermark.data.repo.WaterMarkRepository
import com.mckimquyen.watermark.di.userDataStore
import com.mckimquyen.watermark.di.waterMarkDataStore
import com.mckimquyen.watermark.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-10: nút "pick via system" (`ivSysImage`) trong `GalleryFragment` phải ưu tiên Android Photo
 * Picker (`PickMultipleVisualMedia`) thay vì `MultiPickContract`/ACTION_PICK legacy khi khả dụng.
 * `isPhotoPickerAvailable()` trả `true` trong Robolectric ở đây (giống thiết bị thật Android 13+)
 * — xem giải thích chi tiết trong [MainActivityPhotoPickerRoboTest]; nhánh fallback không tái tạo
 * được trong Robolectric nhưng dùng nguyên `MultiPickContract` gốc chưa sửa, rủi ro hồi quy thấp.
 *
 * Dùng lại pattern `TestHostActivity`/`GalleryFragmentLifecycleRoboTest` (BUG-10) để launch
 * `GalleryFragment` (cần `activityViewModels()`) mà không phụ thuộc Hilt.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryFragmentPhotoPickerRoboTest {

    companion object {
        lateinit var testViewModel: MainViewModel
    }

    class TestHostActivity : FragmentActivity() {
        override val defaultViewModelProviderFactory: ViewModelProvider.Factory
            get() = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = testViewModel as T
            }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        testViewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
    }

    @Test
    fun `pick via system uses Android Photo Picker with multi-select max extra`() {
        assertThat(ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)).isTrue()

        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()

        val handled = fragment.requireView().findViewById<com.google.android.material.appbar.MaterialToolbar>(
            R.id.topAppBar
        ).menu.performIdentifierAction(R.id.ivSysImage, 0)
        assertThat(handled).isTrue()

        val started = shadowOf(activity).nextStartedActivityForResult?.intent
            ?: shadowOf(activity).nextStartedActivity
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(MediaStore.ACTION_PICK_IMAGES)
        assertThat(started.type).isEqualTo("image/*")
        // Multi-select (batch nhiều ảnh) — có extra giới hạn số lượng, khác nhánh pick đơn (icon).
        assertThat(started.hasExtra(MediaStore.EXTRA_PICK_IMAGES_MAX)).isTrue()
    }
}
