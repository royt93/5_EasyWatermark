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
import com.mckimquyen.watermark.ui.adapter.GalleryAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * ENH-09: label FAB/hint chọn ảnh trong `GalleryFragment` phải dùng `<plurals>` (qua
 * `resources.getQuantityString`) thay vì `if (count == 1) ... else ...` hardcode string trực tiếp
 * trong code. Verify bằng cách đẩy trực tiếp giá trị `GalleryAdapter.selectedCount` (LiveData công
 * khai, đúng nguồn dữ liệu observer thật sự lắng nghe) — không cần dựng lại toàn bộ cơ chế chọn ảnh
 * qua RecyclerView thật (click/drag), vốn không phải phần logic ENH-09 thay đổi.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryFragmentSelectionLabelRoboTest {

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

    private fun launchFragment(): GalleryFragment {
        val activity = Robolectric.buildActivity(TestHostActivity::class.java).setup().get()
        val containerId = FrameLayout(activity).let {
            it.id = android.view.View.generateViewId()
            activity.setContentView(it)
            it.id
        }
        val fragment = GalleryFragment().apply { setShowsDialog(false) }
        activity.supportFragmentManager.beginTransaction().add(containerId, fragment, "gallery").commit()
        shadowOf(Looper.getMainLooper()).idle()
        return fragment
    }

    @Test
    fun singleSelection_usesSingularStrings() {
        val fragment = launchFragment()
        val adapter = fragment.binding.rvContent.adapter as GalleryAdapter

        adapter.selectedCount.value = 1
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.fab.text.toString()).isEqualTo("Select 1 photo")
        assertThat(fragment.binding.tvSelectionHint?.text.toString()).isEqualTo("1 selected")
    }

    @Test
    fun multiSelection_usesPluralStrings() {
        val fragment = launchFragment()
        val adapter = fragment.binding.rvContent.adapter as GalleryAdapter

        adapter.selectedCount.value = 2
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(fragment.binding.fab.text.toString()).isEqualTo("Select 2 photos")
        assertThat(fragment.binding.tvSelectionHint?.text.toString()).isEqualTo("2 selected")
    }
}
