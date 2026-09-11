package com.mckimquyen.watermark.ui.dlg

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
 * BUG-10: `GalleryFragment.bindView()` phải `observe(viewLifecycleOwner)` (không phải `this`)
 * cho `galleryPickedImageList` và `selectedCount` — nếu không, observer sống theo vòng đời
 * Fragment (không bị gỡ khi View bị huỷ qua back stack/ViewPager), tích luỹ qua mỗi lần
 * `onCreateView` chạy lại.
 *
 * Test dùng thật `FragmentManager` (không mock lifecycle) để tái hiện đúng kịch bản ticket mô tả:
 * add GalleryFragment vào 1 container, sau đó `detach()` — đúng cơ chế `ViewPager` dùng để ẩn
 * page ngoài màn hình. FragmentManager sẽ gọi `onDestroyView()` (View bị huỷ) nhưng GIỮ Fragment
 * instance sống (chỉ detach, không remove). Với `observe(viewLifecycleOwner)` (đã fix), observer
 * phải bị gỡ ngay khi View huỷ. Với `observe(this)` (bug cũ), observer vẫn còn sống sau khi View
 * đã huỷ.
 *
 * `GalleryFragment` không phải `@AndroidEntryPoint` — chỉ `shareViewModel` (qua
 * `activityViewModels()`) cần Hilt trong app thật. Ở đây thay bằng
 * `TestHostActivity.defaultViewModelProviderFactory` trả thẳng 1 `MainViewModel` dựng trực tiếp
 * (không qua Hilt) — đúng pattern đã dùng ở `MainViewModelRemoveImageRoboTest`.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryFragmentLifecycleRoboTest {

    companion object {
        // Robolectric dựng Activity bằng constructor mặc định (reflection) nên không thể truyền
        // viewModel qua constructor — dùng biến static tạm cho factory đọc lại.
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
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        runBlocking {
            context.waterMarkDataStore.edit { it.clear() }
        }
        viewModel = MainViewModel(
            appContext = context,
            userRepo = UserConfigRepository(context.userDataStore),
            waterMarkRepo = WaterMarkRepository(context, context.waterMarkDataStore),
            memorySettingRepo = MemorySettingRepo(),
            templateRepo = TemplateRepository(null)
        )
        testViewModel = viewModel
    }

    @Test
    fun `view destroyed via back stack unregisters observer, fragment instance still alive`() {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }

        activity.supportFragmentManager.beginTransaction()
            .add(containerId, fragment, "gallery")
            .commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.isAdded).isTrue()
        assertThat(viewModel.galleryPickedImageList.hasObservers())
            .isTrue() // view đã tạo → observer đăng ký

        // detach() huỷ View của GalleryFragment (onDestroyView) nhưng GIỮ NGUYÊN instance (đúng
        // cơ chế ViewPager dùng để ẩn page ngoài màn hình — chính kịch bản ticket mô tả).
        activity.supportFragmentManager.beginTransaction()
            .detach(fragment)
            .commit()
        shadowOf(Looper.getMainLooper()).idle()

        // instance vẫn sống — FragmentManager vẫn track cùng 1 object (chỉ detach, không remove).
        assertThat(activity.supportFragmentManager.findFragmentByTag("gallery")).isSameInstanceAs(fragment)
        assertThat(fragment.isDetached).isTrue()
        assertThat(fragment.view).isNull() // View đã bị huỷ
        assertThat(viewModel.galleryPickedImageList.hasObservers())
            .isFalse() // FIX: observe(viewLifecycleOwner) → gỡ ngay khi View huỷ
    }
}
